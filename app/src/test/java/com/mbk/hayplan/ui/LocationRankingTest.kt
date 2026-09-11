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
        assertEquals(listOf(poor, incomplete), rankLocations(forecasts, outlooks))
        assertEquals(listOf(incomplete, poor), rankLocations(forecasts, outlooks, RankingMode.BEST_WINDOW))
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

    @Test fun `Beach best-window ranking matches the score shown to the user`() {
        val known = forecast("known-window", 20.0)
        val unknown = forecast("unknown-window", 20.0)
        fun beachWindow(score: Int) = ActivityOutlook(
            ActivityType.BEACH, null,
            BestWindow(LocalTime.of(10, 0), LocalTime.of(13, 0), ratingFor(score), score,
                emptyList()),
        )
        val outlooks = mapOf(
            "known-window" to beachWindow(score = 82),
            "unknown-window" to beachWindow(score = 70),
        )
        assertEquals(listOf(known, unknown),
            rankLocations(listOf(unknown, known), outlooks, RankingMode.BEST_WINDOW))
    }

    @Test fun `ranking preference codes are stable and unknown values preserve whole-day default`() {
        assertEquals(RankingMode.WHOLE_DAY, RankingMode.fromCode(null))
        assertEquals(RankingMode.WHOLE_DAY, RankingMode.fromCode("future-value"))
        RankingMode.entries.forEach { assertEquals(it, RankingMode.fromCode(it.code)) }
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

    @Test fun `main towns use fixed population order and never repeat in ranked others`() {
        val ranked = listOf(
            forecast("aviles", 20.0), forecast("other-1", 20.0), forecast("gijon", 20.0),
            forecast("other-2", 20.0), forecast("oviedo", 20.0),
        )
        val sections = locationSections(ranked)
        assertEquals(listOf("gijon", "oviedo", "aviles"), sections.main.map { it.location.id })
        assertEquals(listOf("other-1", "other-2"), sections.allOthers.map { it.location.id })
        assertTrue(sections.main.none { it in sections.allOthers })
    }

    @Test fun `other locations show ten before expanding`() {
        val ranked = (1..12).map { forecast("other-$it", 20.0) }
        val sections = locationSections(ranked)
        assertEquals(10, sections.topOthers.size)
        assertEquals(12, sections.allOthers.size)
        assertEquals((1..10).map { "other-$it" }, sections.topOthers.map { it.location.id })
    }
}
