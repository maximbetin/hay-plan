package com.mbk.hayplan.domain

import java.time.LocalDateTime
import java.time.LocalTime

data class HourlyConditions(
    val time: LocalDateTime,
    val isDaylight: Boolean,
    val airTemperatureC: Double?,
    val precipitationProbabilityPercent: Int?,
    val cloudCoverPercent: Int?,
    val windSpeedKmh: Double?,
    val seaTemperatureC: Double?,
    val waveHeightM: Double?,
    val precipitationMm: Double? = null,
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null,
    val apparentTemperatureC: Double? = null,
    val relativeHumidityPercent: Int? = null,
    val visibilityM: Double? = null,
    val weatherCode: Int? = null,
    val windGustsKmh: Double? = null,
    val uvIndex: Double? = null,
)

enum class Rating(val label: String) {
    POOR("Poor"),
    FAIR("Fair"),
    GOOD("Good"),
    VERY_GOOD("Very Good"),
    EXCELLENT("Excellent"),
}

enum class FactorOutcome {
    POSITIVE,
    MIXED,
    NEGATIVE,
}

data class FactorResult(
    val label: String,
    val value: String,
    val detail: String,
    val outcome: FactorOutcome,
    val points: Int,
    val maximumPoints: Int = 0,
)

enum class ActivityType(val label: String) {
    BEACH("Beach"),
    HIKING("Hiking"),
}

enum class MarineCoverage(val label: String) {
    NONE("Weather only"),
    WATER("Weather + water temperature"),
    WAVES("Weather + waves"),
    FULL("Weather + sea"),
    MIXED("Sea data varies by hour");

    companion object {
        fun combine(values: Iterable<MarineCoverage>): MarineCoverage =
            values.toSet().let { if (it.isEmpty()) NONE else it.singleOrNull() ?: MIXED }
    }
}

data class DayRating(
    val rating: Rating,
    val score: Int,
    val assessedHours: Int,
    val goodHours: Int,
    val warnings: List<ForecastWarning>,
    val marineCoverage: MarineCoverage = MarineCoverage.NONE,
    /** Mean score before safety/comfort caps; used only to order otherwise tied locations. */
    val uncappedScore: Int = score,
    /** Conservative earned points against the full activity profile, used for evidence-aware ranking. */
    val evidenceScore: Int = score,
)

data class HourlyAssessment(val time: LocalDateTime, val evaluation: ConditionsScore?)

sealed interface DayUnavailableReason {
    data object NoForecast : DayUnavailableReason
    data object NoHoursRemaining : DayUnavailableReason
    data class Incomplete(val ratedHours: Int, val expectedHours: Int) : DayUnavailableReason
}

enum class WindowUnavailableReason { NO_COMPLETE_WINDOW }

data class BestWindow(
    val start: LocalTime,
    val end: LocalTime,
    val rating: Rating,
    val score: Int,
    val factors: List<FactorResult>,
    val warnings: List<ForecastWarning> = emptyList(),
    val marineCoverage: MarineCoverage = MarineCoverage.NONE,
)

fun ratingFor(score: Int): Rating = when {
    score >= 90 -> Rating.EXCELLENT
    score >= 60 -> Rating.VERY_GOOD
    score >= 40 -> Rating.GOOD
    score >= 20 -> Rating.FAIR
    else -> Rating.POOR
}

/** Pick the warning that is most important to show when space permits only one. */
enum class ForecastWarning(val message: String, val priority: Int) {
    ROUGH_WAVES("Rough waves.", 3),
    CHOPPY_WAVES("Choppy waves.", 1),
    COLD_WATER("Cold water.", 2),
    VERY_STRONG_WIND("Very strong wind.", 3),
    VERY_STRONG_GUSTS("Very strong gusts.", 3),
    STRONG_GUSTS("Strong gusts.", 2),
    MODERATE_GUSTS("Moderate gusts.", 1),
    HEAVY_RAIN("Heavy rain.", 2),
    RAIN("Rain.", 1),
    POSSIBLE_RAIN("Possible rain.", 1),
    FREEZING_TEMPERATURES("Freezing temperatures.", 2),
    COLD("Cold.", 1),
    EXTREME_HEAT("Extreme heat.", 2),
    HOT("Hot.", 1),
    OVERCAST("Overcast.", 1),
    VERY_LOW_VISIBILITY("Very low visibility.", 2),
    LOW_VISIBILITY("Low visibility.", 1),
    EXTREME_UV("Extreme UV · Use protection.", 1),
    VERY_HIGH_UV("Very high UV · Use protection.", 1),
    THUNDERSTORM("Thunderstorm.", 3),
    FREEZING_RAIN("Freezing rain.", 3),
    HEAVY_RAIN_OR_SNOW("Heavy rain or snow.", 3),
    FOG("Fog.", 2),
    MODERATE_RAIN_OR_SNOW("Moderate rain or snow.", 2),
    LIGHT_RAIN_OR_SNOW("Light rain or snow.", 1);

    fun contains(text: String): Boolean = message.contains(text)
}

fun primaryWarning(warnings: List<ForecastWarning>): ForecastWarning? = warnings.maxByOrNull { it.priority }
