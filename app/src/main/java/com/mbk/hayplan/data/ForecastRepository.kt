package com.mbk.hayplan.data

import kotlinx.coroutines.async
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
        val weatherLoads = weatherPoints.chunked(BATCH_SIZE).map { points ->
            async { requestSlots.withPermit { client.weather(points, forceRefresh) } }
        }
        val marineLoads = locations.mapNotNull { it.coast?.coordinates }.distinct().chunked(BATCH_SIZE).map { points ->
            async { requestSlots.withPermit { client.marine(points, forceRefresh) } }
        }
        val weather = weatherLoads.map { it.await() }.flatMap { it.entries }.associate { it.toPair() }
        val marine = marineLoads.map { it.await() }.flatMap { it.entries }.associate { it.toPair() }
        locations.map { location ->
            val city = weather[location.coordinates]
            val base = weatherData(city)
            val beach = location.coast?.let { coast ->
                val coastalWeather = weather[coast.coordinates]
                val sea = marine[coast.coordinates]
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
    }

    private fun weatherData(data: CachedForecast?): ActivityForecastData =
        if (data == null) ActivityForecastData(errors = listOf(ForecastIssue.WEATHER_UNAVAILABLE))
        else ActivityForecastData(OpenMeteoParser.weather(data.body), listOf(data.status("Weather")))

    private fun CachedForecast.status(label: String) =
        ForecastSourceStatus(label, fetchedAt, refreshFailed, persistenceFailed)

    companion object {
        const val BATCH_SIZE = 5
        const val MAX_PARALLEL_REQUESTS = 6
    }
}
