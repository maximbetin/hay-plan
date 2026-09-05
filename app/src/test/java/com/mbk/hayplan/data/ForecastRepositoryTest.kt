package com.mbk.hayplan.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import com.mbk.hayplan.domain.*

class ForecastRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = MutableClock()
    private val location = LocationCatalog.locations.first { it.id == "gijon" }

    @Test fun `one town weather point and one sea reference are cached until refresh`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url -> calls += url; response(url) }
        val repository = ForecastRepository(client, listOf(location))
        val initial = repository.load().single()
        assertEquals(2, calls.size)
        assertEquals(20.0, initial.weather.hours.first().airTemperatureC!!, 0.001)
        assertEquals(20.0, initial.beach.hours.first().airTemperatureC!!, 0.001)
        assertEquals(21.0, initial.beach.hours.first().seaTemperatureC!!, 0.001)
        val weatherUrl = calls.single { !it.contains("marine-api") }
        listOf("apparent_temperature", "relative_humidity_2m", "visibility", "weather_code",
            "wind_gusts_10m", "uv_index").forEach { assertTrue(weatherUrl.contains(it)) }
        assertTrue(calls.single { it.contains("marine-api") }.contains("latitude=${location.coast!!.coordinates.latitude}"))
        repository.load()
        assertEquals(2, calls.size)
        repository.load(forceRefresh = true)
        assertEquals(4, calls.size)
    }

    @Test fun `inland town supports both activities without any marine request or invented sea data`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url -> calls += url; response(url) }
        val oviedo = LocationCatalog.locations.first { it.id == "oviedo" }
        val result = ForecastRepository(client, listOf(oviedo)).load().single()
        assertEquals(1, calls.size)
        assertFalse(calls.single().contains("marine-api"))
        assertSame(result.weather, result.beach)
        ActivityType.entries.forEach { assertTrue(result.forActivity(it).hours.isNotEmpty()) }
        assertTrue(result.beach.hours.all { it.seaTemperatureC == null && it.waveHeightM == null })
        // The last fixture timestamp lacks the following precipitation sample; use a complete hour.
        assertEquals(MarineCoverage.NONE, ActivityScorer.score(ActivityType.BEACH, result.beach.hours.take(1))!!.marineCoverage)
    }

    @Test fun `shared coordinates are deduplicated on refresh`() = runBlocking {
        var calls = 0
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url -> calls++; response(url) }
        ForecastRepository(client, listOf(location, location.copy(id = "second"))).load(true)
        assertEquals(2, calls)
    }

    @Test fun `marine failure produces an explicit weather only fallback without affecting Hiking`() = runBlocking {
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            if (url.contains("marine-api")) throw IOException("offline") else response(url)
        }
        val result = ForecastRepository(client, listOf(location)).load().single()
        assertTrue(result.weather.errors.isEmpty())
        assertTrue(result.beach.errors.any { it.contains("weather only") })
        assertTrue(result.beach.hours.all { it.seaTemperatureC == null && it.waveHeightM == null })
        assertNotNull(ActivityScorer.score(ActivityType.BEACH, result.beach.hours.take(1)))
    }

    @Test fun `fresh disk cache survives process recreation`() = runBlocking {
        ForecastRepository(OpenMeteoClient(ForecastCache(temporary.root, clock), ::response)).load()
        val result = ForecastRepository(OpenMeteoClient(ForecastCache(temporary.root, clock)) {
            error("No network expected")
        }).load()
        assertTrue(result.all { it.weather.errors.isEmpty() && it.beach.errors.isEmpty() })
    }

    @Test fun `failed refresh preserves original timestamps and stale warnings`() = runBlocking {
        var offline = false
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            if (offline) throw IOException("offline") else response(url)
        }
        val repository = ForecastRepository(client)
        val first = repository.load()
        offline = true
        val refreshed = repository.load(true)
        first.zip(refreshed).forEach { (old, new) ->
            assertEquals(old.beach.sources.map { it.fetchedAt }, new.beach.sources.map { it.fetchedAt })
            assertTrue(new.beach.sources.all { it.refreshFailed })
            assertTrue(new.beach.hours.isNotEmpty())
        }
    }

    @Test fun `catalog dispatches one weather request per town plus one per coastal reference`() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = ForecastRepository(OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            calls += url; response(url)
        })
        val forecasts = repository.load()
        assertEquals(LocationCatalog.locations.map { it.id }, forecasts.map { it.location.id })
        val weatherPoints = LocationCatalog.locations.map { it.coordinates }.distinct().size
        val marinePoints = LocationCatalog.locations.mapNotNull { it.coast?.coordinates }.distinct().size
        val expected = weatherPoints + marinePoints
        assertEquals(expected, calls.size)
        assertEquals(marinePoints, calls.count { it.contains("marine-api") })
        forecasts.filter { it.location.coast == null }.forEach {
            assertSame(it.weather, it.beach)
            assertTrue(it.beach.hours.all { hour -> hour.seaTemperatureC == null && hour.waveHeightM == null })
        }
        repository.load(true)
        assertEquals(expected * 2, calls.size)
    }

    private fun response(url: String) = if (url.contains("marine-api")) marine else weather
    private val weather = """
        {"daily":{"time":["2026-09-02"],"sunrise":["2026-09-02T08:00"],"sunset":["2026-09-02T20:00"]},
         "hourly":{"time":["2026-09-02T09:00","2026-09-02T10:00","2026-09-02T11:00","2026-09-02T12:00"],
         "temperature_2m":[20,20,20,20],"apparent_temperature":[19,19,19,19],
         "relative_humidity_2m":[60,60,60,60],"precipitation_probability":[5,5,5,5],
         "precipitation":[0,0,0,0],"cloud_cover":[10,10,10,10],
         "visibility":[20000,20000,20000,20000],"weather_code":[1,1,1,1],
         "wind_speed_10m":[10,10,10,10],"wind_gusts_10m":[18,18,18,18],"uv_index":[4,4,4,4]}}
    """.trimIndent()
    private val marine = """
        {"hourly":{"time":["2026-09-02T09:00","2026-09-02T10:00","2026-09-02T11:00","2026-09-02T12:00"],
        "wave_height":[0.3,0.3,0.3,0.3],"sea_surface_temperature":[21,21,21,21]}}
    """.trimIndent()
}
