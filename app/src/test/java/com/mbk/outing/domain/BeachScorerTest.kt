package com.mbk.outing.domain

import org.junit.Assert.assertEquals
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
        )

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

        val recommendation = BeachScorer.sunbathing(hours)

        assertEquals(LocalTime.of(11, 0), recommendation.bestStart)
        assertEquals(LocalTime.of(14, 0), recommendation.bestEnd)
        assertEquals(Rating.EXCELLENT, recommendation.rating)
    }

    @Test
    fun `earlier window wins when scores are tied`() {
        val hours = (10..14).map { conditions(it) }

        val recommendation = BeachScorer.swimming(hours)

        assertEquals(LocalTime.of(10, 0), recommendation.bestStart)
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
    )

    companion object {
        private val DATE: LocalDate = LocalDate.of(2026, 9, 2)
    }
}
