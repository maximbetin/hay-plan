package com.mbk.hayplan.ui

import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityOutlook

// Use only the displayed day rating. A good window cannot substitute for an incomplete day.
internal fun rankLocations(
    forecasts: List<LocationForecast>,
    outlooks: Map<String, ActivityOutlook>,
): List<LocationForecast> = forecasts.sortedWith(
    compareByDescending<LocationForecast> { outlooks[it.location.id]?.day?.score ?: -1 }
        .thenByDescending { outlooks[it.location.id]?.day?.uncappedScore ?: -1 }
        .thenBy { it.location.id },
)
