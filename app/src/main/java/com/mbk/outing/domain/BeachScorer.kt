package com.mbk.outing.domain

import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

object BeachScorer {
    private const val WINDOW_HOURS = 3

    fun swimming(hours: List<HourlyConditions>): ActivityRecommendation =
        bestRecommendation("Swimming", hours, ::swimmingFactors)

    fun sunbathing(hours: List<HourlyConditions>): ActivityRecommendation =
        bestRecommendation("Sunbathing", hours, ::sunbathingFactors)

    private fun bestRecommendation(
        activity: String,
        hours: List<HourlyConditions>,
        factorRules: (WindowConditions) -> List<FactorResult>,
    ): ActivityRecommendation {
        val daylightHours = hours.filter { it.isDaylight }.sortedBy { it.time }
        val windows = daylightHours.windowed(WINDOW_HOURS).filter(::isContiguous)
        require(windows.isNotEmpty()) { "At least three contiguous daylight hours are required" }

        val best = windows
            .map { window -> window to factorRules(summarize(window)) }
            .maxBy { (_, factors) -> factors.sumOf { it.points } }

        val (window, factors) = best
        val score = factors.sumOf { it.points }.coerceIn(0, 100)

        return ActivityRecommendation(
            activity = activity,
            rating = ratingFor(score),
            score = score,
            bestStart = window.first().time.toLocalTime(),
            bestEnd = window.last().time.toLocalTime().plusHours(1),
            factors = factors,
        )
    }

    private fun isContiguous(window: List<HourlyConditions>): Boolean =
        window.zipWithNext().all { (first, second) ->
            Duration.between(first.time, second.time) == Duration.ofHours(1)
        }

    private fun summarize(window: List<HourlyConditions>) = WindowConditions(
        airTemperatureC = window.map { it.airTemperatureC }.average(),
        precipitationProbabilityPercent = window.maxOf { it.precipitationProbabilityPercent },
        cloudCoverPercent = window.map { it.cloudCoverPercent }.average().roundToInt(),
        windSpeedKmh = window.maxOf { it.windSpeedKmh },
        seaTemperatureC = window.map { it.seaTemperatureC }.average(),
        waveHeightM = window.maxOf { it.waveHeightM },
    )

    private fun swimmingFactors(c: WindowConditions): List<FactorResult> = listOf(
        when {
            c.waveHeightM <= 0.5 -> factor("Waves", metres(c.waveHeightM), "Calm", 25, FactorOutcome.POSITIVE)
            c.waveHeightM <= 0.8 -> factor("Waves", metres(c.waveHeightM), "Moderate", 18, FactorOutcome.MIXED)
            c.waveHeightM <= 1.2 -> factor("Waves", metres(c.waveHeightM), "Choppy", 8, FactorOutcome.NEGATIVE)
            else -> factor("Waves", metres(c.waveHeightM), "Too rough", 0, FactorOutcome.NEGATIVE)
        },
        when {
            c.seaTemperatureC >= 20 -> factor("Water", celsius(c.seaTemperatureC), "Comfortable", 25, FactorOutcome.POSITIVE)
            c.seaTemperatureC >= 18 -> factor("Water", celsius(c.seaTemperatureC), "Fresh", 18, FactorOutcome.MIXED)
            c.seaTemperatureC >= 16 -> factor("Water", celsius(c.seaTemperatureC), "Cold", 8, FactorOutcome.NEGATIVE)
            else -> factor("Water", celsius(c.seaTemperatureC), "Very cold", 0, FactorOutcome.NEGATIVE)
        },
        when {
            c.windSpeedKmh <= 15 -> factor("Wind", kmh(c.windSpeedKmh), "Light", 20, FactorOutcome.POSITIVE)
            c.windSpeedKmh <= 25 -> factor("Wind", kmh(c.windSpeedKmh), "Breezy", 12, FactorOutcome.MIXED)
            c.windSpeedKmh <= 35 -> factor("Wind", kmh(c.windSpeedKmh), "Strong", 4, FactorOutcome.NEGATIVE)
            else -> factor("Wind", kmh(c.windSpeedKmh), "Very strong", 0, FactorOutcome.NEGATIVE)
        },
        precipitationFactor(c.precipitationProbabilityPercent, 20),
        when {
            c.airTemperatureC >= 22 -> factor("Air", celsius(c.airTemperatureC), "Warm", 10, FactorOutcome.POSITIVE)
            c.airTemperatureC >= 18 -> factor("Air", celsius(c.airTemperatureC), "Mild", 7, FactorOutcome.MIXED)
            else -> factor("Air", celsius(c.airTemperatureC), "Cool", 2, FactorOutcome.NEGATIVE)
        },
    )

    private fun sunbathingFactors(c: WindowConditions): List<FactorResult> = listOf(
        when {
            c.airTemperatureC >= 24 -> factor("Air", celsius(c.airTemperatureC), "Warm", 30, FactorOutcome.POSITIVE)
            c.airTemperatureC >= 20 -> factor("Air", celsius(c.airTemperatureC), "Pleasant", 24, FactorOutcome.POSITIVE)
            c.airTemperatureC >= 17 -> factor("Air", celsius(c.airTemperatureC), "A little cool", 14, FactorOutcome.MIXED)
            else -> factor("Air", celsius(c.airTemperatureC), "Too cool", 4, FactorOutcome.NEGATIVE)
        },
        when {
            c.cloudCoverPercent <= 20 -> factor("Clouds", "${c.cloudCoverPercent}%", "Mostly clear", 30, FactorOutcome.POSITIVE)
            c.cloudCoverPercent <= 45 -> factor("Clouds", "${c.cloudCoverPercent}%", "Some cloud", 22, FactorOutcome.POSITIVE)
            c.cloudCoverPercent <= 70 -> factor("Clouds", "${c.cloudCoverPercent}%", "Often cloudy", 10, FactorOutcome.MIXED)
            else -> factor("Clouds", "${c.cloudCoverPercent}%", "Overcast", 2, FactorOutcome.NEGATIVE)
        },
        precipitationFactor(c.precipitationProbabilityPercent, 25),
        when {
            c.windSpeedKmh <= 15 -> factor("Wind", kmh(c.windSpeedKmh), "Light", 15, FactorOutcome.POSITIVE)
            c.windSpeedKmh <= 25 -> factor("Wind", kmh(c.windSpeedKmh), "Breezy", 9, FactorOutcome.MIXED)
            else -> factor("Wind", kmh(c.windSpeedKmh), "Uncomfortable", 2, FactorOutcome.NEGATIVE)
        },
    )

    private fun precipitationFactor(probability: Int, maximumPoints: Int): FactorResult = when {
        probability <= 10 -> factor("Rain", "$probability%", "Unlikely", maximumPoints, FactorOutcome.POSITIVE)
        probability <= 30 -> factor("Rain", "$probability%", "Possible", (maximumPoints * 0.6).roundToInt(), FactorOutcome.MIXED)
        probability <= 50 -> factor("Rain", "$probability%", "Quite possible", (maximumPoints * 0.25).roundToInt(), FactorOutcome.NEGATIVE)
        else -> factor("Rain", "$probability%", "Likely", 0, FactorOutcome.NEGATIVE)
    }

    private fun ratingFor(score: Int): Rating = when (score) {
        in 80..100 -> Rating.EXCELLENT
        in 60..79 -> Rating.VERY_GOOD
        in 40..59 -> Rating.GOOD
        in 20..39 -> Rating.FAIR
        else -> Rating.POOR
    }

    private fun factor(
        label: String,
        value: String,
        detail: String,
        points: Int,
        outcome: FactorOutcome,
    ) = FactorResult(label, value, detail, outcome, points)

    private fun celsius(value: Double) = String.format(Locale.US, "%.0f°C", value)
    private fun kmh(value: Double) = String.format(Locale.US, "%.0f km/h", value)
    private fun metres(value: Double) = String.format(Locale.US, "%.1f m", value)

    private data class WindowConditions(
        val airTemperatureC: Double,
        val precipitationProbabilityPercent: Int,
        val cloudCoverPercent: Int,
        val windSpeedKmh: Double,
        val seaTemperatureC: Double,
        val waveHeightM: Double,
    )
}
