package com.mbk.hayplan.ui

import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.data.HayPlanLocation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WeatherValue(val label: String, val value: String)
internal data class DayWeatherSummary(val headline: String, val values: List<WeatherValue>)

internal fun formatDate(
    date: LocalDate,
    today: LocalDate? = null,
    language: AppLanguage = AppLanguage.ENGLISH,
): String {
    val prefix = when (date) {
        today -> if (language == AppLanguage.SPANISH) "Hoy" else "Today"
        today?.plusDays(1) -> if (language == AppLanguage.SPANISH) "Mañana" else "Tomorrow"
        else -> null
    }
    val numeric = date.format(DateTimeFormatter.ofPattern("dd/MM"))
    if (prefix != null) return "$prefix · $numeric"
    val locale = if (language == AppLanguage.SPANISH) Locale.forLanguageTag("es-ES") else Locale.ENGLISH
    return date.format(DateTimeFormatter.ofPattern("EEE dd/MM", locale))
}

internal fun forecastContextLabel(
    location: HayPlanLocation,
    activity: ActivityType,
    date: LocalDate,
    language: AppLanguage = AppLanguage.ENGLISH,
): String {
    val strings = UiStrings(language)
    val place = listOfNotNull(location.name, location.weatherReference, strings.activity(activity),
        if (activity == ActivityType.BEACH) location.coast?.name else null).joinToString(" · ")
    return "$place\n${formatDate(date, language = language)}"
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
    val feels = range(values { it.apparentTemperatureC })
    val wind = values { it.windSpeedKmh?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.max())} km/h max" } ?: "Unknown"
    val gusts = values { it.windGustsKmh?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.max())} km/h max" } ?: "Unknown"
    val chance = values { it.precipitationProbabilityPercent?.takeIf { v -> v in 0..100 }?.toDouble() }
        ?.let { "${it.max().toInt()}% max" } ?: "Unknown"
    val rain = values { it.precipitationMm?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.sum())} mm total" } ?: "Unknown"
    val clouds = values { it.cloudCoverPercent?.takeIf { v -> v in 0..100 }?.toDouble() }
        ?.let { "${it.average().toInt()}% avg" } ?: "Unknown"
    val humidity = values { it.relativeHumidityPercent?.takeIf { v -> v in 0..100 }?.toDouble() }
        ?.let { "${it.average().toInt()}% avg" } ?: "Unknown"
    val visibility = values { it.visibilityM?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.min() / 1_000)} km min" } ?: "Unknown"
    val uv = values { it.uvIndex?.takeIf { v -> v >= 0 } }
        ?.let { "${number(it.max())} max" } ?: "Unknown"
    val details = buildList {
        add(WeatherValue("Air temperature", air))
        add(WeatherValue("Feels like", feels))
        add(WeatherValue("Wind", wind))
        add(WeatherValue("Wind gusts", gusts))
        add(WeatherValue("Rain chance", chance))
        add(WeatherValue("Rainfall", rain))
        add(WeatherValue("Cloud cover", clouds))
        add(WeatherValue("Humidity", humidity))
        add(WeatherValue("Visibility", visibility))
        add(WeatherValue("UV index", uv))
        if (outlook.activity == ActivityType.BEACH && coastal) {
            add(WeatherValue("Water temperature", range(values { it.seaTemperatureC })))
            add(WeatherValue("Waves", values { it.waveHeightM?.takeIf { v -> v >= 0 } }
                ?.let { "${number(it.max())} m max" } ?: "Unknown"))
        }
    }
    return DayWeatherSummary("Feels $feels · Rain chance $chance\nGusts $gusts · Clouds $clouds", details)
}

internal fun daylightAverageLabel(count: Int, remainingToday: Boolean): String {
    val period = if (remainingToday) "remaining daylight" else "daylight"
    val hours = if (count == 1) "hour" else "hours"
    return "Average of $count $period $hours"
}

internal fun cardConditions(summary: DayWeatherSummary?, activity: ActivityType, coastal: Boolean): String? {
    if (summary == null) return null
    fun value(label: String) = summary.values.first { it.label == label }.value
        .removeSuffix(" max").removeSuffix(" avg")
    return if (activity == ActivityType.BEACH && coastal) {
        "Feels ${value("Feels like")} · Water ${value("Water temperature")} · Waves ${value("Waves")}\n" +
            "Rain ${value("Rain chance")} · Gusts ${value("Wind gusts")} · Clouds ${value("Cloud cover")}"
    } else {
        "Feels ${value("Feels like")} · Rain ${value("Rain chance")} · Gusts ${value("Wind gusts")}\n" +
            "Clouds ${value("Cloud cover")} · UV ${value("UV index")}"
    }
}

internal fun beachCoverageLabel(
    coastal: Boolean,
    coverage: MarineCoverage,
    language: AppLanguage = AppLanguage.ENGLISH,
): String = if (coastal) UiStrings(language).coverage(coverage)
    else UiStrings(language)("Inland estimate · no beach")

internal fun daylightHasEnded(
    selectedDate: LocalDate,
    now: LocalDateTime,
    outlooks: Collection<ActivityOutlook>,
): Boolean = selectedDate == now.toLocalDate() && outlooks.isNotEmpty() &&
    outlooks.all { it.hourly.isEmpty() && it.dayUnavailableReason == "No hours remaining." }

private fun number(value: Double) = String.format(Locale.US, "%.1f", value)
