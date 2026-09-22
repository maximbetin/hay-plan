package com.mbk.hayplan.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.Rating
import com.mbk.hayplan.domain.ForecastWarning
import com.mbk.hayplan.domain.DayUnavailableReason
import com.mbk.hayplan.domain.WindowUnavailableReason
import com.mbk.hayplan.data.ForecastIssue
import com.mbk.hayplan.domain.ForecastWarningPeriod
import java.time.format.DateTimeFormatter

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH
        fun fromSystem() = if (java.util.Locale.getDefault().language == "es") SPANISH else ENGLISH
    }
}

internal class UiStrings(val language: AppLanguage) {
    operator fun invoke(warning: ForecastWarning): String = invoke(warning.message)
    operator fun invoke(issue: ForecastIssue): String = invoke(issue.message)

    fun dayUnavailable(reason: DayUnavailableReason?): String = when (reason) {
        null -> ""
        DayUnavailableReason.NoForecast -> invoke("No forecast for this date.")
        DayUnavailableReason.MissingDaylightBounds -> invoke("Daylight times unavailable for this date.")
        DayUnavailableReason.NoHoursRemaining -> invoke("No hours remaining.")
        is DayUnavailableReason.Incomplete -> if (language == AppLanguage.SPANISH)
            "Previsión incompleta · ${reason.ratedHours}/${reason.expectedHours} horas valoradas"
        else "Incomplete forecast · ${reason.ratedHours}/${reason.expectedHours} hours rated"
    }

    fun windowUnavailable(reason: WindowUnavailableReason?): String = when (reason) {
        null -> ""
        WindowUnavailableReason.NO_COMPLETE_WINDOW -> invoke("No complete three-hour window")
    }
    operator fun invoke(english: String): String {
        if (language == AppLanguage.ENGLISH) return english
        val translated = fixed[english] ?: when {
            english.contains(" available points × 100 ") -> english.replace("available points", "puntos disponibles")
                .replace("(rounded)", "(redondeado)")
            english.startsWith("Weighted total: ") -> english.replace("Weighted total:", "Total ponderado:")
            english.endsWith(" points") -> english.replace(" points", " puntos")
            english.endsWith(" max") -> english.replace(" max", " máx.")
            english.endsWith(" avg") -> english.replace(" avg", " media")
            english.endsWith(" min") -> english.replace(" min", " mín.")
            else -> english
        }
        return translated.replace(DECIMAL_POINT, ",")
    }

    fun activity(activity: ActivityType) = when (activity) {
        // Playful Andalusian diminutives, deliberately only in Spanish.
        ActivityType.BEACH -> if (language == AppLanguage.SPANISH) "Playita" else activity.label
        ActivityType.HIKING -> if (language == AppLanguage.SPANISH) "Paseíto" else activity.label
    }

    fun updated(value: String) = if (language == AppLanguage.SPANISH) "Actualizado: $value" else "Updated $value"
    fun weatherSource(name: String) = if (language == AppLanguage.SPANISH) "Tiempo: $name" else "Weather: $name"
    fun seaSource(name: String) = if (language == AppLanguage.SPANISH) "Mar: $name" else "Sea: $name"
    fun seaSource(name: String, coverage: MarineCoverage) = "${seaSource(name)} · ${coverage(coverage)}"

    fun warningPeriod(period: ForecastWarningPeriod): String {
        val warning = invoke(period.warning).removeSuffix(".")
        return "$warning · ${period.start.format(TIME)}–${period.end.format(TIME)}"
    }

    fun rating(rating: Rating) = if (language == AppLanguage.ENGLISH) rating.label else when (rating) {
        Rating.POOR -> "Mala"
        Rating.FAIR -> "Regular"
        Rating.GOOD -> "Buena"
        Rating.VERY_GOOD -> "Muy buena"
        Rating.EXCELLENT -> "Excelente"
    }

    fun coverage(coverage: MarineCoverage) = if (language == AppLanguage.ENGLISH) coverage.label else when (coverage) {
        MarineCoverage.NONE -> "Solo tiempo"
        MarineCoverage.WATER -> "Tiempo y temperatura del agua"
        MarineCoverage.WAVES -> "Tiempo y olas"
        MarineCoverage.FULL -> "Tiempo y mar"
        MarineCoverage.MIXED -> "Los datos marítimos varían por hora"
    }

    // Shared across instances: presentation helpers create a UiStrings per call.
    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DECIMAL_POINT = Regex("(?<=\\d)\\.(?=\\d)")
        private val fixed = mapOf(
            "Long-range outlook · lower confidence" to "Previsión a largo plazo · menor fiabilidad",
            "Later outlook · forecast may change" to "Previsión posterior · puede cambiar",
            "Unknown" to "Desconocido",
            "Inland estimate · no beach" to "Opción de interior · sin playa",
            "Air temperature" to "Temperatura del aire",
            "Wind" to "Viento", "Wind gusts" to "Rachas de viento", "Rain chance" to "Probabilidad de lluvia",
            "Rainfall" to "Lluvia", "Feels like" to "Sensación térmica", "Humidity" to "Humedad",
            "Visibility" to "Visibilidad", "UV index" to "Índice UV", "Conditions" to "Condiciones",
            "Water temperature" to "Temperatura del agua", "Waves" to "Olas", "Air" to "Aire",
            "Water" to "Agua", "Cloud cover" to "Nubosidad", "Rain" to "Lluvia",
            "Average" to "Media", "Highest" to "Máximo", "Lowest" to "Mínimo", "Total" to "Total",
            "Most significant" to "Lo más importante", "Clear" to "Despejado", "Mainly clear" to "Poco nuboso",
            "Partly cloudy" to "Sol y nubes", "Overcast" to "Nublado", "Fog" to "Niebla",
            "Drizzle" to "Llovizna", "Rain showers" to "Chubascos", "Snow" to "Nieve",
            "Snow showers" to "Chubascos de nieve", "Thunderstorm" to "Tormenta", "Mixed conditions" to "Tiempo variable",
            "No forecast for this date." to "No hay previsión para esta fecha.", "No hours remaining." to "No quedan horas de luz.",
            "Daylight times unavailable for this date." to "No están disponibles las horas de luz para esta fecha.",
            "No complete three-hour window" to "No hay una franja completa de tres horas",
            "Weather forecast unavailable." to "No hay previsión del tiempo.",
            "Beach weather unavailable · using town weather" to "No hay previsión para la playa · se usa el tiempo de la localidad",
            "Sea forecast unavailable · using weather only" to "No hay datos del mar · se usa solo el tiempo",
            "Couldn't load forecasts. Try Refresh." to "No se han podido cargar las previsiones. Pulsa Actualizar.",
            "Couldn't refresh forecasts. Please try again." to "No se ha podido actualizar. Inténtalo de nuevo.",
            "Rough waves." to "Olas fuertes.",
            "Choppy waves." to "Mar movido.",
            "Cold water." to "Agua fría.",
            "Freezing temperatures." to "Temperaturas bajo cero.",
            "Very strong wind." to "Viento muy fuerte.",
            "Very strong gusts." to "Rachas muy fuertes.",
            "Strong gusts." to "Rachas fuertes.",
            "Moderate gusts." to "Rachas moderadas.",
            "Heavy rain." to "Lluvia fuerte.",
            "Rain." to "Lluvia.",
            "Possible rain." to "Puede llover.",
            "Cold." to "Frío.",
            "Extreme heat." to "Mucho calor.",
            "Hot." to "Calor.",
            "Overcast." to "Cielo nublado.",
            "Very low visibility." to "Visibilidad muy baja.",
            "Low visibility." to "Visibilidad baja.",
            "Extreme UV · Use protection." to "Índice UV extremo · Usa protección.",
            "Very high UV · Use protection." to "Índice UV muy alto · Usa protección.",
            "Thunderstorm." to "Tormenta.",
            "Freezing rain." to "Lluvia helada.",
            "Heavy rain or snow." to "Lluvia o nieve fuerte.",
            "Fog." to "Niebla.",
            "Moderate rain or snow." to "Lluvia o nieve moderada.",
            "Light rain or snow." to "Lluvia o nieve débil.",
        )
    }
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings(AppLanguage.ENGLISH) }
