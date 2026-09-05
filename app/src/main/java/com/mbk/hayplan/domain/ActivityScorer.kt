package com.mbk.hayplan.domain

import java.util.Locale
import kotlin.math.roundToInt

data class ConditionsScore(
    val score: Int,
    val factors: List<FactorResult>,
    val warnings: List<String>,
    val maximumScore: Int,
    val marineCoverage: MarineCoverage = MarineCoverage.NONE,
) {
    val availablePoints: Int get() = factors.sumOf { it.maximumPoints }
    val pointsBeforeLimits: Int get() = (factors.sumOf { it.points } * 100.0 / availablePoints).roundToInt()
}

/** Pure Kotlin rules. Score one hour for a day assessment, or a complete window. */
object ActivityScorer {
    fun score(activity: ActivityType, hours: List<HourlyConditions>): ConditionsScore? {
        if (hours.isEmpty() || !hours.all(::complete)) return null
        val air = hours.map { requireNotNull(it.airTemperatureC) }.average()
        val feelsLike = hours.map { requireNotNull(it.apparentTemperatureC) }.average()
        val humidity = hours.map { requireNotNull(it.relativeHumidityPercent) }.average().roundToInt()
        val wind = hours.maxOf { requireNotNull(it.windSpeedKmh) }
        val gusts = hours.maxOf { requireNotNull(it.windGustsKmh) }
        val rainChance = hours.maxOf { requireNotNull(it.precipitationProbabilityPercent) }
        val rain = hours.sumOf { requireNotNull(it.precipitationMm) }
        val clouds = hours.map { requireNotNull(it.cloudCoverPercent) }.average().roundToInt()
        val visibility = hours.minOf { requireNotNull(it.visibilityM) }
        val uv = hours.maxOf { requireNotNull(it.uvIndex) }
        val weatherCode = hours.map { requireNotNull(it.weatherCode) }.maxBy(::weatherSeverity)
        val factors = mutableListOf<FactorResult>()
        val warnings = mutableListOf<String>()
        var maximumScore = 100

        fun limit(maximum: Int, reason: String) {
            maximumScore = minOf(maximumScore, maximum)
            if (reason !in warnings) warnings += reason
        }
        fun factor(label: String, value: String, detail: String, points: Int, maximum: Int) {
            factors += FactorResult(label, value, detail, when {
                points >= maximum * 0.7 -> FactorOutcome.POSITIVE
                points >= maximum * 0.4 -> FactorOutcome.MIXED
                else -> FactorOutcome.NEGATIVE
            }, points, maximum)
        }

        when (activity) {
            ActivityType.BEACH -> {
                factor("Feels like", celsius(feelsLike), "Average", beachTemperaturePoints(feelsLike), 15)
                val water = hours.mapNotNull { it.seaTemperatureC?.takeIf(Double::isFinite) }
                if (water.size == hours.size) {
                    val average = water.average()
                    factor("Water", celsius(average), "Average", when {
                        average >= 21 -> 12
                        average >= 19 -> 9
                        average >= 17 -> 5
                        else -> 0
                    }, 12)
                }
                val waves = hours.mapNotNull { it.waveHeightM?.takeIf { v -> v.isFinite() && v >= 0 } }
                if (waves.size == hours.size) {
                    val highest = waves.max()
                    factor("Waves", String.format(Locale.US, "%.2f m", highest), "Highest", when {
                        highest <= 0.4 -> 18
                        highest <= 0.7 -> 13
                        highest <= 1.0 -> 7
                        highest <= 1.2 -> 3
                        else -> 0
                    }, 18)
                }
                factor("Cloud cover", "$clouds%", "Average", beachCloudPoints(clouds), 10)
                factor("Wind", "${decimal(wind)} km/h", "Highest", windPoints(wind, 8), 8)
                factor("Wind gusts", "${decimal(gusts)} km/h", "Highest", gustPoints(gusts, 7), 7)
                factor("Rain chance", "$rainChance%", "Highest", rainChancePoints(rainChance, 10), 10)
                factor("Rainfall", "${decimal(rain)} mm", "Total", rainAmountPoints(rain, 10), 10)
                factor("Humidity", "$humidity%", "Average", humidityPoints(humidity, 5), 5)
                factor("Visibility", visibility(visibility), "Lowest", visibilityPoints(visibility, 5), 5)
                // Known limiting conditions still apply even when other hours lack sea values.
                if (waves.any { it > 1.2 }) limit(19, "Rough waves.")
                else if (waves.any { it > 0.8 }) limit(59, "Choppy waves.")
                if (water.any { it < 16 }) {
                    limit(39, "Cold water.")
                }
            }
            ActivityType.HIKING -> {
                factor("Feels like", celsius(feelsLike), "Average", hikingTemperaturePoints(feelsLike), 25)
                factor("Rain chance", "$rainChance%", "Highest", rainChancePoints(rainChance, 15), 15)
                factor("Rainfall", "${decimal(rain)} mm", "Total", rainAmountPoints(rain, 15), 15)
                factor("Wind", "${decimal(wind)} km/h", "Highest", windPoints(wind, 10), 10)
                factor("Wind gusts", "${decimal(gusts)} km/h", "Highest", gustPoints(gusts, 10), 10)
                factor("Cloud cover", "$clouds%", "Average", hikingCloudPoints(clouds), 15)
                factor("Humidity", "$humidity%", "Average", humidityPoints(humidity, 5), 5)
                factor("Visibility", visibility(visibility), "Lowest", visibilityPoints(visibility, 5), 5)
            }
        }
        factors += FactorResult("Conditions", weatherLabel(weatherCode), "Most significant",
            when (weatherSeverity(weatherCode)) {
                0 -> FactorOutcome.POSITIVE
                1 -> FactorOutcome.MIXED
                else -> FactorOutcome.NEGATIVE
            }, points = 0)
        factors += FactorResult("UV index", decimal(uv), "Highest", when {
            uv < 6 -> FactorOutcome.POSITIVE
            uv < 8 -> FactorOutcome.MIXED
            else -> FactorOutcome.NEGATIVE
        }, points = 0)

        if (wind > 35) limit(19, "Very strong wind.")
        if (gusts > 60) limit(19, "Very strong gusts.")
        else if (gusts > 50) limit(39, "Strong gusts.")
        else if (gusts > 40) limit(59, "Moderate gusts.")
        if (rain >= 3) limit(19, "Heavy rain.")
        else if (rain >= 1 || rainChance > 70) limit(39, "Rain.")
        else if (rain >= 0.3 || rainChance > 50) limit(59, "Possible rain.")
        if (hours.any { requireNotNull(it.airTemperatureC) < 0 } || feelsLike < 0) {
            limit(39, "Freezing temperatures.")
        } else if (feelsLike < 5) {
            limit(59, "Cold.")
        }
        if (hours.any { requireNotNull(it.airTemperatureC) >= 35 } || feelsLike >= 35) {
            limit(39, "Extreme heat.")
        } else if (feelsLike >= 32) {
            limit(59, "Hot.")
        }
        if (clouds > 90) limit(89, "Overcast.")
        if (visibility < 1_000) limit(39, "Very low visibility.")
        else if (visibility < 3_000) limit(59, "Low visibility.")
        if (uv >= 11) limit(59, "Extreme UV · Use protection.")
        else if (uv >= 8) limit(89, "Very high UV · Use protection.")
        weatherLimit(weatherCode)?.let { (maximum, warning) -> limit(maximum, warning) }
        val availablePoints = factors.sumOf { it.maximumPoints }
        val normalized = (factors.sumOf { it.points } * 100.0 / availablePoints).roundToInt()
        val coverage = if (activity == ActivityType.HIKING) MarineCoverage.NONE else MarineCoverage.combine(hours.map {
            val water = it.seaTemperatureC?.isFinite() == true
            val waves = it.waveHeightM?.let { v -> v.isFinite() && v >= 0 } == true
            when {
                water && waves -> MarineCoverage.FULL
                water -> MarineCoverage.WATER
                waves -> MarineCoverage.WAVES
                else -> MarineCoverage.NONE
            }
        })
        return ConditionsScore(minOf(normalized, maximumScore), factors, warnings, maximumScore, coverage)
    }

    private fun complete(h: HourlyConditions): Boolean {
        fun Double?.finite() = this != null && isFinite()
        fun Double?.nonNegative() = this != null && isFinite() && this >= 0
        val weather = h.airTemperatureC.finite() && h.windSpeedKmh.nonNegative() &&
            h.precipitationMm.nonNegative() && h.precipitationProbabilityPercent?.let { it in 0..100 } == true
        return weather && h.apparentTemperatureC.finite() && h.windGustsKmh.nonNegative() &&
            h.relativeHumidityPercent?.let { it in 0..100 } == true &&
            h.cloudCoverPercent?.let { it in 0..100 } == true && h.visibilityM.nonNegative() &&
            h.weatherCode?.let { it in 0..99 } == true && h.uvIndex.nonNegative()
    }

    private fun windPoints(wind: Double, maximum: Int): Int = when {
        wind <= 10 -> maximum
        wind <= 15 -> (maximum * 0.8).roundToInt()
        wind <= 22 -> (maximum * 0.5).roundToInt()
        wind <= 30 -> (maximum * 0.2).roundToInt()
        wind <= 35 -> (maximum * 0.1).roundToInt()
        else -> 0
    }

    private fun gustPoints(gusts: Double, maximum: Int): Int = when {
        gusts <= 20 -> maximum
        gusts <= 30 -> (maximum * 0.8).roundToInt()
        gusts <= 40 -> (maximum * 0.5).roundToInt()
        gusts <= 50 -> (maximum * 0.2).roundToInt()
        gusts <= 60 -> (maximum * 0.1).roundToInt()
        else -> 0
    }

    private fun rainChancePoints(chance: Int, maximum: Int) = scaled(maximum, when {
        chance <= 5 -> 1.0
        chance <= 15 -> 0.8
        chance <= 30 -> 0.55
        chance <= 50 -> 0.25
        else -> 0.0
    })

    private fun rainAmountPoints(rain: Double, maximum: Int) = scaled(maximum, when {
        rain == 0.0 -> 1.0
        rain <= 0.1 -> 0.9
        rain <= 0.3 -> 0.65
        rain < 1 -> 0.3
        rain < 3 -> 0.1
        else -> 0.0
    })

    private fun hikingTemperaturePoints(value: Double) = when {
        value in 16.0..22.0 -> 25
        (value >= 13 && value < 16) || (value > 22 && value <= 25) -> 22
        (value >= 10 && value < 13) || (value > 25 && value <= 28) -> 17
        (value >= 5 && value < 10) || (value > 28 && value <= 31) -> 10
        (value >= 0 && value < 5) || (value > 31 && value < 35) -> 4
        else -> 0
    }

    private fun beachTemperaturePoints(value: Double) = when {
        value in 22.0..27.0 -> 15
        (value >= 19 && value < 22) || (value > 27 && value <= 30) -> 12
        (value >= 16 && value < 19) || (value > 30 && value <= 33) -> 7
        (value >= 12 && value < 16) || (value > 33 && value < 35) -> 3
        else -> 0
    }

    private fun hikingCloudPoints(clouds: Int) = when {
        clouds <= 20 -> 15
        clouds <= 45 -> 12
        clouds <= 70 -> 8
        clouds <= 90 -> 4
        else -> 0
    }

    private fun beachCloudPoints(clouds: Int) = when {
        clouds <= 20 -> 10
        clouds <= 45 -> 7
        clouds <= 70 -> 4
        clouds <= 90 -> 2
        else -> 0
    }

    private fun humidityPoints(humidity: Int, maximum: Int) = scaled(maximum, when {
        humidity in 35..70 -> 1.0
        humidity in 25..80 -> 0.8
        humidity in 20..90 -> 0.4
        else -> 0.2
    })

    private fun visibilityPoints(visibility: Double, maximum: Int) = scaled(maximum, when {
        visibility >= 10_000 -> 1.0
        visibility >= 5_000 -> 0.8
        visibility >= 3_000 -> 0.5
        visibility >= 1_000 -> 0.2
        else -> 0.0
    })

    private fun weatherSeverity(code: Int) = when (code) {
        0, 1, 2 -> 0
        3 -> 1
        51, 53, 55, 61, 71, 77, 80 -> 2
        45, 48, 63, 73, 81, 85 -> 3
        56, 57, 65, 66, 67, 75, 82, 86, 95, 96, 99 -> 4
        else -> 1
    }

    private fun weatherLimit(code: Int): Pair<Int, String>? = when (code) {
        3 -> 89 to "Overcast."
        95, 96, 99 -> 19 to "Thunderstorm."
        56, 57, 66, 67 -> 19 to "Freezing rain."
        65, 75, 82, 86 -> 19 to "Heavy rain or snow."
        45, 48 -> 39 to "Fog."
        63, 73, 81, 85 -> 39 to "Moderate rain or snow."
        51, 53, 55, 61, 71, 77, 80 -> 59 to "Light rain or snow."
        else -> null
    }

    private fun weatherLabel(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Mixed conditions"
    }

    private fun scaled(maximum: Int, ratio: Double) = (maximum * ratio).roundToInt()

    private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun celsius(value: Double) = "${decimal(value)}°C"
    private fun visibility(value: Double) = if (value >= 1_000) "${decimal(value / 1_000)} km" else "${value.roundToInt()} m"
}
