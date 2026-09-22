package com.mbk.hayplan.ui

import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.ratingFor
import java.time.LocalDate

internal val MAIN_LOCATION_IDS = listOf("gijon", "oviedo", "aviles")
internal const val OTHER_LOCATION_LIMIT = 10

internal data class LocationSections(
    val main: List<LocationForecast>,
    val topOthers: List<LocationForecast>,
    val allOthers: List<LocationForecast>,
) {
    /** Every location in display order: the fixed main towns, then the rest by rank. */
    val all: List<LocationForecast> get() = main + allOthers
}

internal fun locationSections(ranked: List<LocationForecast>): LocationSections {
    val byId = ranked.associateBy { it.location.id }
    val main = MAIN_LOCATION_IDS.mapNotNull(byId::get)
    val allOthers = ranked.filter { it.location.id !in MAIN_LOCATION_IDS }
    return LocationSections(main, allOthers.take(OTHER_LOCATION_LIMIT), allOthers)
}

// Locations rank by the day score; the best window is shown on each card but never reorders them.
// Main towns are placed separately after ranking.
internal fun rankLocations(
    forecasts: List<LocationForecast>,
    outlooks: Map<String, ActivityOutlook>,
    coarseScores: Boolean = false,
): List<LocationForecast> = forecasts.sortedWith(
    compareByDescending<LocationForecast> { rankingValue(outlooks[it.location.id]?.day?.score, coarseScores) }
        .thenByDescending {
            if (!coarseScores) 0 else coverageValue(outlooks[it.location.id]?.day?.marineCoverage)
        }
        .thenByDescending { rankingValue(outlooks[it.location.id]?.day?.knownConditionsScore, coarseScores) }
        .thenBy { it.location.id },
)

/** How good a day looks at a glance: its rating band, then how much sea data backs it. */
private val glanceOrder = compareBy<ActivityOutlook> { rankingValue(it.day?.score, coarse = true) }
    .thenBy { coverageValue(it.day?.marineCoverage) }

/** One location's best day of the week: highest rating band and evidence, earliest date on a tie. */
internal fun bestDay(days: List<DatedOutlook>): DatedOutlook? =
    days.filter { it.outlook.day != null }.maxWithOrNull(
        compareBy<DatedOutlook, ActivityOutlook>(glanceOrder) { it.outlook }.thenByDescending { it.date })

/** Week rows rank by each location's best day, in rating bands because later days show no exact scores. */
internal fun rankLocationsForWeek(
    forecasts: List<LocationForecast>,
    week: Map<String, List<DatedOutlook>>,
): List<LocationForecast> = rankLocations(forecasts,
    week.mapNotNull { (id, days) -> bestDay(days)?.let { id to it.outlook } }.toMap(), coarseScores = true)

internal data class WeekHighlight(val location: LocationForecast, val date: LocalDate, val outlook: ActivityOutlook)

/**
 * The single best place and day of the week by what the grid shows, preferring the sooner, more certain day;
 * remaining ties follow [ordered] so the answer matches the rows below it.
 */
internal fun weekHighlight(ordered: List<LocationForecast>, week: Map<String, List<DatedOutlook>>): WeekHighlight? =
    ordered.mapNotNull { forecast ->
        week[forecast.location.id]?.let(::bestDay)?.let { WeekHighlight(forecast, it.date, it.outlook) }
    }.maxWithOrNull(compareBy<WeekHighlight, ActivityOutlook>(glanceOrder) { it.outlook }
        .thenByDescending { it.date }.thenBy { -ordered.indexOf(it.location) })

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
