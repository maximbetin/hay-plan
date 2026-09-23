package com.mbk.hayplan.ui

import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.ratingFor
import java.time.LocalDate

internal const val VISIBLE_LOCATION_LIMIT = 10

// Locations rank by the day score; the best window is shown on each card but never reorders them.
// Inland Beach estimates follow real beaches, regardless of their numerical score.
internal fun rankLocations(
    forecasts: List<LocationForecast>,
    outlooks: Map<String, ActivityOutlook>,
    activity: ActivityType = ActivityType.HIKING,
    coarseScores: Boolean = false,
): List<LocationForecast> = forecasts.sortedWith(
    compareBy<LocationForecast> { noBeach(it, activity) }
        .thenByDescending { rankingValue(outlooks[it.location.id]?.day?.score, coarseScores) }
        .thenByDescending {
            if (!coarseScores) 0 else coverageValue(outlooks[it.location.id]?.day?.marineCoverage)
        }
        .thenByDescending { rankingValue(outlooks[it.location.id]?.day?.knownConditionsScore, coarseScores) }
        .thenBy { it.location.id },
)

/**
 * How good a day looks at a glance: its rating band, how much sea data backs it, then the exact score
 * where one is shown (days 0-2), so the sooner, more certain and visibly higher day wins; earliest on a tie.
 */
private fun glanceOrder(today: LocalDate) = compareBy<DatedOutlook> { rankingValue(it.outlook.day?.score, coarse = true) }
    .thenBy { coverageValue(it.outlook.day?.marineCoverage) }
    .thenBy { point -> point.outlook.day?.score?.takeIf { scorePrecision(point.date, today) == ScorePrecision.EXACT } ?: -1 }
    .thenByDescending { it.date }

/** One location's best day of the week by [glanceOrder]. */
internal fun bestDay(days: List<DatedOutlook>, today: LocalDate): DatedOutlook? =
    days.filter { it.outlook.day != null }.maxWithOrNull(glanceOrder(today))

/** Beach at an inland place is only an estimate: such rows sink to the bottom and never headline the week. */
internal fun noBeach(forecast: LocationForecast, activity: ActivityType) =
    activity == ActivityType.BEACH && forecast.location.coast == null

/** Week rows rank by each location's best day, so the headline is the first row that can hold it. */
internal fun rankLocationsForWeek(
    forecasts: List<LocationForecast>,
    week: Map<String, List<DatedOutlook>>,
    activity: ActivityType,
    today: LocalDate,
): List<LocationForecast> {
    val best = forecasts.associateWith { forecast -> week[forecast.location.id]?.let { bestDay(it, today) } }
    return forecasts.sortedWith(compareBy<LocationForecast> { noBeach(it, activity) }
        .thenByDescending(nullsFirst(glanceOrder(today))) { best[it] }
        .thenBy { it.location.id })
}

internal data class WeekHighlight(val location: LocationForecast, val date: LocalDate, val outlook: ActivityOutlook)

/** The single best place and day of the week by what the grid shows; ties follow [ordered]. */
internal fun weekHighlight(
    ordered: List<LocationForecast>,
    week: Map<String, List<DatedOutlook>>,
    activity: ActivityType,
    today: LocalDate,
): WeekHighlight? = ordered.filterNot { noBeach(it, activity) }.mapNotNull { forecast ->
    week[forecast.location.id]?.let { bestDay(it, today) }?.let { WeekHighlight(forecast, it.date, it.outlook) }
}.maxWithOrNull(compareBy<WeekHighlight, DatedOutlook>(glanceOrder(today)) { DatedOutlook(it.date, it.outlook) }
    .thenBy { -ordered.indexOf(it.location) })

private fun rankingValue(score: Int?, coarse: Boolean): Int = when {
    score == null -> -1
    coarse -> ratingFor(score).ordinal
    else -> score
}

private fun coverageValue(coverage: MarineCoverage?): Int = when (coverage) {
    MarineCoverage.FULL -> 4
    MarineCoverage.WATER, MarineCoverage.WAVES -> 3
    MarineCoverage.MIXED -> 2
    MarineCoverage.NONE -> 1
    null -> 0
}
