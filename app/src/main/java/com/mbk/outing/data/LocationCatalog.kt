package com.mbk.outing.data

import com.mbk.outing.domain.ActivityType
import com.mbk.outing.domain.HourlyConditions
import java.time.Instant
import java.time.ZoneId

data class Coordinates(val latitude: Double, val longitude: Double)
data class MainBeach(val name: String, val coordinates: Coordinates)

data class OutingLocation(
    val id: String,
    val name: String,
    val region: String,
    val coordinates: Coordinates,
    val mainBeach: MainBeach? = null,
)

object LocationCatalog {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    // Static catalog, not a runtime dependency. A town may have no assigned beach.
    // Coordinates: https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML
    val locations = listOf(
        OutingLocation(
            id = "gijon",
            name = "Gijón",
            region = "Asturias",
            coordinates = Coordinates(43.5322015, -5.6611195),
            mainBeach = MainBeach("San Lorenzo", Coordinates(43.541062, -5.650062)),
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
    val hiking: ActivityForecastData,
    val beach: ActivityForecastData?,
) {
    fun forActivity(activity: ActivityType): ActivityForecastData? = when (activity) {
        ActivityType.BEACH -> beach
        ActivityType.HIKING -> hiking
    }
}
