package com.mbk.hayplan.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class OpenMeteoClient(
    private val cache: ForecastCache,
    private val download: suspend (String) -> String = ::downloadForecast,
) {
    suspend fun weather(point: Coordinates, forceRefresh: Boolean): CachedForecast {
        val url = weatherUrl(listOf(point))
        return cache.get(url, forceRefresh, validate = {
            require(OpenMeteoParser.weather(it).isNotEmpty())
        }) { download(url) }
    }

    suspend fun weather(points: List<Coordinates>, forceRefresh: Boolean): Map<Coordinates, CachedForecast?> =
        loadBatch(points, forceRefresh, ::weatherUrl, validate = {
            require(OpenMeteoParser.weather(it).isNotEmpty())
        }, fallback = ::weather)

    suspend fun marine(point: Coordinates, forceRefresh: Boolean): CachedForecast {
        val url = marineUrl(listOf(point))
        return cache.get(url, forceRefresh, validate = {
            OpenMeteoParser.validateMarine(it)
        }) { download(url) }
    }

    suspend fun marine(points: List<Coordinates>, forceRefresh: Boolean): Map<Coordinates, CachedForecast?> =
        loadBatch(points, forceRefresh, ::marineUrl, validate = OpenMeteoParser::validateMarine,
            fallback = ::marine)

    private suspend fun loadBatch(
        points: List<Coordinates>,
        forceRefresh: Boolean,
        requestUrl: (List<Coordinates>) -> String,
        validate: (String) -> Unit,
        fallback: suspend (Coordinates, Boolean) -> CachedForecast,
    ): Map<Coordinates, CachedForecast?> {
        val distinctPoints = points.distinct()
        if (distinctPoints.isEmpty()) return emptyMap()
        if (distinctPoints.size == 1) {
            val point = distinctPoints.single()
            return mapOf(point to try {
                fallback(point, forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            })
        }
        val keys = distinctPoints.associateWith { requestUrl(listOf(it)) }
        val pointsByKey = keys.entries.associate { (point, key) -> key to point }
        val cached = cache.getMany(keys.values.toList(), forceRefresh, validate) { requestedKeys ->
            val requestedPoints = requestedKeys.map(pointsByKey::getValue)
            val responses = OpenMeteoParser.splitBatch(download(requestUrl(requestedPoints)), requestedPoints.size)
            requestedKeys.mapIndexed { index, key -> key to responses.getValue(index) }.toMap()
        }.toMutableMap()
        keys.forEach { (point, key) ->
            if (cached[key] == null) cached[key] = try {
                fallback(point, forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        return keys.mapValues { (_, key) -> cached[key] }
    }

    private fun weatherUrl(points: List<Coordinates>) =
        "https://api.open-meteo.com/v1/forecast?${coordinates(points)}" +
            "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "precipitation_probability,precipitation,cloud_cover,visibility,weather_code," +
            "wind_speed_10m,wind_gusts_10m,uv_index" +
            "&daily=sunrise,sunset&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"

    private fun marineUrl(points: List<Coordinates>) =
        "https://marine-api.open-meteo.com/v1/marine?${coordinates(points)}" +
            "&hourly=wave_height,sea_surface_temperature&cell_selection=sea&length_unit=metric"

    // Both endpoints accept 16 days; actual variable coverage may be shorter.
    private fun coordinates(points: List<Coordinates>) =
        "latitude=${points.joinToString(",") { it.latitude.toString() }}" +
            "&longitude=${points.joinToString(",") { it.longitude.toString() }}" +
            "&timezone=Europe%2FMadrid&forecast_days=16"
}

private suspend fun downloadForecast(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode !in 200..299) {
            throw IOException("Open-Meteo HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
