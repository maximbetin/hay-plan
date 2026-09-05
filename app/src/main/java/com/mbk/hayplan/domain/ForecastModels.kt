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
    val warnings: List<String>,
    val marineCoverage: MarineCoverage = MarineCoverage.NONE,
    /** Mean score before safety/comfort caps; used only to order otherwise tied locations. */
    val uncappedScore: Int = score,
)

data class HourlyAssessment(val time: LocalDateTime, val evaluation: ConditionsScore?)

data class BestWindow(
    val start: LocalTime,
    val end: LocalTime,
    val rating: Rating,
    val score: Int,
    val factors: List<FactorResult>,
    val warnings: List<String> = emptyList(),
    val marineCoverage: MarineCoverage = MarineCoverage.NONE,
)

fun ratingFor(score: Int): Rating = when {
    score >= 90 -> Rating.EXCELLENT
    score >= 60 -> Rating.VERY_GOOD
    score >= 40 -> Rating.GOOD
    score >= 20 -> Rating.FAIR
    else -> Rating.POOR
}
