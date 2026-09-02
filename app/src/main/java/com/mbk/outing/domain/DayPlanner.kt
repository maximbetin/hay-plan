package com.mbk.outing.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class ActivityOutlook(
    val activity: String,
    val recommendation: ActivityRecommendation?,
    val unavailableReason: String? = null,
)

object DayPlanner {
    fun forDate(hours: List<HourlyConditions>, date: LocalDate, now: LocalDateTime): List<ActivityOutlook> {
        val eligible = hours.filter {
            it.time.toLocalDate() == date && !it.time.isBefore(now) && it.isDaylight
        }
        val hasWindow = eligible.sortedBy { it.time }.windowed(3).any {
            it[0].time.plusHours(1) == it[1].time && it[1].time.plusHours(1) == it[2].time
        }
        val noDaylight = "No full three-hour daylight window remaining."
        val swimming = BeachScorer.swimming(eligible)
        val sunbathing = BeachScorer.sunbathing(eligible)
        val swimmingReason = when {
            !hasWindow -> noDaylight
            eligible.none { it.waveHeightM != null && it.seaTemperatureC != null } ->
                "Marine forecast unavailable for this date."
            else -> "Not enough weather or marine data for a three-hour window."
        }
        return listOf(
            ActivityOutlook("Swimming", swimming, if (swimming == null) swimmingReason else null),
            ActivityOutlook("Sunbathing", sunbathing, if (sunbathing != null) null else
                if (!hasWindow) noDaylight else "Not enough weather data for a three-hour window."),
        )
    }
}
