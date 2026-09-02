package com.mbk.outing.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class ActivityOutlook(
    val activity: ActivityType,
    val day: DayRating?,
    val bestWindow: BestWindow?,
    val dayUnavailableReason: String? = null,
    val windowUnavailableReason: String? = null,
    val hourly: List<HourlyAssessment> = emptyList(),
)

object DayPlanner {
    const val WINDOW_HOURS = 3

    fun forDate(
        hours: List<HourlyConditions>,
        date: LocalDate,
        now: LocalDateTime,
        activity: ActivityType,
    ): ActivityOutlook {
        val dayHours = hours.filter { it.time.toLocalDate() == date }
        val eligible = dayHours.filter { !it.time.isBefore(now) && it.isDaylight }
            .distinctBy { it.time }.sortedBy { it.time }
        val sunrise = dayHours.firstNotNullOfOrNull { it.sunrise }
        val sunset = dayHours.firstNotNullOfOrNull { it.sunset }
        val start = if (sunrise != null) ceilHour(maxOf(sunrise, now)) else eligible.firstOrNull()?.time
        val end = sunset?.truncatedTo(ChronoUnit.HOURS) ?: eligible.lastOrNull()?.time?.plusHours(1)
        val expectedHours = if (start == null || end == null) 0 else
            Duration.between(start, end).toHours().toInt().coerceIn(0, 24)
        val byTime = eligible.associateBy { it.time }
        // Keep missing slots visible rather than presenting an incomplete day as complete.
        val hourly = (0 until expectedHours).map { index ->
            val time = requireNotNull(start).plusHours(index.toLong())
            HourlyAssessment(time, byTime[time]?.let { ActivityScorer.score(activity, listOf(it)) })
        }
        val evaluations = hourly.mapNotNull { it.evaluation }
        val day = if (expectedHours > 0 && evaluations.size == expectedHours) {
            val mean = evaluations.map { it.score }.average().roundToInt()
            val factors = evaluations.first().factors.filter { it.maximumPoints > 0 }.map { factor ->
                DayFactor(factor.label, evaluations.map { hour ->
                    hour.factors.first { it.label == factor.label }.points
                }.average(), factor.maximumPoints)
            }
            val limits = evaluations.map { it.factors.sumOf { factor -> factor.points } - it.score }.average()
            DayRating(ratingFor(mean), mean, evaluations.size, evaluations.count { it.score >= 40 },
                evaluations.flatMap { it.warnings }.distinct(), factors, limits)
        } else null

        val best = hourly.windowed(WINDOW_HOURS).mapNotNull { window ->
            if (window.any { it.evaluation == null }) return@mapNotNull null
            val conditions = window.map { byTime.getValue(it.time) }
            val summary = ActivityScorer.score(activity, conditions) ?: return@mapNotNull null
            val hourlyMean = window.map { requireNotNull(it.evaluation).score }.average().roundToInt()
            val score = minOf(hourlyMean, summary.maximumScore)
            BestWindow(window.first().time.toLocalTime(), window.last().time.plusHours(1).toLocalTime(),
                ratingFor(score), score, summary.factors, summary.warnings)
        }.maxByOrNull { it.score }

        return ActivityOutlook(
            activity, day, best,
            dayUnavailableReason = when {
                day != null -> null
                dayHours.isEmpty() -> "No forecast for this date."
                expectedHours == 0 -> "No hours remaining."
                else -> "Incomplete forecast · ${evaluations.size}/$expectedHours hours rated"
            },
            windowUnavailableReason = if (best != null) null else "No complete three-hour window",
            hourly = hourly,
        )
    }

    private fun ceilHour(time: LocalDateTime): LocalDateTime {
        val rounded = time.truncatedTo(ChronoUnit.HOURS)
        return if (rounded.isBefore(time)) rounded.plusHours(1) else rounded
    }
}
