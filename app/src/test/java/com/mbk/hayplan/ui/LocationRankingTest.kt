package com.mbk.hayplan.ui

import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LocationRankingTest {
    private val date = LocalDate.of(2026, 9, 4)
    private fun forecast(id: String, temperature: Double?) = LocationForecast(
        HayPlanLocation(id, id, "Asturias", Coordinates(43.5, -5.5)),
        ActivityForecastData((10..13).map {
            HourlyConditions(date.atTime(it, 0), true, temperature, 0, 0, 10.0, null, null, 0.0,
                apparentTemperatureC = temperature, relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = 1, windGustsKmh = 18.0, uvIndex = 4.0)
        }),
    )
    private fun outlooks(forecasts: List<LocationForecast>, activity: ActivityType) =
        forecasts.associate { it.location.id to DayPlanner.forDate(
            it.forActivity(activity).hours, date, date.atStartOfDay(), activity) }

    @Test fun `ranking follows selected activity without dropping inland or unavailable towns`() {
        val warm = forecast("warm", 27.0)
        val mild = forecast("mild", 20.0)
        val missing = forecast("missing", null)
        val forecasts = listOf(missing, mild, warm)
        assertEquals(listOf(warm, mild, missing), rankLocations(forecasts, outlooks(forecasts, ActivityType.BEACH)))
        assertEquals(listOf(mild, warm, missing), rankLocations(forecasts, outlooks(forecasts, ActivityType.HIKING)))
        assertSame(warm, rankLocations(forecasts, outlooks(forecasts, ActivityType.BEACH)).first())
    }

    @Test fun `ties have deterministic order regardless of input order`() {
        val forecasts = listOf(forecast("b", 20.0), forecast("a", 20.0))
        val outlooks = outlooks(forecasts, ActivityType.HIKING)
        assertEquals(listOf("a", "b"), rankLocations(forecasts, outlooks).map { it.location.id })
        assertEquals(rankLocations(forecasts, outlooks), rankLocations(forecasts.reversed(), outlooks))
    }

    @Test fun `capped score ties retain the safer label but rank by underlying conditions`() {
        val better = forecast("better", 20.0)
        val worse = forecast("worse", 20.0)
        fun capped(knownConditions: Int) = ActivityOutlook(ActivityType.BEACH,
            DayRating(Rating.POOR, 19, 4, 0, emptyList(),
                knownConditionsScore = knownConditions), null)
        val outlooks = mapOf("better" to capped(82), "worse" to capped(54))
        assertEquals(listOf(better, worse), rankLocations(listOf(worse, better), outlooks))
        assertTrue(outlooks.values.all { it.day!!.score == 19 && it.day.rating == Rating.POOR })
    }

    @Test fun `incomplete day with a window stays below even a poor complete day`() {
        val good = forecast("incomplete", 20.0)
        val incomplete = good.copy(weather = good.weather.copy(hours = good.weather.hours.mapIndexed { i, hour ->
            if (i == 3) hour.copy(airTemperatureC = null) else hour
        })).let { it.copy(beach = it.weather) }
        val poor = forecast("poor", -2.0)
        val forecasts = listOf(incomplete, poor)
        val outlooks = outlooks(forecasts, ActivityType.HIKING)
        assertNull(outlooks.getValue("incomplete").day)
        assertNotNull(outlooks.getValue("incomplete").bestWindow)
        // A good best window never lifts a location whose full day cannot be rated.
        assertEquals(listOf(poor, incomplete), rankLocations(forecasts, outlooks))
    }

    @Test fun `Beach ranking matches the conservative score shown to the user`() {
        val known = forecast("known", 20.0)
        val unknown = forecast("unknown", 20.0)
        fun beach(score: Int) = ActivityOutlook(ActivityType.BEACH,
            DayRating(ratingFor(score), score, 4, 4, emptyList()), null)
        val outlooks = mapOf(
            "known" to beach(score = 82),
            "unknown" to beach(score = 70),
        )
        assertEquals(listOf(known, unknown), rankLocations(listOf(unknown, known), outlooks))
    }

    private fun day(score: Int?) = ActivityOutlook(ActivityType.HIKING,
        score?.let { DayRating(ratingFor(it), it, 4, 4, emptyList()) }, null)

    @Test fun `a location's best day is the visibly highest and the sooner, exact day on a tie`() {
        val days = listOf(DatedOutlook(date, day(50)), DatedOutlook(date.plusDays(1), day(null)),
            DatedOutlook(date.plusDays(2), day(70)), DatedOutlook(date.plusDays(3), day(88)))
        // Day 3 shows only its band, so its hidden 88 never beats the 70 the user can see.
        assertEquals(date.plusDays(2), bestDay(days, date)!!.date)
        assertEquals(date, bestDay(listOf(DatedOutlook(date, day(65)), DatedOutlook(date.plusDays(1), day(65))), date)!!.date)
        assertEquals(date.plusDays(1),
            bestDay(listOf(DatedOutlook(date, day(65)), DatedOutlook(date.plusDays(1), day(80))), date)!!.date)
        assertNull(bestDay(listOf(DatedOutlook(date, day(null))), date))
    }

    @Test fun `week rows and the highlight agree on the best place and day`() {
        val steady = forecast("a-steady", 20.0)
        val peak = forecast("b-peak", 20.0)
        val none = forecast("c-none", 20.0)
        val week = mapOf(
            "a-steady" to listOf(DatedOutlook(date, day(62)), DatedOutlook(date.plusDays(1), day(62))),
            "b-peak" to listOf(DatedOutlook(date, day(10)), DatedOutlook(date.plusDays(1), day(85))),
            "c-none" to listOf(DatedOutlook(date, day(null))),
        )
        val ordered = rankLocationsForWeek(listOf(none, peak, steady), week, ActivityType.HIKING, date)
        assertEquals(listOf(peak, steady, none), ordered)
        val highlight = weekHighlight(ordered, week, ActivityType.HIKING, date)!!
        assertSame(peak, highlight.location)
        assertEquals(date.plusDays(1), highlight.date)
        assertNull(weekHighlight(listOf(none), week, ActivityType.HIKING, date))
        // These test places have no coast, so none of them may headline a Beach week.
        assertNull(weekHighlight(ordered, week, ActivityType.BEACH, date))
    }

    @Test fun `banded ranking ignores hidden point differences within a rating`() {
        val low = forecast("a-low", 20.0)
        val high = forecast("z-high", 20.0)
        val outlooks = mapOf(
            "a-low" to ActivityOutlook(ActivityType.HIKING,
                DayRating(Rating.VERY_GOOD, 61, 4, 4, emptyList(), knownConditionsScore = 61), null),
            "z-high" to ActivityOutlook(ActivityType.HIKING,
                DayRating(Rating.VERY_GOOD, 89, 4, 4, emptyList(), knownConditionsScore = 89), null),
        )
        assertEquals(listOf(high, low), rankLocations(listOf(low, high), outlooks))
        assertEquals(listOf(low, high), rankLocations(listOf(high, low), outlooks, coarseScores = true))
    }

    @Test fun `coarse Beach ranking uses visible coverage when scores share a band`() {
        val known = forecast("z-known", 20.0)
        val unknown = forecast("a-unknown", 20.0)
        val outlooks = mapOf(
            "z-known" to ActivityOutlook(ActivityType.BEACH,
                DayRating(Rating.VERY_GOOD, 82, 4, 4, emptyList(), MarineCoverage.FULL), null),
            "a-unknown" to ActivityOutlook(ActivityType.BEACH,
                DayRating(Rating.VERY_GOOD, 70, 4, 4, emptyList(), MarineCoverage.NONE), null),
        )
        assertEquals(listOf(known, unknown),
            rankLocations(listOf(unknown, known), outlooks, coarseScores = true))
    }

    @Test fun `named towns receive no special ranking and inland Beach estimates follow beaches`() {
        val oviedo = forecast("oviedo", 20.0)
        val coast = forecast("coast", 20.0).let { it.copy(location = it.location.copy(
            coast = CoastalReference("Beach", Coordinates(43.5, -5.5)))) }
        val outlooks = mapOf(
            "oviedo" to ActivityOutlook(ActivityType.BEACH,
                DayRating(Rating.VERY_GOOD, 70, 4, 4, emptyList()), null),
            "coast" to ActivityOutlook(ActivityType.BEACH,
                DayRating(Rating.GOOD, 50, 4, 4, emptyList()), null),
        )
        assertEquals(listOf(coast, oviedo),
            rankLocations(listOf(oviedo, coast), outlooks, ActivityType.BEACH))
        assertEquals(listOf(oviedo, coast),
            rankLocations(listOf(coast, oviedo), outlooks, ActivityType.HIKING))
    }

    @Test fun `week order does not promote a named town`() {
        val gijon = forecast("gijon", 20.0)
        val better = forecast("better", 20.0)
        val week = mapOf(
            "gijon" to listOf(DatedOutlook(date, day(50))),
            "better" to listOf(DatedOutlook(date, day(70))),
        )
        val ranked = rankLocationsForWeek(listOf(gijon, better), week, ActivityType.HIKING, date)
        assertEquals(listOf(better, gijon), ranked)
        assertSame(better, weekHighlight(ranked, week, ActivityType.HIKING, date)?.location)
    }
}
