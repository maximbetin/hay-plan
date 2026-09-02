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
        val eligible = dayHours.filter {
            !it.time.isBefore(now) && it.isDaylight
        }.distinctBy { it.time }.sortedBy { it.time }
        val scores = eligible.map { ActivityScorer.score(activity, listOf(it)) }
        val sunrise = dayHours.firstNotNullOfOrNull { it.sunrise }
        val sunset = dayHours.firstNotNullOfOrNull { it.sunset }
        val expectedHours = if (sunrise != null && sunset != null) {
            val start = ceilHour(maxOf(sunrise, now))
            val end = sunset.truncatedTo(ChronoUnit.HOURS)
            Duration.between(start, end).toHours().toInt().coerceAtLeast(0)
        } else if (eligible.isEmpty()) 0 else
            Duration.between(eligible.first().time, eligible.last().time).toHours().toInt() + 1
        // A brief good spell cannot determine the headline. Require complete coverage
        // of the daylight period; partial marine data may still provide a best window.
        val day = if (expectedHours > 0 && scores.size == expectedHours && scores.all { it != null }) {
            val known = scores.filterNotNull()
            val mean = known.map { it.score }.average().roundToInt()
            DayRating(ratingFor(mean), mean, known.size, known.count { it.score >= 40 },
                known.flatMap { it.warnings }.distinct())
        } else null

        val best = eligible.windowed(WINDOW_HOURS)
            .filter { window -> window.zipWithNext().all { (a, b) -> a.time.plusHours(1) == b.time } }
            .mapNotNull { window ->
                ActivityScorer.score(activity, window)?.let { scored ->
                    val hourlyMean = window.map {
                        requireNotNull(ActivityScorer.score(activity, listOf(it))).score
                    }.average().roundToInt()
                    // Use the same hourly basis as the day headline. Interval-wide
                    // limits still prevent a dangerous hour being averaged away.
                    val windowScore = minOf(hourlyMean, scored.maximumScore)
                    BestWindow(window.first().time.toLocalTime(), window.last().time.plusHours(1).toLocalTime(),
                        ratingFor(windowScore), windowScore, scored.factors, scored.warnings)
                }
            }.maxByOrNull { it.score }
        return ActivityOutlook(
            activity, day, best,
            dayUnavailableReason = if (day != null) null else if (expectedHours == 0) "No daylight hours remaining."
                else "Incomplete ${if (activity == ActivityType.BEACH) "weather or sea" else "weather"} data for the full day.",
            windowUnavailableReason = if (best != null) null else if (eligible.size < WINDOW_HOURS)
                "No full three-hour daylight window remaining." else "No complete three-hour forecast window.",
        )
    }

    private fun ceilHour(time: LocalDateTime): LocalDateTime {
        val rounded = time.truncatedTo(ChronoUnit.HOURS)
        return if (rounded.isBefore(time)) rounded.plusHours(1) else rounded
    }
}
