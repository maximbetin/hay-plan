package com.mbk.outing.data

import com.mbk.outing.domain.HourlyConditions
import org.json.JSONObject
import java.time.LocalDateTime

/** Keeps API array layout, null handling, and timestamp alignment out of scoring code. */
object OpenMeteoParser {
    fun weather(json: String): List<HourlyConditions> {
        val root = JSONObject(json)
        val hourly = root.getJSONObject("hourly")
        val times = hourly.times()
        val daily = root.optJSONObject("daily")
        val sun = daily?.optJSONArray("time")?.let { dates ->
            (0 until dates.length()).associate { i ->
                dates.getString(i) to (
                    daily.dateTime("sunrise", i) to daily.dateTime("sunset", i)
                )
            }
        }.orEmpty()
        return times.mapIndexed { i, time ->
            val (sunrise, sunset) = sun[time.toLocalDate().toString()] ?: (null to null)
            // An entire hour must fit before sunset, not just its starting instant.
            val daylight = sunrise != null && sunset != null &&
                !time.isBefore(sunrise) && !time.plusHours(1).isAfter(sunset)
            // Open-Meteo precipitation describes the PRECEDING hour. Shift its
            // timestamp so the value at 12:00 describes our 11:00–12:00 slot.
            val next = if (times.getOrNull(i + 1) == time.plusHours(1)) i + 1 else -1
            HourlyConditions(
                time = time,
                isDaylight = daylight,
                airTemperatureC = hourly.number("temperature_2m", i),
                precipitationProbabilityPercent = hourly.number("precipitation_probability", next)
                    ?.takeIf { it in 0.0..100.0 }?.toInt(),
                precipitationMm = hourly.number("precipitation", next)?.takeIf { it >= 0 },
                cloudCoverPercent = hourly.number("cloud_cover", i)
                    ?.takeIf { it in 0.0..100.0 }?.toInt(),
                windSpeedKmh = hourly.number("wind_speed_10m", i)?.takeIf { it >= 0 },
                seaTemperatureC = null,
                waveHeightM = null,
            )
        }
    }

    fun withMarine(hours: List<HourlyConditions>, json: String): List<HourlyConditions> {
        val marine = JSONObject(json).getJSONObject("hourly")
        val byTime = marine.times().mapIndexed { i, time ->
            time to Pair(
                marine.number("sea_surface_temperature", i),
                marine.number("wave_height", i)?.takeIf { it >= 0 },
            )
        }.toMap()
        // Join timestamps, never assume both endpoint arrays have matching indices.
        return hours.map { hour ->
            val values = byTime[hour.time]
            hour.copy(seaTemperatureC = values?.first, waveHeightM = values?.second)
        }
    }

    private fun JSONObject.times(): List<LocalDateTime> = getJSONArray("time").let { array ->
        (0 until array.length()).map { LocalDateTime.parse(array.getString(it)) }
    }

    private fun JSONObject.number(name: String, index: Int): Double? =
        optJSONArray(name)?.opt(index)?.let { (it as? Number)?.toDouble() }?.takeIf { it.isFinite() }

    private fun JSONObject.dateTime(name: String, index: Int): LocalDateTime? =
        (optJSONArray(name)?.opt(index) as? String)?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull()
        }
}
