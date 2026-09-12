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
        return translated.replace(Regex("(?<=\\d)\\.(?=\\d)"), ",")
    }

    fun activity(activity: ActivityType) = when (activity) {
        ActivityType.BEACH -> if (language == AppLanguage.SPANISH) "Playa" else activity.label
        ActivityType.HIKING -> if (language == AppLanguage.SPANISH) "Senderismo" else activity.label
    }

    fun rankingMode(mode: RankingMode): String = when (mode) {
        RankingMode.WHOLE_DAY -> if (language == AppLanguage.SPANISH) "Todo el día" else "Whole day"
        RankingMode.BEST_WINDOW -> if (language == AppLanguage.SPANISH) "Mejores 3 horas" else "Best 3 hours"
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

    private val fixed = mapOf(
        "Updating…" to "Actualizando…", "Language" to "Idioma", "English" to "Inglés",
        "Loading forecasts…" to "Cargando previsiones…",
        "Long-range outlook · lower confidence" to "Previsión a largo plazo · menor fiabilidad",
        "Later outlook · forecast may change" to "Previsión posterior · puede cambiar",
        "No forecasts available. Try Refresh." to "No hay previsiones. Pulsa Actualizar.",
        "Unknown" to "Desconocido",
        "Inland estimate · no beach" to "Opción de interior · sin playa",
        "Daylight overall" to "Día completo", "Remaining daylight" to "Luz restante",
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
        "How is this comfort score calculated? ▾" to "¿Cómo se calcula esta puntuación de comodidad? ▾",
        "Hide comfort score calculation ▴" to "Ocultar cálculo de la puntuación de comodidad ▴",
        "The day comfort score is the average of the displayed daylight-hour scores, rounded to a whole number. Each hour uses its available factors, scales their points to 100, then applies any condition limits. Tap an hour to see its inputs and calculation." to
            "La puntuación de comodidad del día es la media de las horas de luz que se muestran. Cada hora se puntúa sobre 100 y se aplican los límites necesarios. Toca una hora para ver el cálculo.",
        "The day comfort score is the average of the displayed daylight-hour scores, rounded to a whole number. Each hour earns up to 70 weather points plus available sea points, always against 100 possible points, then applies any condition limits. Tap an hour to see its inputs and calculation." to
            "La puntuación de comodidad del día es la media de las horas de luz que se muestran. Cada hora suma hasta 70 puntos del tiempo más los puntos disponibles del mar, siempre sobre 100 puntos posibles, y después se aplican los límites necesarios. Toca una hora para ver el cálculo.",
        "Average of these three hourly scores, with any limits for the whole period applied." to
            "Media de esas tres horas, con los límites que correspondan.",
        "Town and nearby-area weather, not exact trail or elevation conditions." to
            "Tiempo previsto para la localidad y sus alrededores, no para una ruta o altitud concretas.",
        "Only available sea data is used. Local shelter, beach flags and currents are not assessed." to
            "Solo se usan los datos del mar disponibles. No se tienen en cuenta las zonas resguardadas, las banderas ni las corrientes.",
        "Weather for outdoor leisure; pool temperatures and river conditions are not assessed." to
            "Tiempo para actividades al aire libre; no incluye la temperatura de las piscinas ni el estado de los ríos.",
        "No forecast for this date." to "No hay previsión para esta fecha.", "No hours remaining." to "No quedan horas de luz.",
        "Daylight times unavailable for this date." to "No están disponibles las horas de luz para esta fecha.",
        "No complete three-hour window" to "No hay una franja completa de tres horas",
        "Weather forecast unavailable." to "No hay previsión del tiempo.",
        "Beach weather unavailable · using town weather" to "No hay previsión para la playa · se usa el tiempo de la localidad",
        "Sea forecast unavailable · using weather only" to "No hay datos del mar · se usa solo el tiempo",
        "Refresh failed · saved forecast" to "No se ha podido actualizar · se usa la previsión guardada",
        "Forecast may be outdated" to "La previsión puede no estar actualizada",
        "Couldn't save forecast" to "No se ha podido guardar la previsión",
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

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings(AppLanguage.ENGLISH) }
