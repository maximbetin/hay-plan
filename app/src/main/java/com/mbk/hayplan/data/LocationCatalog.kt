package com.mbk.hayplan.data

import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import java.time.Instant
import java.time.ZoneId

data class Coordinates(val latitude: Double, val longitude: Double)
data class CoastalReference(val name: String, val coordinates: Coordinates)

data class HayPlanLocation(
    val id: String,
    val name: String,
    val region: String,
    val coordinates: Coordinates,
    val coast: CoastalReference? = null,
    val weatherReference: String? = null,
)

object LocationCatalog {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    val locations = listOf(
        HayPlanLocation(
            id = "gijon",
            name = "Gijón",
            region = "Asturias",
            coordinates = Coordinates(43.5322015, -5.6611195),
            // One representative coastal point, not a catalog or a beach-specific safety forecast.
            // https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML
            coast = CoastalReference("San Lorenzo", Coordinates(43.541062, -5.650062)),
        ),
        HayPlanLocation(
            id = "oviedo",
            name = "Oviedo",
            region = "Asturias",
            coordinates = Coordinates(43.36029, -5.84476),
        ),
        HayPlanLocation(
            id = "aviles",
            name = "Avilés",
            region = "Asturias",
            // GeoNames populated-place centre: https://www.geonames.org/advanced-search.html?country=ES&q=Asturias
            coordinates = Coordinates(43.554728, -5.924832),
            // Nearby reference in Castrillón; not represented as being inside Avilés municipality.
            // Instituto Geográfico Nacional entry: https://nomenclator.asturias.me/castrillon/i/84850591/playa-de-salinas
            coast = CoastalReference("Salinas (Castrillón)", Coordinates(43.578125397, -5.96411084)),
        ),
        HayPlanLocation(
            // New town centres: Open-Meteo geocoding (GeoNames), verified September 2026.
            id = "luanco", name = "Luanco", region = "Asturias",
            coordinates = Coordinates(43.61517, -5.79344),
            // Turismo Asturias GPS; the marine client selects the nearest sea grid cell.
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-luanco
            coast = CoastalReference("Luanco", Coordinates(43.618597, -5.787932)),
        ),
        HayPlanLocation(
            id = "llanes", name = "Llanes", region = "Asturias",
            coordinates = Coordinates(43.41982, -4.75485),
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-el-sablon
            coast = CoastalReference("El Sablón", Coordinates(43.42294, -4.75275)),
        ),
        HayPlanLocation(
            id = "ribadesella", name = "Ribadesella", region = "Asturias",
            coordinates = Coordinates(43.46145, -5.05955),
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-ribadesella-santa-marina
            coast = CoastalReference("Santa Marina", Coordinates(43.465361, -5.070060)),
        ),
        HayPlanLocation(
            id = "candas", name = "Candás", region = "Asturias",
            coordinates = Coordinates(43.58922, -5.76735),
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-la-palmera
            coast = CoastalReference("La Palmera", Coordinates(43.587525, -5.761127)),
        ),
        HayPlanLocation(
            id = "luarca", name = "Luarca", region = "Asturias",
            coordinates = Coordinates(43.54343, -6.53986),
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-1-y-2-de-luarca
            coast = CoastalReference("Primera y Segunda de Luarca", Coordinates(43.546709, -6.538224)),
        ),
        HayPlanLocation(
            id = "cangas-de-onis", name = "Cangas de Onís", region = "Asturias",
            coordinates = Coordinates(43.3514, -5.12916),
            // Inland: weather only, as for Oviedo; no river or lake conditions inferred.
        ),
        HayPlanLocation(
            id = "salinas", name = "Salinas", region = "Asturias",
            coordinates = Coordinates(43.57669, -5.95575),
            // Same beach point as Avilés so the marine request/cache is shared.
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-salinas
            coast = CoastalReference("Salinas (Castrillón)", Coordinates(43.578125397, -5.96411084)),
        ),
        HayPlanLocation(
            id = "cudillero", name = "Cudillero", region = "Asturias",
            coordinates = Coordinates(43.56217, -6.14589),
            // Representative beach in the municipality, not in the harbour itself.
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-la-concha-de-artedo
            coast = CoastalReference("Concha de Artedo", Coordinates(43.56453, -6.18882)),
        ),
        HayPlanLocation(
            id = "tapia-de-casariego", name = "Tapia de Casariego", region = "Asturias",
            coordinates = Coordinates(43.57019, -6.94383),
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-anguileiro
            coast = CoastalReference("Anguileiro", Coordinates(43.566243, -6.949220)),
        ),
        HayPlanLocation(
            id = "villaviciosa", name = "Villaviciosa", region = "Asturias",
            coordinates = Coordinates(43.48126, -5.43574),
            // Beach at the estuary mouth; weather remains the town's weather.
            // https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-rodiles
            coast = CoastalReference("Rodiles", Coordinates(43.533404, -5.379658)),
        ),
        HayPlanLocation(
            id = "somiedo", name = "Somiedo", region = "Asturias",
            coordinates = Coordinates(43.0929, -6.25629),
            // Park visitor centre is in Pola de Somiedo; no whole-park/mountain forecast.
            // https://www.turismoasturias.es/es/descubre/donde-ir/municipios/somiedo
            weatherReference = "Pola de Somiedo",
        ),
        HayPlanLocation(
            id = "redes", name = "Redes", region = "Asturias",
            coordinates = Coordinates(43.1831, -5.34476),
            // Campo de Caso is the visitor-centre town for Redes, not a coastal location.
            // https://www.turismoasturias.es/es/-/blogs/las-10-mejores-cosas-que-ver-y-hacer-en-el-parque-natural-de-redes
            weatherReference = "Campo de Caso",
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
    val location: HayPlanLocation,
    val weather: ActivityForecastData,
    val beach: ActivityForecastData = weather,
) {
    fun forActivity(activity: ActivityType): ActivityForecastData = when (activity) {
        ActivityType.BEACH -> beach
        ActivityType.HIKING -> weather
    }
}
