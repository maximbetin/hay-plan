package com.mbk.hayplan.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.MarineCoverage
import com.mbk.hayplan.domain.Rating

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

internal class UiStrings(val language: AppLanguage) {
    operator fun invoke(english: String): String {
        if (language == AppLanguage.ENGLISH) return english
        fixed[english]?.let { return it }
        return when {
            english.startsWith("Top ") && english.contains(" coastal locations") ->
                Regex("Top (\\d+) coastal locations · highest daylight score first").replace(english) {
                    "${it.groupValues[1]} mejores costeras · puntuación diurna más alta primero"
                }
            english.startsWith("Top ") -> Regex("Top (\\d+) · highest daylight score first").replace(english) {
                "${it.groupValues[1]} mejores · puntuación diurna más alta primero"
            }
            english.startsWith("Show all ") -> Regex("Show all (\\d+) locations").replace(english) {
                "Ver las ${it.groupValues[1]} ubicaciones"
            }
            english.startsWith("Updated ") -> english.replaceFirst("Updated", "Actualizado")
            english.startsWith("Weather reference: ") -> english.replaceFirst("Weather reference:", "Referencia meteorológica:")
            english.startsWith("Sea reference: ") -> english.replaceFirst("Sea reference:", "Referencia marítima:")
            english.startsWith("Weather: ") -> english.replaceFirst("Weather:", "Tiempo:")
            english.startsWith("Sea: ") -> english.replaceFirst("Sea:", "Mar:").replace("Weather + sea", "Tiempo y mar")
                .replace("Weather + waves", "Tiempo y olas").replace("Weather + water temperature", "Tiempo y temperatura del agua")
                .replace("Sea data varies by hour", "Los datos marítimos varían por hora")
            english.startsWith("Average of ") -> Regex("Average of (\\d+) remaining daylight hours?").replace(english) {
                "Media de ${it.groupValues[1]} horas de luz restantes"
            }.let { value -> Regex("Average of (\\d+) daylight hours?").replace(value) {
                "Media de ${it.groupValues[1]} horas de luz"
            } }
            english.matches(Regex("\\d+/\\d+ daylight hours Good or better")) ->
                english.replace(" daylight hours Good or better", " horas de luz con valoración buena o superior")
            english.matches(Regex("\\d+/\\d+ hours rated")) -> english.replace(" hours rated", " horas valoradas")
            english.startsWith("Score reduced by ") -> english.replace("Score reduced by", "Puntuación reducida en")
                .replace(" points", " puntos")
            english.contains(" available points × 100 ") -> english.replace("available points", "puntos disponibles")
                .replace("(rounded)", "(redondeado)")
            english.endsWith(" points") -> english.replace(" points", " puntos")
            english.endsWith(" max") -> english.replace(" max", " máx.")
            english.startsWith("Air ") -> english.replaceFirst("Air", "Aire")
                .replace(" · Rain chance ", " · Prob. lluvia ").replace(" · Rain ", " · Lluvia ")
                .replace(" · Wind ", " · Viento ").replace("\nWind ", "\nViento ")
                .replace(" · Water ", " · Agua ").replace(" · Waves ", " · Olas ")
                .replace("Unknown", "Desconocido").replace(" max", " máx.")
            english.startsWith("Incomplete forecast · ") -> english.replace("Incomplete forecast", "Pronóstico incompleto")
                .replace(" hours rated", " horas valoradas")
            else -> english
        }
    }

    fun activity(activity: ActivityType) = when (activity) {
        ActivityType.BEACH -> if (language == AppLanguage.SPANISH) "Playa" else activity.label
        ActivityType.HIKING -> if (language == AppLanguage.SPANISH) "Senderismo" else activity.label
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
        "Back" to "Atrás", "Refresh" to "Actualizar", "Updating…" to "Actualizando…",
        "Settings" to "Ajustes", "Language" to "Idioma", "Close" to "Cerrar", "English" to "Inglés",
        "Long-range outlook" to "Pronóstico a largo plazo", "Loading forecasts…" to "Cargando pronósticos…",
        "No forecasts available. Try Refresh." to "No hay pronósticos disponibles. Pulsa Actualizar.",
        "No daylight remains today" to "Ya no quedan horas de luz hoy",
        "Choose tomorrow to see useful rankings." to "Elige mañana para ver clasificaciones útiles.",
        "View tomorrow" to "Ver mañana", "All locations · highest daylight score first" to "Todas las ubicaciones · puntuación diurna más alta primero",
        "Show top 5" to "Ver las 5 mejores", "Coastal locations" to "Ubicaciones costeras",
        "Inland alternatives" to "Alternativas de interior", "Hourly" to "Por horas",
        "Best 3 hours" to "Mejores 3 horas", "Not recommended" to "No recomendado",
        "Unavailable" to "No disponible", "Unknown" to "Desconocido",
        "Inland estimate · no beach" to "Estimación de interior · sin playa",
        "Daylight overall" to "Día completo", "Remaining daylight" to "Luz restante",
        "Score details" to "Detalles de puntuación", "Daylight hours" to "Horas de luz",
        "Show day score details" to "Mostrar detalles de la puntuación del día",
        "Show best three-hour forecast" to "Mostrar el mejor pronóstico de tres horas",
        "No daylight hours remaining or available." to "No quedan horas de luz disponibles.",
        "Daylight conditions" to "Condiciones durante el día", "Air temperature" to "Temperatura del aire",
        "Wind" to "Viento", "Rain chance" to "Probabilidad de lluvia", "Rainfall" to "Precipitación",
        "Water temperature" to "Temperatura del agua", "Waves" to "Olas", "Air" to "Aire",
        "Water" to "Agua", "Cloud cover" to "Nubosidad", "Rain" to "Lluvia",
        "Average" to "Media", "Highest" to "Máximo", "Total" to "Total",
        "How is this score calculated? ▾" to "¿Cómo se calcula esta puntuación? ▾",
        "Hide score calculation ▴" to "Ocultar cálculo de puntuación ▴",
        "The day score is the average of the displayed daylight-hour scores, rounded to a whole number. Each hour uses its available factors, scales their points to 100, then applies any condition limits. Tap an hour to see its inputs and calculation." to
            "La puntuación del día es la media de las horas de luz mostradas, redondeada a un número entero. Cada hora usa los factores disponibles, escala sus puntos a 100 y aplica los límites de las condiciones. Toca una hora para ver sus datos y cálculo.",
        "Average of these three hourly scores, with any limits for the whole period applied." to
            "Media de las puntuaciones de estas tres horas, aplicando los límites del periodo completo.",
        "Forecast data is incomplete for this hour." to "Los datos del pronóstico están incompletos para esta hora.",
        "Personal weather-suitability score, not forecast confidence or safety." to
            "Puntuación personal de idoneidad meteorológica; no indica confianza ni seguridad.",
        "Town weather, not trail or elevation conditions." to "Tiempo de la localidad, no de rutas ni de altitud.",
        "Only available sea data is used. Local shelter, beach flags and currents are not assessed." to
            "Solo se usan los datos marítimos disponibles. No se evalúan el abrigo local, las banderas ni las corrientes.",
        "Weather for outdoor leisure; pool temperatures and river conditions are not assessed." to
            "Tiempo para ocio al aire libre; no se evalúan piscinas ni condiciones de los ríos.",
        "No forecast for this date." to "No hay pronóstico para esta fecha.", "No hours remaining." to "No quedan horas.",
        "No complete three-hour window" to "No hay un periodo completo de tres horas",
        "Weather forecast unavailable." to "Pronóstico meteorológico no disponible.",
        "Sea forecast unavailable · using weather only" to "Pronóstico marítimo no disponible · usando solo el tiempo",
        "Refresh failed · saved forecast" to "Falló la actualización · usando el pronóstico guardado",
        "Forecast may be outdated" to "El pronóstico puede estar desactualizado",
        "Couldn't save forecast" to "No se pudo guardar el pronóstico",
        "Couldn't load forecasts. Try Refresh." to "No se pudieron cargar los pronósticos. Pulsa Actualizar.",
        "Couldn't refresh forecasts. Please try again." to "No se pudo actualizar. Inténtalo de nuevo.",
        "Rough waves: rating limited to Poor." to "Oleaje fuerte: valoración limitada a Mala.",
        "Choppy waves: rating limited to Good." to "Oleaje movido: valoración limitada a Buena.",
        "Cold water: rating limited to Fair." to "Agua fría: valoración limitada a Regular.",
        "Freezing temperatures: rating limited to Fair." to "Temperaturas bajo cero: valoración limitada a Regular.",
        "Strong wind: rating limited to Poor." to "Viento fuerte: valoración limitada a Mala.",
        "Rainfall: rating limited to Poor." to "Lluvia: valoración limitada a Mala.",
        "Rain: rating limited to Fair." to "Lluvia: valoración limitada a Regular.",
        "High heat: rating limited to Fair." to "Calor intenso: valoración limitada a Regular.",
    )
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings(AppLanguage.ENGLISH) }
