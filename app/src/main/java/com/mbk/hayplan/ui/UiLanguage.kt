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
            english.startsWith("Show all ") -> Regex("Show all (\\d+) locations").replace(english) {
                "Ver las ${it.groupValues[1]} ubicaciones"
            }
            english.startsWith("Updated ") -> english.replaceFirst("Updated ", "Actualizado: ")
            english.startsWith("Weather reference: ") -> english.replaceFirst("Weather reference:", "Referencia del tiempo:")
            english.startsWith("Sea reference: ") -> english.replaceFirst("Sea reference:", "Referencia del mar:")
            english.startsWith("Weather: ") -> english.replaceFirst("Weather:", "Tiempo:")
            english.startsWith("Sea: ") -> english.replaceFirst("Sea:", "Mar:").replace("Weather + sea", "Tiempo y mar")
                .replace("Weather + waves", "Tiempo y olas").replace("Weather + water temperature", "Tiempo y temperatura del agua")
                .replace("Sea data varies by hour", "Los datos marítimos varían por hora")
            english.startsWith("Average of 1 remaining daylight hour") -> "Media de 1 hora de luz restante"
            english.startsWith("Average of ") -> Regex("Average of (\\d+) remaining daylight hours").replace(english) {
                "Media de ${it.groupValues[1]} horas de luz restantes"
            }.let { value -> Regex("Average of (\\d+) daylight hours?").replace(value) {
                val count = it.groupValues[1]
                if (count == "1") "Media de 1 hora de luz" else "Media de $count horas de luz"
            } }
            english.matches(Regex("\\d+/\\d+ daylight hours Good or better")) -> {
                val values = english.substringBefore(' ').split('/')
                "${values[0]} de ${values[1]} horas con buen tiempo"
            }
            english.matches(Regex("\\d+/\\d+ hours rated")) -> english.replace(" hours rated", " horas valoradas")
            english.startsWith("Score reduced by ") -> english.replace("Score reduced by", "Se restan")
                .replace(" points", " puntos")
            english.contains(" available points × 100 ") -> english.replace("available points", "puntos disponibles")
                .replace("(rounded)", "(redondeado)")
            english.startsWith("Weighted total: ") -> english.replace("Weighted total:", "Total ponderado:")
            english.endsWith(" points") -> english.replace(" points", " puntos")
            english.startsWith("Air ") -> english.replaceFirst("Air", "Aire")
                .replace(" · Rain chance ", " · Prob. lluvia ").replace(" · Rain ", " · Lluvia ")
                .replace(" · Wind ", " · Viento ").replace("\nWind ", "\nViento ")
                .replace(" · Water ", " · Agua ").replace(" · Waves ", " · Olas ")
                .replace("Unknown", "Desconocido").replace(" max", " máx.")
            english.startsWith("Feels ") -> english.replaceFirst("Feels", "Sensación")
                .replace(" · Rain chance ", " · Prob. lluvia ").replace(" · Rain ", " · Lluvia ")
                .replace(" · Gusts ", " · Rachas ").replace("\nGusts ", "\nRachas ")
                .replace(" · Water ", " · Agua ").replace(" · Waves ", " · Olas ")
                .replace("\nRain ", "\nLluvia ").replace("\nClouds ", "\nNubes ")
                .replace(" · Clouds ", " · Nubes ").replace(" · UV ", " · UV ")
                .replace("Unknown", "Desconocido").replace(" max", " máx.").replace(" avg", " media")
            english.endsWith(" max") -> english.replace(" max", " máx.")
            english.endsWith(" avg") -> english.replace(" avg", " media")
            english.endsWith(" min") -> english.replace(" min", " mín.")
            english.startsWith("Incomplete forecast · ") -> english.replace("Incomplete forecast", "Previsión incompleta")
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
        "Long-range outlook" to "Previsión a largo plazo", "Loading forecasts…" to "Cargando previsiones…",
        "No forecasts available. Try Refresh." to "No hay previsiones. Pulsa Actualizar.",
        "No daylight remains today" to "Ya no quedan horas de luz",
        "Choose tomorrow to see useful rankings." to "Elige mañana para ver las mejores opciones.",
        "View tomorrow" to "Ver mañana", "All locations" to "Todos los lugares",
        "Best locations" to "Mejores lugares", "Best coastal locations" to "Mejores lugares de costa",
        "Show top 5" to "Ver los 5 mejores", "Coastal locations" to "Lugares de costa",
        "Inland alternatives" to "Opciones de interior", "Hourly" to "Por horas",
        "Best 3 hours" to "Mejor franja de 3 horas", "More conditions" to "Ver más",
        "Fewer conditions" to "Ver menos",
        "Unavailable" to "No disponible", "Unknown" to "Desconocido",
        "Inland estimate · no beach" to "Opción de interior · sin playa",
        "Daylight overall" to "Día completo", "Remaining daylight" to "Horas de luz restantes",
        "Score details" to "Detalles de puntuación", "Daylight hours" to "Horas de luz",
        "Show day score details" to "Mostrar detalles de la puntuación del día",
        "Show best three-hour forecast" to "Mostrar la mejor franja de tres horas",
        "No daylight hours remaining or available." to "No quedan horas de luz disponibles.",
        "Daylight conditions" to "Tiempo durante el día", "Air temperature" to "Temperatura del aire",
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
        "How is this score calculated? ▾" to "¿Cómo se calcula esta puntuación? ▾",
        "Hide score calculation ▴" to "Ocultar cálculo de puntuación ▴",
        "The day score is the average of the displayed daylight-hour scores, rounded to a whole number. Each hour uses its available factors, scales their points to 100, then applies any condition limits. Tap an hour to see its inputs and calculation." to
            "La puntuación del día es la media de las horas de luz que se muestran. Cada hora se puntúa sobre 100 y se aplican los límites necesarios. Toca una hora para ver el cálculo.",
        "Average of these three hourly scores, with any limits for the whole period applied." to
            "Media de esas tres horas, con los límites que correspondan.",
        "Forecast data is incomplete for this hour." to "Faltan datos para esta hora.",
        "Personal weather-suitability score, not forecast confidence or safety." to
            "Puntuación orientativa según el tiempo; no mide la fiabilidad de la previsión ni garantiza la seguridad.",
        "Town and nearby-area weather, not exact trail or elevation conditions." to
            "Tiempo previsto para la localidad y sus alrededores, no para una ruta o altitud concretas.",
        "Only available sea data is used. Local shelter, beach flags and currents are not assessed." to
            "Solo se usan los datos del mar disponibles. No se tienen en cuenta las zonas resguardadas, las banderas ni las corrientes.",
        "Weather for outdoor leisure; pool temperatures and river conditions are not assessed." to
            "Tiempo para actividades al aire libre; no incluye la temperatura de las piscinas ni el estado de los ríos.",
        "No forecast for this date." to "No hay previsión para esta fecha.", "No hours remaining." to "No quedan horas de luz.",
        "No complete three-hour window" to "No hay una franja completa de tres horas",
        "Weather forecast unavailable." to "No hay previsión del tiempo.",
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
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings(AppLanguage.ENGLISH) }
