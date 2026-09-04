package com.mbk.hayplan.ui

import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.data.HayPlanLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WeatherValue(val label: String, val value: String)
internal data class DayWeatherSummary(val headline: String, val values: List<WeatherValue>)

internal fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ENGLISH))

internal fun forecastContextLabel(location: HayPlanLocation, activity: ActivityType, date: LocalDate): String {
    val place = listOfNotNull(location.name, location.weatherReference, activity.label,
        if (activity == ActivityType.BEACH) location.coast?.name else null).joinToString(" · ")
    return "$place\n${formatDate(date)}"
}

/** Describe exactly the daylight slots being assessed, never just their known subset. */
internal fun dayWeatherSummary(outlook: ActivityOutlook, hours: List<HourlyConditions>, coastal: Boolean = true): DayWeatherSummary? {
    if (outlook.hourly.isEmpty()) return null
    val byTime = hours.associateBy { it.time }
    val slots = outlook.hourly.map { byTime[it.time] }
    fun values(read: (HourlyConditions) -> Double?): List<Double>? {
        val known = slots.mapNotNull { it?.let(read)?.takeIf(Double::isFinite) }
        return known.takeIf { it.size == slots.size }
    }
    fun range(values: List<Double>?): String {
        if (values == null) return "Unknown"
        val low = number(values.min())
        val high = number(values.max())
        return if (low == high) "$low°C" else "$low–$high°C"
    }
    val air = range(values { it.airTemperatureC })
    val wind = values { it.windSpeedKmh?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.max())} km/h max" } ?: "Unknown"
    val chance = values { it.precipitationProbabilityPercent?.takeIf { v -> v in 0..100 }?.toDouble() }
        ?.let { "${it.max().toInt()}% max" } ?: "Unknown"
    val rain = values { it.precipitationMm?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.sum())} mm total" } ?: "Unknown"
    val details = buildList {
        add(WeatherValue("Air temperature", air))
        add(WeatherValue("Wind", wind))
        add(WeatherValue("Rain chance", chance))
        add(WeatherValue("Rainfall", rain))
        if (outlook.activity == ActivityType.BEACH && coastal) {
            add(WeatherValue("Water temperature", range(values { it.seaTemperatureC })))
            add(WeatherValue("Waves", values { it.waveHeightM?.takeIf { v -> v >= 0 } }
                ?.let { "${number(it.max())} m max" } ?: "Unknown"))
        }
    }
    return DayWeatherSummary("Air $air · Rain chance $chance\nWind $wind", details)
}

internal fun daylightAverageLabel(count: Int, remainingToday: Boolean): String {
    val period = if (remainingToday) "remaining daylight" else "daylight"
    val hours = if (count == 1) "hour" else "hours"
    return "Average of $count $period $hours"
}

private fun number(value: Double) = String.format(Locale.US, "%.1f", value)
