package com.mbk.outing.domain

import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt

object BeachScorer {
    private const val WINDOW_HOURS = 3

    fun swimming(hours: List<HourlyConditions>): ActivityRecommendation? =
        bestRecommendation("Swimming", hours, ::swimmingFactors)

    fun sunbathing(hours: List<HourlyConditions>): ActivityRecommendation? =
        bestRecommendation("Sunbathing", hours, ::sunbathingFactors)

    private fun bestRecommendation(
        activity: String,
        hours: List<HourlyConditions>,
        factorRules: (WindowConditions) -> List<FactorResult>,
    ): ActivityRecommendation? {
        val daylightHours = hours.filter { it.isDaylight }.sortedBy { it.time }
        return daylightHours.windowed(WINDOW_HOURS)
            .filter(::isContiguous)
            .filter { window -> window.all { hasRequiredData(it, activity) } }
            .map { window ->
                val conditions = summarize(window)
                val factors = factorRules(conditions) + factor(
                    "Rain amount", "${decimal(conditions.precipitationMm)} mm", "Total in window",
                    0, when {
                        conditions.precipitationMm <= 0.2 -> FactorOutcome.POSITIVE
                        conditions.precipitationMm < 1 -> FactorOutcome.MIXED
                        else -> FactorOutcome.NEGATIVE
                    },
                )
                val (score, warnings) = applyLimits(activity, conditions, factors.sumOf { it.points })
                ActivityRecommendation(
                    activity = activity,
                    rating = ratingFor(score),
                    score = score,
                    bestStart = window.first().time.toLocalTime(),
                    bestEnd = window.last().time.toLocalTime().plusHours(1),
                    factors = factors,
                    warnings = warnings,
                )
            }
            // Sorted chronologically: first maximum wins ties.
            .maxByOrNull { it.score }
    }

    private fun hasRequiredData(h: HourlyConditions, activity: String): Boolean {
        fun Double?.valid() = this != null && isFinite()
        val common = h.airTemperatureC.valid() &&
            h.windSpeedKmh?.let { it.isFinite() && it >= 0 } == true &&
            h.precipitationMm?.let { it.isFinite() && it >= 0 } == true &&
            h.precipitationProbabilityPercent?.let { it in 0..100 } == true
        return common && if (activity == "Swimming") {
            h.seaTemperatureC.valid() && h.waveHeightM?.let { it.isFinite() && it >= 0 } == true
        } else {
            h.cloudCoverPercent?.let { it in 0..100 } == true
        }
    }

    private fun applyLimits(activity: String, c: WindowConditions, total: Int): Pair<Int, List<String>> {
        var score = total.coerceIn(0, 100)
        val warnings = mutableListOf<String>()
        fun cap(maximum: Int, reason: String) {
            score = minOf(score, maximum)
            warnings += reason
        }
        if (activity == "Swimming") {
            val waves = requireNotNull(c.waveHeightM)
            if (waves > 1.2) cap(19, "Rough waves limit swimming to Poor, regardless of other conditions.")
            else if (waves > 0.8) cap(59, "Choppy waves limit swimming to Good.")
            if (requireNotNull(c.seaTemperatureC) < 16) cap(39, "Cold water limits swimming to Fair.")
        }
        if (c.windSpeedKmh > 35) cap(19, "Strong wind limits the rating to Poor.")
        if (c.precipitationMm >= 3) cap(19, "Rain in this window limits the rating to Poor.")
        else if (c.precipitationMm >= 1 || c.precipitationProbabilityPercent > 50) {
            cap(39, "Rain limits the rating to Fair.")
        }
        if (c.airTemperatureC >= 35) cap(39, "High heat limits the comfort rating to Fair.")
        return score to warnings
    }

    private fun isContiguous(window: List<HourlyConditions>): Boolean =
        window.zipWithNext().all { (first, second) ->
            first.time.toLocalDate() == second.time.toLocalDate() &&
                Duration.between(first.time, second.time) == Duration.ofHours(1)
        }

    private fun summarize(window: List<HourlyConditions>) = WindowConditions(
        airTemperatureC = window.map { requireNotNull(it.airTemperatureC) }.average(),
        precipitationProbabilityPercent = window.maxOf { requireNotNull(it.precipitationProbabilityPercent) },
        precipitationMm = window.sumOf { requireNotNull(it.precipitationMm) },
        cloudCoverPercent = window.mapNotNull { it.cloudCoverPercent }.average().takeIf { it.isFinite() }?.roundToInt(),
        windSpeedKmh = window.maxOf { requireNotNull(it.windSpeedKmh) },
        seaTemperatureC = window.mapNotNull { it.seaTemperatureC }.average().takeIf { it.isFinite() },
        waveHeightM = window.mapNotNull { it.waveHeightM }.maxOrNull(),
    )

    private fun swimmingFactors(c: WindowConditions): List<FactorResult> {
        val waves = requireNotNull(c.waveHeightM)
        val water = requireNotNull(c.seaTemperatureC)
        return listOf(
            when {
                waves <= 0.5 -> factor("Waves", metres(waves), "Calm", 25, FactorOutcome.POSITIVE)
                waves <= 0.8 -> factor("Waves", metres(waves), "Moderate", 18, FactorOutcome.MIXED)
                waves <= 1.2 -> factor("Waves", metres(waves), "Choppy", 8, FactorOutcome.NEGATIVE)
                else -> factor("Waves", metres(waves), "Too rough", 0, FactorOutcome.NEGATIVE)
            },
            when {
                water >= 20 -> factor("Water", celsius(water), "Comfortable", 25, FactorOutcome.POSITIVE)
                water >= 18 -> factor("Water", celsius(water), "Fresh", 18, FactorOutcome.MIXED)
                water >= 16 -> factor("Water", celsius(water), "Cold", 8, FactorOutcome.NEGATIVE)
                else -> factor("Water", celsius(water), "Very cold", 0, FactorOutcome.NEGATIVE)
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
    }

    private fun sunbathingFactors(c: WindowConditions): List<FactorResult> {
        val clouds = requireNotNull(c.cloudCoverPercent)
        return listOf(
            when {
                c.airTemperatureC >= 35 -> factor("Air", celsius(c.airTemperatureC), "Very hot", 4, FactorOutcome.NEGATIVE)
                c.airTemperatureC >= 30 -> factor("Air", celsius(c.airTemperatureC), "Hot", 20, FactorOutcome.MIXED)
                c.airTemperatureC >= 24 -> factor("Air", celsius(c.airTemperatureC), "Warm", 30, FactorOutcome.POSITIVE)
                c.airTemperatureC >= 20 -> factor("Air", celsius(c.airTemperatureC), "Pleasant", 24, FactorOutcome.POSITIVE)
                c.airTemperatureC >= 17 -> factor("Air", celsius(c.airTemperatureC), "A little cool", 14, FactorOutcome.MIXED)
                else -> factor("Air", celsius(c.airTemperatureC), "Too cool", 4, FactorOutcome.NEGATIVE)
            },
            when {
                clouds <= 20 -> factor("Clouds", "$clouds%", "Mostly clear", 30, FactorOutcome.POSITIVE)
                clouds <= 45 -> factor("Clouds", "$clouds%", "Some cloud", 22, FactorOutcome.POSITIVE)
                clouds <= 70 -> factor("Clouds", "$clouds%", "Often cloudy", 10, FactorOutcome.MIXED)
                else -> factor("Clouds", "$clouds%", "Overcast", 2, FactorOutcome.NEGATIVE)
            },
            precipitationFactor(c.precipitationProbabilityPercent, 25),
            when {
                c.windSpeedKmh <= 15 -> factor("Wind", kmh(c.windSpeedKmh), "Light", 15, FactorOutcome.POSITIVE)
                c.windSpeedKmh <= 25 -> factor("Wind", kmh(c.windSpeedKmh), "Breezy", 9, FactorOutcome.MIXED)
                else -> factor("Wind", kmh(c.windSpeedKmh), "Uncomfortable", 2, FactorOutcome.NEGATIVE)
            },
        )
    }

    private fun precipitationFactor(probability: Int, maximumPoints: Int): FactorResult = when {
        probability <= 10 -> factor("Rain chance", "$probability%", "Unlikely", maximumPoints, FactorOutcome.POSITIVE)
        probability <= 30 -> factor("Rain chance", "$probability%", "Possible", (maximumPoints * 0.6).roundToInt(), FactorOutcome.MIXED)
        probability <= 50 -> factor("Rain chance", "$probability%", "Quite possible", (maximumPoints * 0.25).roundToInt(), FactorOutcome.NEGATIVE)
        else -> factor("Rain chance", "$probability%", "Likely", 0, FactorOutcome.NEGATIVE)
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

    private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun celsius(value: Double) = "${decimal(value)}°C"
    private fun kmh(value: Double) = String.format(Locale.US, "%.1f km/h", value)
    private fun metres(value: Double) = String.format(Locale.US, "%.2f m", value)

    private data class WindowConditions(
        val airTemperatureC: Double,
        val precipitationProbabilityPercent: Int,
        val precipitationMm: Double,
        val cloudCoverPercent: Int?,
        val windSpeedKmh: Double,
        val seaTemperatureC: Double?,
        val waveHeightM: Double?,
    )
}
