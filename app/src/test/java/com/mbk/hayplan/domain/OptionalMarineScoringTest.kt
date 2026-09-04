package com.mbk.hayplan.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

class OptionalMarineScoringTest {
    private val date = LocalDate.of(2026, 9, 3)
    private fun hour(time: Int = 10) = HourlyConditions(date.atTime(time, 0), true,
        24.0, 5, 15, 10.0, null, null, 0.0)
    private fun score(data: HourlyConditions) = ActivityScorer.score(ActivityType.BEACH, listOf(data))!!

    @Test fun `optional marine factors change coverage and available points without freezing preferences`() {
        val weather = score(hour())
        val water = score(hour().copy(seaTemperatureC = 18.0))
        val waves = score(hour().copy(waveHeightM = 0.7))
        val full = score(hour().copy(seaTemperatureC = 21.0, waveHeightM = 0.3))
        assertEquals(listOf(MarineCoverage.NONE, MarineCoverage.WATER, MarineCoverage.WAVES, MarineCoverage.FULL),
            listOf(weather, water, waves, full).map { it.marineCoverage })
        assertTrue(water.availablePoints > weather.availablePoints)
        assertTrue(waves.availablePoints > weather.availablePoints)
        assertTrue(full.availablePoints > water.availablePoints && full.availablePoints > waves.availablePoints)
        listOf(weather, water, waves, full).forEach { assertTrue(it.score in 0..100) }
    }

    @Test fun `zero is a real measurement while absent values receive no invented points`() {
        val calm = score(hour().copy(waveHeightM = 0.0))
        val absent = score(hour())
        assertTrue(calm.availablePoints > absent.availablePoints)
        assertTrue(calm.factors.first { it.label == "Waves" }.points > 0)
        val cold = score(hour().copy(seaTemperatureC = 0.0))
        assertTrue(cold.availablePoints > absent.availablePoints)
        assertEquals(0, cold.factors.first { it.label == "Water" }.points)
        assertTrue(cold.warnings.any { it.contains("Cold water") })
    }

    @Test fun `invalid marine values are omitted not treated as calm warm water`() {
        val result = score(hour().copy(seaTemperatureC = Double.NaN, waveHeightM = -1.0))
        assertEquals(MarineCoverage.NONE, result.marineCoverage)
        assertFalse(result.factors.any { it.label == "Water" || it.label == "Waves" })
    }

    @Test fun `limits apply after normalization`() {
        val limited = listOf(score(hour().copy(windSpeedKmh = 40.0)),
            score(hour().copy(precipitationMm = 1.1)), score(hour().copy(waveHeightM = 1.5)))
        limited.forEach {
            assertTrue(it.maximumScore < 100)
            assertTrue(it.score <= it.maximumScore)
            assertTrue(it.warnings.isNotEmpty())
        }
    }

    @Test fun `missing core weather still makes the hour unavailable`() {
        assertNull(ActivityScorer.score(ActivityType.BEACH, listOf(hour().copy(cloudCoverPercent = null))))
        assertNull(ActivityScorer.score(ActivityType.BEACH, listOf(hour().copy(precipitationMm = null))))
    }

    @Test fun `mixed sea coverage cannot hide known rough waves in the best window`() {
        val hours = listOf(hour(10).copy(waveHeightM = 1.5), hour(11), hour(12))
        val outlook = DayPlanner.forDate(hours, date, date.atStartOfDay(), ActivityType.BEACH)
        assertEquals(MarineCoverage.MIXED, outlook.marineCoverage)
        assertEquals(outlook.hourly.map { it.evaluation!!.score }.average().roundToInt(), outlook.day!!.score)
        val weatherOnly = DayPlanner.forDate(hours.map { it.copy(waveHeightM = null) },
            date, date.atStartOfDay(), ActivityType.BEACH)
        assertTrue(outlook.bestWindow!!.score < weatherOnly.bestWindow!!.score)
        assertTrue(outlook.bestWindow.warnings.any { it.contains("Rough waves") })
        assertEquals(MarineCoverage.MIXED, outlook.bestWindow.marineCoverage)
    }

}
