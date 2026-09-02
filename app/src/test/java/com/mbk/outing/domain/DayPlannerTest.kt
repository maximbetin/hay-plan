package com.mbk.outing.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayPlannerTest {
    private val date = LocalDate.of(2026, 9, 2)
    private val morning = date.atStartOfDay()

    @Test fun `excellent window does not make an otherwise poor day excellent`() {
        val hours = (8..19).map { if (it in 11..13) hour(it) else hour(it).copy(
            airTemperatureC = 2.0, seaTemperatureC = 10.0, waveHeightM = 2.0,
            cloudCoverPercent = 100, windSpeedKmh = 50.0, precipitationProbabilityPercent = 100,
        ) }
        val outlook = DayPlanner.forDate(hours, date, morning, ActivityType.BEACH)
        assertEquals(Rating.FAIR, outlook.day!!.rating)
        assertEquals(Rating.EXCELLENT, outlook.bestWindow!!.rating)
        assertEquals(LocalTime.of(11, 0), outlook.bestWindow.start)
        assertEquals(12, outlook.day.assessedHours)
        assertEquals(3, outlook.day.goodHours)
    }

    @Test fun `missing marine hours leave day unavailable but preserve a known window`() {
        val hours = (8..19).map { hour(it).copy(waveHeightM = if (it >= 14) null else 0.3) }
        val beach = DayPlanner.forDate(hours, date, morning, ActivityType.BEACH)
        val hiking = DayPlanner.forDate(hours, date, morning, ActivityType.HIKING)
        assertNull(beach.day)
        assertNotNull(beach.bestWindow)
        assertNotNull(hiking.day)
    }

    @Test fun `today ignores elapsed hours and tomorrow remains independent`() {
        val today = (8..19).map(::hour)
        val tomorrow = today.map { it.copy(time = it.time.plusDays(1)) }
        val now = date.atTime(11, 30)
        val current = DayPlanner.forDate(today + tomorrow, date, now, ActivityType.HIKING)
        val future = DayPlanner.forDate(today + tomorrow, date.plusDays(1), now, ActivityType.HIKING)
        assertEquals(8, current.day!!.assessedHours)
        assertEquals(LocalTime.NOON, current.bestWindow!!.start)
        assertEquals(12, future.day!!.assessedHours)
        assertEquals(LocalTime.of(8, 0), future.bestWindow!!.start)
    }

    @Test fun `day assessment is still meaningful with only two hours remaining`() {
        val result = DayPlanner.forDate((8..19).map(::hour), date, date.atTime(17, 30), ActivityType.HIKING)
        assertEquals(2, result.day!!.assessedHours)
        assertNull(result.bestWindow)
    }

    @Test fun `no daylight yields no rating or window`() {
        val hours = (20..23).map { hour(it).copy(isDaylight = false) }
        val result = DayPlanner.forDate(hours, date, morning, ActivityType.BEACH)
        assertNull(result.day)
        assertNull(result.bestWindow)
    }

    @Test fun `a missing hourly timestamp invalidates the full day`() {
        val result = DayPlanner.forDate(listOf(hour(10), hour(12), hour(13)), date, morning, ActivityType.HIKING)
        assertNull(result.day)
        assertNull(result.bestWindow)
    }

    @Test fun `solar bounds reveal a truncated forecast even if returned hours are complete`() {
        val hours = (11..13).map { hour(it).copy(sunrise = date.atTime(8, 15), sunset = date.atTime(20, 35)) }
        val result = DayPlanner.forDate(hours, date, morning, ActivityType.HIKING)
        assertNull(result.day)
        assertNotNull(result.bestWindow)
    }

    @Test fun `ties choose earliest window`() {
        val result = DayPlanner.forDate((8..19).map(::hour), date, morning, ActivityType.BEACH)
        assertEquals(LocalTime.of(8, 0), result.bestWindow!!.start)
    }

    @Test fun `all sixteen dates remain separately selectable for calculation`() {
        val hours = (0..15).flatMap { day -> (8..19).map { hour(it).copy(time = date.plusDays(day.toLong()).atTime(it, 0)) } }
        (0..15).forEach { day ->
            assertNotNull(DayPlanner.forDate(hours, date.plusDays(day.toLong()), morning, ActivityType.HIKING).day)
        }
    }

    @Test fun `hiking ignores sea and cloud conditions`() {
        val data = hour(10).copy(waveHeightM = null, seaTemperatureC = null, cloudCoverPercent = null)
        assertEquals(100, ActivityScorer.score(ActivityType.HIKING, listOf(data))!!.score)
        assertNull(ActivityScorer.score(ActivityType.BEACH, listOf(data)))
    }

    @Test fun `rough seas cannot be offset by pleasant weather`() {
        val score = ActivityScorer.score(ActivityType.BEACH, listOf(hour(10).copy(waveHeightM = 1.3)))!!
        assertEquals(Rating.POOR, ratingFor(score.score))
        assertTrue(score.warnings.any { it.contains("Rough waves") })
    }

    @Test fun `wave boundary is inclusive and explicit`() {
        val score = ActivityScorer.score(ActivityType.BEACH, listOf(hour(10).copy(waveHeightM = 1.2)))!!
        assertEquals(Rating.GOOD, ratingFor(score.score))
    }

    @Test fun `one cold or hot hour cannot hide inside a comfortable window average`() {
        val data = (10..12).map(::hour).toMutableList()
        data[1] = data[1].copy(airTemperatureC = 36.0, seaTemperatureC = 12.0)
        val score = ActivityScorer.score(ActivityType.BEACH, data)!!
        assertTrue(score.score <= 39)
        assertTrue(score.warnings.size >= 2)
    }

    @Test fun `rainfall matters even with a low rain probability`() {
        val score = ActivityScorer.score(ActivityType.HIKING, listOf(hour(10).copy(precipitationMm = 3.0)))!!
        assertEquals(Rating.POOR, ratingFor(score.score))
    }

    @Test fun `non finite and missing weather are unavailable for both activities`() {
        ActivityType.entries.forEach { activity ->
            assertNull(ActivityScorer.score(activity, listOf(hour(10).copy(airTemperatureC = Double.NaN))))
            assertNull(ActivityScorer.score(activity, listOf(hour(10).copy(precipitationMm = null))))
        }
    }

    @Test fun `rating boundaries match documented scale`() {
        listOf(0 to Rating.POOR, 19 to Rating.POOR, 20 to Rating.FAIR, 39 to Rating.FAIR,
            40 to Rating.GOOD, 59 to Rating.GOOD, 60 to Rating.VERY_GOOD, 79 to Rating.VERY_GOOD,
            80 to Rating.EXCELLENT, 100 to Rating.EXCELLENT).forEach { (score, expected) ->
            assertEquals(expected, ratingFor(score))
        }
    }

    @Test fun `day and window use the same hourly basis without interval limits`() {
        val hours = listOf(
            hour(10).copy(airTemperatureC = 25.0, windSpeedKmh = 10.0),
            hour(11).copy(airTemperatureC = 25.0, windSpeedKmh = 20.0),
            hour(12).copy(airTemperatureC = 23.0, windSpeedKmh = 10.0),
        )
        val outlook = DayPlanner.forDate(hours, date, morning, ActivityType.HIKING)
        assertEquals(outlook.day!!.score, outlook.bestWindow!!.score)
        assertEquals(Rating.EXCELLENT, outlook.bestWindow.rating)
    }

    private fun hour(hour: Int) = HourlyConditions(date.atTime(hour, 0), true,
        airTemperatureC = 24.0, precipitationProbabilityPercent = 5, cloudCoverPercent = 15,
        windSpeedKmh = 10.0, seaTemperatureC = 21.0, waveHeightM = 0.3, precipitationMm = 0.0)
}
