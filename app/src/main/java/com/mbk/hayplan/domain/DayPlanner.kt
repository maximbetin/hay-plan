package com.mbk.hayplan.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class ActivityOutlook(
    val activity: ActivityType,
    val day: DayRating?,
    val bestWindow: BestWindow?,
    val dayUnavailableReason: DayUnavailableReason? = null,
    val windowUnavailableReason: WindowUnavailableReason? = null,
    val hourly: List<HourlyAssessment> = emptyList(),
    /** Known warning evidence remains available even when an incomplete day has no rating. */
    val warningPeriods: List<ForecastWarningPeriod> = emptyList(),
) {
    val marineCoverage: MarineCoverage get() = MarineCoverage.combine(hourly.mapNotNull { it.evaluation?.marineCoverage })
}

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
        val missingDaylightBounds = dayHours.isNotEmpty() && (sunrise == null || sunset == null)
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
        val dayWarningPeriods = warningPeriods(hourly)
        val day = if (expectedHours > 0 && evaluations.size == expectedHours) {
            val mean = evaluations.map { it.score }.average().roundToInt()
            DayRating(ratingFor(mean), mean, evaluations.size, evaluations.count { it.score >= 40 },
                evaluations.flatMap { it.warnings }.distinct(), MarineCoverage.combine(evaluations.map { it.marineCoverage }),
                knownConditionsScore = evaluations.map { it.knownConditionsScore }.average().roundToInt(),
                warningPeriods = dayWarningPeriods)
        } else null

        val best = hourly.windowed(WINDOW_HOURS).mapNotNull { window ->
            if (window.any { it.evaluation == null }) return@mapNotNull null
            val conditions = window.map { byTime.getValue(it.time) }
            val summary = ActivityScorer.score(activity, conditions) ?: return@mapNotNull null
            val windowScores = window.map { requireNotNull(it.evaluation) }
            val hourlyMean = windowScores.map { it.score }.average().roundToInt()
            val score = minOf(hourlyMean, summary.maximumScore)
            val periods = warningPeriods(window).toMutableList()
            summary.warnings.filter { warning -> periods.none { it.warning == warning } }.forEach { warning ->
                periods += ForecastWarningPeriod(warning, window.first().time, window.last().time.plusHours(1))
            }
            BestWindow(window.first().time.toLocalTime(), window.last().time.plusHours(1).toLocalTime(),
                ratingFor(score), score, summary.factors, summary.warnings,
                MarineCoverage.combine(window.map { requireNotNull(it.evaluation).marineCoverage }),
                periods.sortedBy { it.start },
                knownConditionsScore = windowScores.map { it.knownConditionsScore }.average().roundToInt())
        }.maxByOrNull { it.score }

        return ActivityOutlook(
            activity, day, best,
            dayUnavailableReason = when {
                day != null -> null
                dayHours.isEmpty() -> DayUnavailableReason.NoForecast
                missingDaylightBounds -> DayUnavailableReason.MissingDaylightBounds
                expectedHours == 0 -> DayUnavailableReason.NoHoursRemaining
                else -> DayUnavailableReason.Incomplete(evaluations.size, expectedHours)
            },
            windowUnavailableReason = if (best != null) null else WindowUnavailableReason.NO_COMPLETE_WINDOW,
            hourly = hourly,
            warningPeriods = dayWarningPeriods,
        )
    }

    private fun ceilHour(time: LocalDateTime): LocalDateTime {
        val rounded = time.truncatedTo(ChronoUnit.HOURS)
        return if (rounded.isBefore(time)) rounded.plusHours(1) else rounded
    }

    private fun warningPeriods(hours: List<HourlyAssessment>): List<ForecastWarningPeriod> =
        ForecastWarning.entries.flatMap { warning ->
            val periods = mutableListOf<ForecastWarningPeriod>()
            var start: LocalDateTime? = null
            var end: LocalDateTime? = null
            hours.sortedBy { it.time }.forEach { hour ->
                val applies = hour.evaluation?.warnings?.contains(warning) == true
                if (applies && (end == null || end == hour.time)) {
                    if (start == null) start = hour.time
                    end = hour.time.plusHours(1)
                } else {
                    if (start != null && end != null) periods += ForecastWarningPeriod(warning, start, end)
                    start = if (applies) hour.time else null
                    end = if (applies) hour.time.plusHours(1) else null
                }
            }
            if (start != null && end != null) periods += ForecastWarningPeriod(warning, start, end)
            periods
        }.sortedWith(compareBy<ForecastWarningPeriod> { it.start }
            .thenByDescending { it.warning.priority }.thenByDescending { it.warning.tieBreakPriority })
}
