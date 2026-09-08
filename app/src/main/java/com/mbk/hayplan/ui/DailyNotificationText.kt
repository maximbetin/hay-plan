package com.mbk.hayplan.ui

import com.mbk.hayplan.domain.DailyRecommendations
import com.mbk.hayplan.domain.PlaceRecommendation
import com.mbk.hayplan.domain.RecommendedActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DailyNotificationText(
    val title: String,
    val body: String,
    val subText: String? = null,
)

object DailyNotificationFormatter {
    fun format(plan: DailyRecommendations, language: AppLanguage, savedForecast: Boolean): DailyNotificationText =
        DailyNotificationText(
            title = place("Gijón", plan.gijon, language),
            body = place("Oviedo", plan.oviedo, language),
            subText = if (!savedForecast) null else if (language == AppLanguage.SPANISH)
                "Previsión guardada" else "Saved forecast",
        )

    private fun place(name: String, recommendation: PlaceRecommendation, language: AppLanguage): String {
        val activity = when (recommendation.activity) {
            RecommendedActivity.BEACH -> if (language == AppLanguage.SPANISH) "playa" else "beach"
            RecommendedActivity.WALK -> if (language == AppLanguage.SPANISH) "paseo" else "walk"
            RecommendedActivity.NONE -> return if (language == AppLanguage.SPANISH)
                "$name: sin recomendación clara" else "$name: no strong recommendation"
            RecommendedActivity.UNAVAILABLE -> return if (language == AppLanguage.SPANISH)
                "$name: previsión no disponible" else "$name: forecast unavailable"
        }
        val window = requireNotNull(recommendation.window)
        val rating = UiStrings(language).rating(window.rating)
        return "$name: $activity ${shortTime(window.start)}–${shortTime(window.end)} · $rating"
    }

    private fun shortTime(time: LocalTime): String =
        time.format(if (time.minute == 0) HOUR else TIME)

    private val HOUR = DateTimeFormatter.ofPattern("HH")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
}
