package com.mbk.hayplan.domain

import com.mbk.hayplan.data.LocationCatalog
import com.mbk.hayplan.notification.DailyPlanScheduler
import com.mbk.hayplan.notification.DailyNotificationRetryPolicy
import com.mbk.hayplan.ui.AppLanguage
import com.mbk.hayplan.ui.DailyNotificationFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class DailyRecommendationTest {
    @Test fun `pleasant complete sea forecast recommends Gijon Beach and Oviedo walking`() {
        val plan = DailyRecommendationPlanner.create(
            outlook(ActivityType.BEACH, window(82, MarineCoverage.FULL)),
            outlook(ActivityType.HIKING, window(95)),
            outlook(ActivityType.HIKING, window(72)),
        )
        assertEquals(RecommendedActivity.BEACH, plan.gijon.activity)
        assertEquals(RecommendedActivity.WALK, plan.oviedo.activity)
    }

    @Test fun `missing marine evidence or important Beach warning chooses Gijon walking`() {
        listOf(
            window(90, MarineCoverage.NONE),
            window(82, MarineCoverage.FULL, listOf(ForecastWarning.COLD_WATER)),
            window(82, MarineCoverage.FULL, listOf(ForecastWarning.ROUGH_WAVES)),
        ).forEach { beach ->
            val plan = DailyRecommendationPlanner.create(
                outlook(ActivityType.BEACH, beach), outlook(ActivityType.HIKING, window(70)),
                outlook(ActivityType.HIKING, window(70)))
            assertEquals(RecommendedActivity.WALK, plan.gijon.activity)
        }
    }

    @Test fun `poor conditions are honest while absent windows remain unavailable`() {
        val plan = DailyRecommendationPlanner.create(
            outlook(ActivityType.BEACH, window(30, MarineCoverage.FULL)),
            outlook(ActivityType.HIKING, window(39)),
            outlook(ActivityType.HIKING, null),
        )
        assertEquals(RecommendedActivity.NONE, plan.gijon.activity)
        assertEquals(RecommendedActivity.UNAVAILABLE, plan.oviedo.activity)
    }

    @Test fun `notification text stays compact localized and identifies saved data`() {
        val plan = DailyRecommendations(
            PlaceRecommendation(RecommendedActivity.BEACH, window(82, MarineCoverage.FULL,
                start = LocalTime.of(12, 0))),
            PlaceRecommendation(RecommendedActivity.WALK, window(55, start = LocalTime.of(11, 30))),
        )
        val english = DailyNotificationFormatter.format(plan, AppLanguage.ENGLISH, savedForecast = false)
        assertEquals("Gijón: beach 12–15 · Very Good", english.title)
        assertEquals("Oviedo: walk 11:30–14:30 · Good", english.body)
        assertTrue(english.title.length <= 45)
        assertTrue(english.body.length <= 45)
        assertNull(english.subText)
        val spanish = DailyNotificationFormatter.format(plan, AppLanguage.SPANISH, savedForecast = true)
        assertEquals("Gijón: playa 12–15 · Muy buena", spanish.title)
        assertEquals("Oviedo: paseo 11:30–14:30 · Buena", spanish.body)
        assertEquals("Previsión guardada", spanish.subText)
    }

    @Test fun `next notification stays at Asturias wall time across tomorrow and DST`() {
        val before = ZonedDateTime.of(2026, 3, 28, 8, 30, 0, 0, LocationCatalog.zone)
        assertEquals(ZonedDateTime.of(2026, 3, 28, 9, 0, 0, 0, LocationCatalog.zone),
            DailyPlanScheduler.nextRun(before, LocalTime.of(9, 0)))
        val after = ZonedDateTime.of(2026, 3, 28, 10, 0, 0, 0, LocationCatalog.zone)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 9, 0, 0, 0, LocationCatalog.zone),
            DailyPlanScheduler.nextRun(after, LocalTime.of(9, 0)))
    }

    @Test fun `notification retries missing essential forecasts before reporting unavailable`() {
        assertTrue(DailyNotificationRetryPolicy.shouldRetry(0, essentialForecastAvailable = false))
        assertTrue(DailyNotificationRetryPolicy.shouldRetry(1, essentialForecastAvailable = false))
        assertTrue(DailyNotificationRetryPolicy.shouldRetry(2, essentialForecastAvailable = false))
        assertEquals(false,
            DailyNotificationRetryPolicy.shouldRetry(3, essentialForecastAvailable = false))
        assertEquals(false,
            DailyNotificationRetryPolicy.shouldRetry(0, essentialForecastAvailable = true))
    }

    private fun outlook(activity: ActivityType, window: BestWindow?) =
        ActivityOutlook(activity, day = null, bestWindow = window)

    private fun window(
        score: Int,
        coverage: MarineCoverage = MarineCoverage.NONE,
        warnings: List<ForecastWarning> = emptyList(),
        start: LocalTime = LocalTime.of(10, 0),
    ) = BestWindow(start, start.plusHours(3), ratingFor(score), score, emptyList(), warnings, coverage)
}
