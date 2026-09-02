package com.mbk.outing.data

import com.mbk.outing.domain.ActivityType
import com.mbk.outing.domain.HourlyConditions
import java.time.Instant
import java.time.ZoneId

data class Coordinates(val latitude: Double, val longitude: Double)
data class BeachLocation(val id: String, val name: String, val coordinates: Coordinates)

data class OutingLocation(
    val id: String,
    val name: String,
    val region: String,
    val coordinates: Coordinates,
    val beaches: List<BeachLocation> = emptyList(),
) {
    val mainBeach: BeachLocation? get() = beaches.firstOrNull()
    fun beach(id: String?): BeachLocation? = beaches.find { it.id == id } ?: mainBeach
}

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
            beaches = listOf(
                BeachLocation("san-lorenzo", "San Lorenzo", Coordinates(43.541062, -5.650062)),
                BeachLocation("poniente", "Poniente", Coordinates(43.542312, -5.672437)),
                BeachLocation("arbeyal", "L'Arbeyal", Coordinates(43.544812, -5.694062)),
                BeachLocation("estano", "Estaño", Coordinates(43.547813, -5.597687)),
                BeachLocation("penarrubia", "Peñarrubia", Coordinates(43.550125, -5.628452)),
                BeachLocation("serin", "Serín", Coordinates(43.549688, -5.608)),
                BeachLocation("cagonera", "La Cagonera", Coordinates(43.54917, -5.606109)),
                BeachLocation("mayanes", "Los Mayanes", Coordinates(43.545062, -5.643438)),
                BeachLocation("rinconin", "El Rinconín", Coordinates(43.548329, -5.639259)),
            ),
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
    val beaches: Map<String, ActivityForecastData> = emptyMap(),
) {
    val beach: ActivityForecastData? get() = location.mainBeach?.let { beaches[it.id] }

    fun forActivity(activity: ActivityType, beachId: String? = null): ActivityForecastData? = when (activity) {
        ActivityType.BEACH -> location.beach(beachId)?.let { beaches[it.id] }
        ActivityType.HIKING -> hiking
    }
}
