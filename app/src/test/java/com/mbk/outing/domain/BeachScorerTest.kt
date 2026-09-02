package com.mbk.outing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BeachScorerTest {
    @Test
    fun `excellent night conditions are ignored`() {
        val recommendation = BeachScorer.swimming(
            listOf(
                conditions(10, daylight = true, waves = 0.7, water = 18.0),
                conditions(11, daylight = true, waves = 0.7, water = 18.0),
                conditions(12, daylight = true, waves = 0.7, water = 18.0),
                conditions(22, daylight = false, waves = 0.1, water = 24.0),
                conditions(23, daylight = false, waves = 0.1, water = 24.0),
                conditions(0, daylight = false, waves = 0.1, water = 24.0, nextDay = true),
            ),
        )!!

        assertEquals(LocalTime.of(10, 0), recommendation.bestStart)
        assertEquals(LocalTime.of(13, 0), recommendation.bestEnd)
    }

    @Test
    fun `best contiguous daylight window is selected`() {
        val hours = (9..15).map { hour ->
            if (hour in 11..13) {
                conditions(hour, air = 25.0, rain = 5, clouds = 10, wind = 8.0)
            } else {
                conditions(hour, air = 17.0, rain = 60, clouds = 90, wind = 30.0)
            }
        }

        val recommendation = BeachScorer.sunbathing(hours)!!

        assertEquals(LocalTime.of(11, 0), recommendation.bestStart)
        assertEquals(LocalTime.of(14, 0), recommendation.bestEnd)
        assertEquals(Rating.EXCELLENT, recommendation.rating)
    }

    @Test
    fun `earlier window wins when scores are tied`() {
        val hours = (10..14).map { conditions(it) }

        val recommendation = BeachScorer.swimming(hours)!!

        assertEquals(LocalTime.of(10, 0), recommendation.bestStart)
    }

    @Test
    fun `missing marine data does not suppress sunbathing`() {
        val hours = (10..12).map { conditions(it).copy(waveHeightM = null, seaTemperatureC = null) }
        assertNull(BeachScorer.swimming(hours))
        assertNotNull(BeachScorer.sunbathing(hours))
    }

    @Test
    fun `one missing critical hour cannot be averaged away`() {
        val hours = (10..12).map { conditions(it).copy(waveHeightM = if (it == 11) null else 0.3) }
        assertNull(BeachScorer.swimming(hours))
    }

    @Test
    fun `rough waves cannot be offset by excellent weather`() {
        val result = BeachScorer.swimming((10..12).map { conditions(it, waves = 1.3) })!!
        assertEquals(Rating.POOR, result.rating)
        assertTrue(result.warnings.any { it.contains("Rough waves") })
    }

    @Test
    fun `wave limit boundary is explicit`() {
        val result = BeachScorer.swimming((10..12).map { conditions(it, waves = 1.2) })!!
        assertEquals(Rating.GOOD, result.rating)
    }

    @Test
    fun `gaps empty input and too few hours return unavailable`() {
        assertNull(BeachScorer.swimming(emptyList()))
        assertNull(BeachScorer.sunbathing(listOf(conditions(10), conditions(11))))
        assertNull(BeachScorer.swimming(listOf(conditions(10), conditions(12), conditions(13))))
    }

    @Test
    fun `rainfall amount limits rating even with low probability`() {
        val result = BeachScorer.sunbathing((10..12).map { conditions(it).copy(precipitationMm = 1.0) })!!
        assertEquals(Rating.POOR, result.rating)
    }

    @Test
    fun `today excludes elapsed hours without changing tomorrow`() {
        val today = (9..16).map { conditions(it) }
        val tomorrow = today.map { it.copy(time = it.time.plusDays(1)) }
        val now = DATE.atTime(11, 30)
        val current = DayPlanner.forDate(today + tomorrow, DATE, now)
        val future = DayPlanner.forDate(today + tomorrow, DATE.plusDays(1), now)
        assertEquals(LocalTime.of(12, 0), current.first().recommendation!!.bestStart)
        assertEquals(LocalTime.of(9, 0), future.first().recommendation!!.bestStart)
    }

    @Test
    fun `late today is unavailable and does not fall back to tomorrow`() {
        val hours = (9..16).map { conditions(it) }
        val outlooks = DayPlanner.forDate(hours, DATE, DATE.atTime(15, 30))
        assertTrue(outlooks.all { it.recommendation == null })
        assertTrue(outlooks.first().unavailableReason!!.contains("remaining"))
    }

    @Test
    fun `all sixteen dates can be rated independently`() {
        val hours = (0..15).flatMap { day ->
            (10..12).map { conditions(it).copy(time = DATE.plusDays(day.toLong()).atTime(it, 0)) }
        }
        (0..15).forEach { day ->
            val outlook = DayPlanner.forDate(hours, DATE.plusDays(day.toLong()), DATE.atStartOfDay())
            assertTrue(outlook.all { it.recommendation != null })
        }
    }

    @Test
    fun `each activity requires only its own factors`() {
        val hours = (10..12).map { conditions(it).copy(cloudCoverPercent = null) }
        assertNotNull(BeachScorer.swimming(hours))
        assertNull(BeachScorer.sunbathing(hours))
    }

    @Test
    fun `non finite input is unavailable not a valid rating`() {
        val hours = (10..12).map { conditions(it).copy(airTemperatureC = Double.NaN) }
        assertNull(BeachScorer.sunbathing(hours))
        assertNull(BeachScorer.swimming(hours))
    }

    private fun conditions(
        hour: Int,
        daylight: Boolean = true,
        air: Double = 24.0,
        rain: Int = 5,
        clouds: Int = 15,
        wind: Double = 10.0,
        water: Double = 21.0,
        waves: Double = 0.3,
        nextDay: Boolean = false,
    ) = HourlyConditions(
        time = LocalDateTime.of(
            if (nextDay) DATE.plusDays(1) else DATE,
            LocalTime.of(hour, 0),
        ),
        isDaylight = daylight,
        airTemperatureC = air,
        precipitationProbabilityPercent = rain,
        cloudCoverPercent = clouds,
        windSpeedKmh = wind,
        seaTemperatureC = water,
        waveHeightM = waves,
        precipitationMm = 0.0,
    )

    companion object {
        private val DATE: LocalDate = LocalDate.of(2026, 9, 2)
    }
}
