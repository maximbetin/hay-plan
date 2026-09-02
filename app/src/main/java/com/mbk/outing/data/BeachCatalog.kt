package com.mbk.outing.data

import com.mbk.outing.domain.HourlyConditions
import java.time.ZoneId

data class Beach(val id: String, val name: String, val latitude: Double, val longitude: Double)

object BeachCatalog {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    // Fixed beach coordinates; no geolocation permission or geocoding service needed.
    // Source: https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML
    val beaches = listOf(
        Beach("san-lorenzo", "San Lorenzo", 43.541062, -5.650062),
        Beach("poniente", "Poniente", 43.542312, -5.672437),
        Beach("estano", "Estaño", 43.547813, -5.597687),
    )
}

data class BeachForecast(
    val beach: Beach,
    val hours: List<HourlyConditions>,
    val weatherError: Boolean = false,
    val marineError: Boolean = false,
)
