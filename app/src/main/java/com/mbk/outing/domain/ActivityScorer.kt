package com.mbk.outing.domain

import java.util.Locale
import kotlin.math.roundToInt

data class ConditionsScore(
    val score: Int,
    val factors: List<FactorResult>,
    val warnings: List<String>,
    val maximumScore: Int,
)

/** Pure Kotlin rules. Score one hour for a day assessment, or a complete window. */
object ActivityScorer {
    fun score(activity: ActivityType, hours: List<HourlyConditions>): ConditionsScore? {
        if (hours.isEmpty() || !hours.all { complete(it, activity) }) return null
        val air = hours.map { requireNotNull(it.airTemperatureC) }.average()
        val wind = hours.maxOf { requireNotNull(it.windSpeedKmh) }
        val rainChance = hours.maxOf { requireNotNull(it.precipitationProbabilityPercent) }
        val rain = hours.sumOf { requireNotNull(it.precipitationMm) }
        val factors = mutableListOf<FactorResult>()
        val warnings = mutableListOf<String>()
        var maximumScore = 100

        fun limit(maximum: Int, reason: String) {
            maximumScore = minOf(maximumScore, maximum)
            warnings += reason
        }
        fun factor(label: String, value: String, detail: String, points: Int, maximum: Int) {
            factors += FactorResult(label, value, detail, when {
                points >= maximum * 0.7 -> FactorOutcome.POSITIVE
                points >= maximum * 0.4 -> FactorOutcome.MIXED
                else -> FactorOutcome.NEGATIVE
            }, points)
        }

        when (activity) {
            ActivityType.BEACH -> {
                factor("Air", celsius(air), "Average", when {
                    air in 22.0..28.0 -> 20
                    air >= 18 && air < 22 -> 14
                    air > 28 && air < 32 -> 12
                    else -> 5
                }, 20)
                val water = hours.map { requireNotNull(it.seaTemperatureC) }.average()
                factor("Water", celsius(water), "Average", when {
                    water >= 20 -> 15
                    water >= 18 -> 10
                    water >= 16 -> 5
                    else -> 0
                }, 15)
                val waves = hours.maxOf { requireNotNull(it.waveHeightM) }
                factor("Waves", String.format(Locale.US, "%.2f m", waves), "Highest", when {
                    waves <= 0.5 -> 20
                    waves <= 0.8 -> 14
                    waves <= 1.2 -> 6
                    else -> 0
                }, 20)
                val clouds = hours.map { requireNotNull(it.cloudCoverPercent) }.average().roundToInt()
                factor("Cloud cover", "$clouds%", "Average", when {
                    clouds <= 20 -> 10
                    clouds <= 45 -> 7
                    clouds <= 70 -> 3
                    else -> 0
                }, 10)
                factor("Wind", "${decimal(wind)} km/h", "Highest", windPoints(wind, 15), 15)
                factor("Rain chance", "$rainChance%", "Highest", rainPoints(rainChance, 20), 20)
                if (waves > 1.2) limit(19, "Rough waves: rating limited to Poor.")
                else if (waves > 0.8) limit(59, "Choppy waves: rating limited to Good.")
                if (hours.any { requireNotNull(it.seaTemperatureC) < 16 }) {
                    limit(39, "Cold water: rating limited to Fair.")
                }
            }
            ActivityType.HIKING -> {
                factor("Air", celsius(air), "Average", when {
                    air in 15.0..24.0 -> 40
                    air >= 10 && air < 15 -> 30
                    air > 24 && air < 29 -> 28
                    air >= 5 && air < 10 -> 15
                    air >= 29 && air < 32 -> 15
                    else -> 0
                }, 40)
                factor("Wind", "${decimal(wind)} km/h", "Highest", windPoints(wind, 25), 25)
                factor("Rain chance", "$rainChance%", "Highest", rainPoints(rainChance, 35), 35)
                if (hours.any { requireNotNull(it.airTemperatureC) < 0 }) {
                    limit(39, "Freezing temperatures: rating limited to Fair.")
                }
            }
        }
        factors += FactorResult("Rain", "${decimal(rain)} mm", "Total", when {
            rain <= 0.2 -> FactorOutcome.POSITIVE
            rain < 1 -> FactorOutcome.MIXED
            else -> FactorOutcome.NEGATIVE
        }, 0)
        if (wind > 35) limit(19, "Strong wind: rating limited to Poor.")
        if (rain >= 3) limit(19, "Rainfall: rating limited to Poor.")
        else if (rain >= 1 || rainChance > 50) limit(39, "Rain: rating limited to Fair.")
        if (hours.any { requireNotNull(it.airTemperatureC) >= 35 }) {
            limit(39, "High heat: rating limited to Fair.")
        }
        return ConditionsScore(minOf(factors.sumOf { it.points }, maximumScore), factors, warnings, maximumScore)
    }

    private fun complete(h: HourlyConditions, activity: ActivityType): Boolean {
        fun Double?.finite() = this != null && isFinite()
        fun Double?.nonNegative() = this != null && isFinite() && this >= 0
        val weather = h.airTemperatureC.finite() && h.windSpeedKmh.nonNegative() &&
            h.precipitationMm.nonNegative() && h.precipitationProbabilityPercent?.let { it in 0..100 } == true
        return weather && (activity == ActivityType.HIKING || (
            h.seaTemperatureC.finite() && h.waveHeightM.nonNegative() &&
                h.cloudCoverPercent?.let { it in 0..100 } == true
            ))
    }

    private fun windPoints(wind: Double, maximum: Int): Int = when {
        wind <= 15 -> maximum
        wind <= 25 -> (maximum * 0.6).roundToInt()
        wind <= 35 -> (maximum * 0.2).roundToInt()
        else -> 0
    }

    private fun rainPoints(chance: Int, maximum: Int): Int = when {
        chance <= 10 -> maximum
        chance <= 30 -> (maximum * 0.6).roundToInt()
        chance <= 50 -> (maximum * 0.25).roundToInt()
        else -> 0
    }

    private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun celsius(value: Double) = "${decimal(value)}°C"
}
