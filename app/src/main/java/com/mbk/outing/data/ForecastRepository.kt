package com.mbk.outing.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ForecastRepository(
    private val client: OpenMeteoClient,
    private val locations: List<OutingLocation> = LocationCatalog.locations,
) {
    suspend fun load(forceRefresh: Boolean = false): List<LocationForecast> = coroutineScope {
        // Deduplicate coordinates across locations, including force refresh.
        val weatherPoints = locations.flatMap { listOfNotNull(it.coordinates, it.mainBeach?.coordinates) }.distinct()
        val seaPoints = locations.mapNotNull { it.mainBeach?.coordinates }.distinct()
        val weather = weatherPoints.associateWith { point ->
            async { attempt { client.weather(point, forceRefresh) } }
        }
        val marine = seaPoints.associateWith { point ->
            async { attempt { client.marine(point, forceRefresh) } }
        }
        locations.map { location ->
            async {
                val city = weather.getValue(location.coordinates).await().getOrNull()
                val hiking = weatherData(city)
                val beach = location.mainBeach?.let { mainBeach ->
                    val air = weather.getValue(mainBeach.coordinates).await().getOrNull()
                    val sea = marine.getValue(mainBeach.coordinates).await().getOrNull()
                    val base = weatherData(air)
                    base.copy(
                        hours = if (sea != null) OpenMeteoParser.withMarine(base.hours, sea.body) else base.hours,
                        sources = base.sources + listOfNotNull(sea?.status("Sea")),
                        errors = base.errors + if (sea == null) listOf("Sea forecast unavailable.") else emptyList(),
                    )
                }
                LocationForecast(location, hiking, beach)
            }
        }.awaitAll()
    }

    private fun weatherData(data: CachedForecast?): ActivityForecastData =
        if (data == null) ActivityForecastData(errors = listOf("Weather forecast unavailable."))
        else ActivityForecastData(OpenMeteoParser.weather(data.body), listOf(data.status("Weather")))

    private fun CachedForecast.status(label: String) =
        ForecastSourceStatus(label, fetchedAt, refreshFailed, persistenceFailed)

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
