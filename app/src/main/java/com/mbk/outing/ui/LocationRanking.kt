package com.mbk.outing.ui

import com.mbk.outing.data.LocationForecast
import com.mbk.outing.domain.ActivityOutlook

// Use only the displayed day rating. A good window cannot substitute for an incomplete day.
internal fun rankLocations(
    forecasts: List<LocationForecast>,
    outlooks: Map<String, ActivityOutlook>,
): List<LocationForecast> = forecasts.sortedWith(
    compareByDescending<LocationForecast> { outlooks[it.location.id]?.day?.score ?: -1 }
        .thenBy { it.location.id },
)
