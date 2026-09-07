package com.mbk.hayplan.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ForecastRepository(
    private val client: OpenMeteoClient,
    val locations: List<HayPlanLocation> = LocationCatalog.locations,
) {
    suspend fun load(forceRefresh: Boolean = false): List<LocationForecast> = coroutineScope {
        val requestSlots = Semaphore(MAX_PARALLEL_REQUESTS)
        // Hiking uses the town/reference point; Beach uses the actual coastal reference point.
        val weatherPoints = (locations.map { it.coordinates } +
            locations.mapNotNull { it.coast?.coordinates }).distinct()
        val weather = weatherPoints.associateWith { point ->
            async { attempt { requestSlots.withPermit { client.weather(point, forceRefresh) } } }
        }
        val marine = locations.mapNotNull { it.coast?.coordinates }.distinct().associateWith { point ->
            async { attempt { requestSlots.withPermit { client.marine(point, forceRefresh) } } }
        }
        locations.map { location ->
            async {
                val city = weather.getValue(location.coordinates).await()
                val base = weatherData(city)
                val beach = location.coast?.let { coast ->
                    val coastalWeather = weather.getValue(coast.coordinates).await()
                    val sea = marine.getValue(coast.coordinates).await()
                    val beachBase = when {
                        coastalWeather != null -> weatherData(coastalWeather)
                        city != null -> base.copy(errors = base.errors + ForecastIssue.BEACH_WEATHER_FALLBACK)
                        else -> base
                    }
                    beachBase.copy(
                        hours = if (sea != null) OpenMeteoParser.withMarine(beachBase.hours, sea.body) else beachBase.hours,
                        sources = beachBase.sources + listOfNotNull(sea?.status("Sea")),
                        errors = beachBase.errors + if (sea == null)
                            listOf(ForecastIssue.SEA_UNAVAILABLE) else emptyList(),
                    )
                } ?: base
                LocationForecast(location, base, beach)
            }
        }.awaitAll()
    }

    private fun weatherData(data: CachedForecast?): ActivityForecastData =
        if (data == null) ActivityForecastData(errors = listOf(ForecastIssue.WEATHER_UNAVAILABLE))
        else ActivityForecastData(OpenMeteoParser.weather(data.body), listOf(data.status("Weather")))

    private fun CachedForecast.status(label: String) =
        ForecastSourceStatus(label, fetchedAt, refreshFailed, persistenceFailed)

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MAX_PARALLEL_REQUESTS = 6
    }
}
