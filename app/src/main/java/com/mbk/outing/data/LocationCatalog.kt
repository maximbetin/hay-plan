package com.mbk.outing.data

import com.mbk.outing.domain.ActivityType
import com.mbk.outing.domain.HourlyConditions
import java.time.Instant
import java.time.ZoneId

data class Coordinates(val latitude: Double, val longitude: Double)
data class CoastalReference(val name: String, val coordinates: Coordinates)

data class OutingLocation(
    val id: String,
    val name: String,
    val region: String,
    val coordinates: Coordinates,
    val coast: CoastalReference? = null,
)

object LocationCatalog {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    val locations = listOf(
        OutingLocation(
            id = "gijon",
            name = "Gijón",
            region = "Asturias",
            coordinates = Coordinates(43.5322015, -5.6611195),
            // One representative coastal point, not a catalog or a beach-specific safety forecast.
            // https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML
            coast = CoastalReference("San Lorenzo", Coordinates(43.541062, -5.650062)),
        ),
        OutingLocation(
            id = "oviedo",
            name = "Oviedo",
            region = "Asturias",
            coordinates = Coordinates(43.36029, -5.84476),
        ),
        OutingLocation(
            id = "aviles",
            name = "Avilés",
            region = "Asturias",
            // GeoNames populated-place centre: https://www.geonames.org/advanced-search.html?country=ES&q=Asturias
            coordinates = Coordinates(43.554728, -5.924832),
            // Nearby reference in Castrillón; not represented as being inside Avilés municipality.
            // Instituto Geográfico Nacional entry: https://nomenclator.asturias.me/castrillon/i/84850591/playa-de-salinas
            coast = CoastalReference("Salinas (Castrillón)", Coordinates(43.578125397, -5.96411084)),
        ),
    )
}

data class ForecastSourceStatus(
    val label: String,
    val fetchedAt: Instant,
    val refreshFailed: Boolean,
    val persistenceFailed: Boolean,
)

data class ActivityForecastData(
    val hours: List<HourlyConditions> = emptyList(),
    val sources: List<ForecastSourceStatus> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class LocationForecast(
    val location: OutingLocation,
    val weather: ActivityForecastData,
    val beach: ActivityForecastData = weather,
) {
    fun forActivity(activity: ActivityType): ActivityForecastData = when (activity) {
        ActivityType.BEACH -> beach
        ActivityType.HIKING -> weather
    }
}
