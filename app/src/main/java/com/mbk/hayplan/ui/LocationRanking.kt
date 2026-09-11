package com.mbk.hayplan.ui

import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.ratingFor

enum class RankingMode(val code: String) {
    WHOLE_DAY("whole_day"),
    BEST_WINDOW("best_window");

    companion object {
        fun fromCode(code: String?): RankingMode = entries.firstOrNull { it.code == code } ?: WHOLE_DAY
    }
}

internal val MAIN_LOCATION_IDS = listOf("gijon", "oviedo", "aviles")
internal const val OTHER_LOCATION_LIMIT = 10

internal data class LocationSections(
    val main: List<LocationForecast>,
    val topOthers: List<LocationForecast>,
    val allOthers: List<LocationForecast>,
)

internal fun locationSections(ranked: List<LocationForecast>): LocationSections {
    val byId = ranked.associateBy { it.location.id }
    val main = MAIN_LOCATION_IDS.mapNotNull(byId::get)
    val allOthers = ranked.filter { it.location.id !in MAIN_LOCATION_IDS }
    return LocationSections(main, allOthers.take(OTHER_LOCATION_LIMIT), allOthers)
}

// The chosen period supplies every sort key. Main towns are placed separately after ranking.
internal fun rankLocations(
    forecasts: List<LocationForecast>,
    outlooks: Map<String, ActivityOutlook>,
    mode: RankingMode = RankingMode.WHOLE_DAY,
    coarseScores: Boolean = false,
): List<LocationForecast> = forecasts.sortedWith(
    compareByDescending<LocationForecast> {
        val outlook = outlooks[it.location.id]
        when (mode) {
            RankingMode.WHOLE_DAY -> rankingValue(outlook?.day?.score, coarseScores)
            RankingMode.BEST_WINDOW -> rankingValue(outlook?.bestWindow?.score, coarseScores)
        }
    }
        .thenByDescending {
            val outlook = outlooks[it.location.id]
            if (!coarseScores) 0 else coverageValue(
                if (mode == RankingMode.WHOLE_DAY) outlook?.day?.marineCoverage
                else outlook?.bestWindow?.marineCoverage,
            )
        }
        .thenByDescending {
            val outlook = outlooks[it.location.id]
            rankingValue(if (mode == RankingMode.WHOLE_DAY) outlook?.day?.score
                else outlook?.bestWindow?.score, coarseScores)
        }
        .thenByDescending {
            val outlook = outlooks[it.location.id]
            rankingValue(if (mode == RankingMode.WHOLE_DAY) outlook?.day?.knownConditionsScore
                else outlook?.bestWindow?.knownConditionsScore, coarseScores)
        }
        .thenBy { it.location.id },
)

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
