package com.mbk.hayplan.domain

enum class RecommendedActivity { BEACH, WALK, NONE, UNAVAILABLE }

data class PlaceRecommendation(
    val activity: RecommendedActivity,
    val window: BestWindow? = null,
)

data class DailyRecommendations(
    val gijon: PlaceRecommendation,
    val oviedo: PlaceRecommendation,
)

/** A deliberately small policy for the two places shown in the daily notification. */
object DailyRecommendationPlanner {
    fun create(
        gijonBeach: ActivityOutlook,
        gijonHiking: ActivityOutlook,
        oviedoHiking: ActivityOutlook,
        gijonCoastalWeatherAvailable: Boolean,
    ): DailyRecommendations = DailyRecommendations(
        gijon = when {
            gijonCoastalWeatherAvailable && gijonBeach.bestWindow.isRecommendedBeach() ->
                PlaceRecommendation(RecommendedActivity.BEACH, gijonBeach.bestWindow)
            gijonHiking.bestWindow.isRecommendedWalk() ->
                PlaceRecommendation(RecommendedActivity.WALK, gijonHiking.bestWindow)
            gijonBeach.bestWindow == null && gijonHiking.bestWindow == null ->
                PlaceRecommendation(RecommendedActivity.UNAVAILABLE)
            else -> PlaceRecommendation(RecommendedActivity.NONE)
        },
        oviedo = when {
            oviedoHiking.bestWindow.isRecommendedWalk() ->
                PlaceRecommendation(RecommendedActivity.WALK, oviedoHiking.bestWindow)
            oviedoHiking.bestWindow == null -> PlaceRecommendation(RecommendedActivity.UNAVAILABLE)
            else -> PlaceRecommendation(RecommendedActivity.NONE)
        },
    )

    private fun BestWindow?.isRecommendedBeach(): Boolean = this != null &&
        score >= 60 && marineCoverage == MarineCoverage.FULL && !hasBlockingWarning()

    private fun BestWindow?.isRecommendedWalk(): Boolean = this != null &&
        score >= 40 && !hasBlockingWarning()

    private fun BestWindow.hasBlockingWarning(): Boolean =
        (warnings.asSequence() + warningPeriods.asSequence().map { it.warning })
            .any { it.priority >= 2 }
}
