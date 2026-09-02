package com.mbk.outing.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class OpenMeteoClient {
    suspend fun weather(beach: Beach): String = fetch(
        "https://api.open-meteo.com/v1/forecast?${coordinates(beach)}" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,cloud_cover,wind_speed_10m" +
            "&daily=sunrise,sunset&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm",
    )

    suspend fun marine(beach: Beach): String = fetch(
        "https://marine-api.open-meteo.com/v1/marine?${coordinates(beach)}" +
            "&hourly=wave_height,sea_surface_temperature&cell_selection=sea&length_unit=metric",
    )

    // Both endpoints accept 16 days; actual variable coverage may be shorter.
    private fun coordinates(beach: Beach) =
        "latitude=${beach.latitude}&longitude=${beach.longitude}" +
            "&timezone=Europe%2FMadrid&forecast_days=16"

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
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
}
