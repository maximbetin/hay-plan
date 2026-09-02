package com.mbk.outing.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ForecastRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = MutableClock()
    private val location = LocationCatalog.locations.single()

    @Test fun `Gijon loads three sources once and reuses them until manual refresh`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            calls += url
            response(url)
        }
        val repository = ForecastRepository(client)
        val initial = repository.load().single()
        assertEquals(3, calls.size)
        assertEquals(20.0, initial.hiking.hours.first().airTemperatureC!!, 0.001)
        assertEquals(24.0, initial.beach!!.hours.first().airTemperatureC!!, 0.001)
        assertEquals(21.0, initial.beach.hours.first().seaTemperatureC!!, 0.001)
        repository.load()
        assertEquals(3, calls.size)
        repository.load(forceRefresh = true)
        assertEquals(6, calls.size)
    }

    @Test fun `inland location requests weather only`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url -> calls += url; response(url) }
        val result = ForecastRepository(client, listOf(location.copy(mainBeach = null))).load().single()
        assertEquals(1, calls.size)
        assertNull(result.beach)
        assertTrue(result.hiking.hours.isNotEmpty())
    }

    @Test fun `shared coordinates are deduplicated even on manual refresh`() = runBlocking {
        var calls = 0
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url -> calls++; response(url) }
        val standalone = location.copy(mainBeach = MainBeach("Same point", location.coordinates))
        ForecastRepository(client, listOf(standalone, standalone.copy(id = "second"))).load(forceRefresh = true)
        assertEquals(2, calls) // One weather URL and one marine URL.
    }

    @Test fun `marine failure does not prevent hiking or masquerade as complete beach data`() = runBlocking {
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            if (url.contains("marine-api")) throw IOException("offline") else response(url)
        }
        val result = ForecastRepository(client).load().single()
        assertTrue(result.hiking.errors.isEmpty())
        assertTrue(result.hiking.hours.isNotEmpty())
        assertTrue(result.beach!!.errors.any { it.contains("Sea") })
        assertTrue(result.beach.hours.all { it.seaTemperatureC == null && it.waveHeightM == null })
    }

    @Test fun `a fresh cache after process recreation requires no requests`() = runBlocking {
        ForecastRepository(OpenMeteoClient(ForecastCache(temporary.root, clock), ::response)).load()
        val result = ForecastRepository(OpenMeteoClient(ForecastCache(temporary.root, clock)) {
            error("No network expected")
        }).load().single()
        assertTrue(result.hiking.errors.isEmpty())
        assertTrue(result.beach!!.errors.isEmpty())
    }

    @Test fun `offline refresh preserves original source timestamps and marks the fallback`() = runBlocking {
        var offline = false
        val client = OpenMeteoClient(ForecastCache(temporary.root, clock)) { url ->
            if (offline) throw IOException("offline") else response(url)
        }
        val repository = ForecastRepository(client)
        val first = repository.load().single()
        offline = true
        val refreshed = repository.load(forceRefresh = true).single()
        assertEquals(first.hiking.sources.first().fetchedAt, refreshed.hiking.sources.first().fetchedAt)
        assertTrue(refreshed.hiking.sources.all { it.refreshFailed })
        assertTrue(refreshed.beach!!.sources.all { it.refreshFailed })
        assertTrue(refreshed.hiking.hours.isNotEmpty())
    }

    private fun response(url: String): String = if (url.contains("marine-api")) marine else {
        val temperature = if (url.contains("latitude=${location.coordinates.latitude}")) 20 else 24
        weather.replace("TEMPERATURE", temperature.toString())
    }

    private val weather = """
        {"daily":{"time":["2026-09-02"],"sunrise":["2026-09-02T08:00"],"sunset":["2026-09-02T20:00"]},
         "hourly":{"time":["2026-09-02T09:00","2026-09-02T10:00","2026-09-02T11:00","2026-09-02T12:00"],
         "temperature_2m":[TEMPERATURE,TEMPERATURE,TEMPERATURE,TEMPERATURE],
         "precipitation_probability":[5,5,5,5],"precipitation":[0,0,0,0],
         "cloud_cover":[10,10,10,10],"wind_speed_10m":[10,10,10,10]}}
    """.trimIndent()

    private val marine = """
        {"hourly":{"time":["2026-09-02T09:00","2026-09-02T10:00","2026-09-02T11:00","2026-09-02T12:00"],
        "wave_height":[0.3,0.3,0.3,0.3],"sea_surface_temperature":[21,21,21,21]}}
    """.trimIndent()
}
