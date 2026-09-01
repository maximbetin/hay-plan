package com.mbk.outing.domain

import java.time.LocalDateTime
import java.time.LocalTime

data class HourlyConditions(
    val time: LocalDateTime,
    val isDaylight: Boolean,
    val airTemperatureC: Double,
    val precipitationProbabilityPercent: Int,
    val cloudCoverPercent: Int,
    val windSpeedKmh: Double,
    val seaTemperatureC: Double,
    val waveHeightM: Double,
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
)

data class ActivityRecommendation(
    val activity: String,
    val rating: Rating,
    val score: Int,
    val bestStart: LocalTime,
    val bestEnd: LocalTime,
    val factors: List<FactorResult>,
)
