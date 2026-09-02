package com.mbk.outing.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ForecastRepository(private val client: OpenMeteoClient = OpenMeteoClient()) {
    suspend fun load(): List<BeachForecast> = coroutineScope {
        BeachCatalog.beaches.map { beach -> async { loadBeach(beach) } }.awaitAll()
    }

    private suspend fun loadBeach(beach: Beach): BeachForecast = coroutineScope {
        val weather = async { attempt { OpenMeteoParser.weather(client.weather(beach)) } }
        val marine = async { attempt { client.marine(beach) } }
        val hours = weather.await().getOrNull()
        val marineJson = marine.await().getOrNull()
        if (hours.isNullOrEmpty()) {
            return@coroutineScope BeachForecast(beach, emptyList(), weatherError = true)
        }
        val merged = if (marineJson == null) null else {
            attempt { OpenMeteoParser.withMarine(hours, marineJson) }.getOrNull()
        }
        BeachForecast(beach, merged ?: hours, marineError = merged == null)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
