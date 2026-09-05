package com.mbk.hayplan.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class OpenMeteoClient(
    private val cache: ForecastCache,
    private val download: suspend (String) -> String = ::downloadForecast,
) {
    suspend fun weather(point: Coordinates, forceRefresh: Boolean): CachedForecast {
        val url = "https://api.open-meteo.com/v1/forecast?${coordinates(point)}" +
            "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "precipitation_probability,precipitation,cloud_cover,visibility,weather_code," +
            "wind_speed_10m,wind_gusts_10m,uv_index" +
            "&daily=sunrise,sunset&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"
        return cache.get(url, forceRefresh, validate = {
            require(OpenMeteoParser.weather(it).isNotEmpty())
        }) { download(url) }
    }

    suspend fun marine(point: Coordinates, forceRefresh: Boolean): CachedForecast {
        val url = "https://marine-api.open-meteo.com/v1/marine?${coordinates(point)}" +
            "&hourly=wave_height,sea_surface_temperature&cell_selection=sea&length_unit=metric"
        return cache.get(url, forceRefresh, validate = {
            OpenMeteoParser.withMarine(emptyList(), it)
        }) { download(url) }
    }

    // Both endpoints accept 16 days; actual variable coverage may be shorter.
    private fun coordinates(point: Coordinates) =
        "latitude=${point.latitude}&longitude=${point.longitude}" +
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
