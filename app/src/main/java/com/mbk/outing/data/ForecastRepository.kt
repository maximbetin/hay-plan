package com.mbk.outing.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ForecastRepository(
    private val client: OpenMeteoClient,
    val locations: List<OutingLocation> = LocationCatalog.locations,
) {
    suspend fun load(forceRefresh: Boolean = false): List<LocationForecast> = coroutineScope {
        // One weather point per town, plus sea data only for configured coastal references.
        val weather = locations.map { it.coordinates }.distinct().associateWith { point ->
            async { attempt { client.weather(point, forceRefresh) } }
        }
        val marine = locations.mapNotNull { it.coast?.coordinates }.distinct().associateWith { point ->
            async { attempt { client.marine(point, forceRefresh) } }
        }
        locations.map { location ->
            async {
                val city = weather.getValue(location.coordinates).await()
                val base = weatherData(city)
                val beach = location.coast?.let { coast ->
                    val sea = marine.getValue(coast.coordinates).await()
                    base.copy(
                        hours = if (sea != null) OpenMeteoParser.withMarine(base.hours, sea.body) else base.hours,
                        sources = base.sources + listOfNotNull(sea?.status("Sea")),
                        errors = base.errors + if (sea == null)
                            listOf("Sea forecast unavailable · using weather only") else emptyList(),
                    )
                } ?: base
                LocationForecast(location, base, beach)
            }
        }.awaitAll()
    }

    private fun weatherData(data: CachedForecast?): ActivityForecastData =
        if (data == null) ActivityForecastData(errors = listOf("Weather forecast unavailable."))
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
}
