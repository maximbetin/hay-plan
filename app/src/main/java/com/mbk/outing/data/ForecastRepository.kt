package com.mbk.outing.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ForecastRepository(
    private val client: OpenMeteoClient,
    private val locations: List<OutingLocation> = LocationCatalog.locations,
) {
    suspend fun load(
        forceRefresh: Boolean = false,
        selectedBeachIds: Map<String, String> = emptyMap(),
    ): List<LocationForecast> = coroutineScope {
        val selected = locations.associate { it.id to it.beach(selectedBeachIds[it.id]) }
        // Deduplicate coordinates across locations, including force refresh.
        val weatherPoints = locations.flatMap { listOfNotNull(it.coordinates, selected[it.id]?.coordinates) }.distinct()
        val seaPoints = selected.values.mapNotNull { it?.coordinates }.distinct()
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
                val beach = selected[location.id]?.let { mainBeach ->
                    val air = weather.getValue(mainBeach.coordinates).await().getOrNull()
                    val sea = marine.getValue(mainBeach.coordinates).await().getOrNull()
                    mainBeach.id to beachData(air, sea)
                }
                LocationForecast(location, hiking, if (beach == null) emptyMap() else mapOf(beach))
            }
        }.awaitAll()
    }

    /** Only a newly selected/expired beach needs these two requests. Cache hits use neither. */
    suspend fun loadBeach(beach: BeachLocation): ActivityForecastData = coroutineScope {
        val air = async { attempt { client.weather(beach.coordinates, false) } }
        val sea = async { attempt { client.marine(beach.coordinates, false) } }
        beachData(air.await().getOrNull(), sea.await().getOrNull())
    }

    private fun beachData(air: CachedForecast?, sea: CachedForecast?): ActivityForecastData {
        val base = weatherData(air)
        return base.copy(
            hours = if (sea != null) OpenMeteoParser.withMarine(base.hours, sea.body) else base.hours,
            sources = base.sources + listOfNotNull(sea?.status("Sea")),
            errors = base.errors + if (sea == null) listOf("Sea forecast unavailable.") else emptyList(),
        )
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
