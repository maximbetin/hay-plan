package com.mbk.hayplan.ui

import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.DayUnavailableReason
import com.mbk.hayplan.domain.Rating
import com.mbk.hayplan.domain.ratingFor
import com.mbk.hayplan.data.HayPlanLocation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal data class WeatherValue(val label: String, val value: String)
internal data class DayWeatherSummary(val headline: String, val values: List<WeatherValue>)

internal fun dayWeatherHeadline(summary: DayWeatherSummary, language: AppLanguage): String {
    if (language == AppLanguage.ENGLISH) return summary.headline
    val strings = UiStrings(language)
    fun value(label: String) = strings(summary.values.first { it.label == label }.value)
    return "Sensación ${value("Feels like")} · Prob. lluvia ${value("Rain chance")}\n" +
        "Rachas ${value("Wind gusts")} · Nubes ${value("Cloud cover")}"
}

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
        ?.let { "${it.average().roundToInt()}% avg" } ?: "Unknown"
    val humidity = values { it.relativeHumidityPercent?.takeIf { v -> v in 0..100 }?.toDouble() }
        ?.let { "${it.average().roundToInt()}% avg" } ?: "Unknown"
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

internal fun cardConditions(summary: DayWeatherSummary?, activity: ActivityType, coastal: Boolean,
                            language: AppLanguage = AppLanguage.ENGLISH): String? {
    if (summary == null) return null
    val strings = UiStrings(language)
    fun value(label: String) = strings(summary.values.first { it.label == label }.value
        .removeSuffix(" max").removeSuffix(" avg"))
    return if (language == AppLanguage.SPANISH && activity == ActivityType.BEACH && coastal) {
        "Sensación ${value("Feels like")} · Agua ${value("Water temperature")} · Olas ${value("Waves")}\n" +
            "Lluvia ${value("Rain chance")} · Rachas ${value("Wind gusts")} · Nubes ${value("Cloud cover")}"
    } else if (language == AppLanguage.SPANISH) {
        "Sensación ${value("Feels like")} · Lluvia ${value("Rain chance")} · Rachas ${value("Wind gusts")}\n" +
            "Nubes ${value("Cloud cover")} · UV ${value("UV index")}"
    } else if (activity == ActivityType.BEACH && coastal) {
        "Feels ${value("Feels like")} · Water ${value("Water temperature")} · Waves ${value("Waves")}\n" +
            "Rain ${value("Rain chance")} · Gusts ${value("Wind gusts")} · Clouds ${value("Cloud cover")}"
    } else {
        "Feels ${value("Feels like")} · Rain ${value("Rain chance")} · Gusts ${value("Wind gusts")}\n" +
            "Clouds ${value("Cloud cover")} · UV ${value("UV index")}"
    }
}

internal fun forecastConfidenceLabel(date: LocalDate, today: LocalDate): String? = when {
    isLongRangeOutlook(date, today) -> "Long-range outlook · lower confidence"
    date.isAfter(today.plusDays(2)) -> "Later outlook · forecast may change"
    else -> null
}

internal fun isLongRangeOutlook(date: LocalDate, today: LocalDate): Boolean =
    date.isAfter(today.plusDays(6))

internal enum class ScorePrecision { EXACT, BAND, HIDDEN }

internal fun scorePrecision(date: LocalDate, today: LocalDate): ScorePrecision = when {
    isLongRangeOutlook(date, today) -> ScorePrecision.HIDDEN
    date.isAfter(today.plusDays(2)) -> ScorePrecision.BAND
    else -> ScorePrecision.EXACT
}

internal fun scoreBand(score: Int): String = ratingRange(ratingFor(score))

internal fun ratingRange(rating: Rating): String = when (rating) {
    Rating.POOR -> "0–19"
    Rating.FAIR -> "20–39"
    Rating.GOOD -> "40–59"
    Rating.VERY_GOOD -> "60–89"
    Rating.EXCELLENT -> "90–100"
}

// Equal-height bars within a band avoid implying meaningful one-point differences.
internal fun scoreBandMidpoint(score: Int): Int = when (ratingFor(score)) {
    Rating.POOR -> 10
    Rating.FAIR -> 30
    Rating.GOOD -> 50
    Rating.VERY_GOOD -> 75
    Rating.EXCELLENT -> 95
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
    outlooks.all { it.hourly.isEmpty() && it.dayUnavailableReason == DayUnavailableReason.NoHoursRemaining }

private fun number(value: Double) = String.format(Locale.US, "%.1f", value)
