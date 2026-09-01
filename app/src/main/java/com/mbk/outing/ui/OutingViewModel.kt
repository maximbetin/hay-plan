package com.mbk.outing.ui

import androidx.lifecycle.ViewModel
import com.mbk.outing.data.SampleForecast
import com.mbk.outing.domain.ActivityRecommendation
import com.mbk.outing.domain.BeachScorer

data class OutingUiState(
    val locationName: String,
    val areaName: String,
    val dayLabel: String,
    val recommendations: List<ActivityRecommendation>,
    val isSampleData: Boolean,
)

class OutingViewModel : ViewModel() {
    val uiState: OutingUiState

    init {
        val forecast = SampleForecast.tomorrow()
        uiState = OutingUiState(
            locationName = "San Lorenzo",
            areaName = "Gijón, Asturias",
            dayLabel = "Tomorrow",
            recommendations = listOf(
                BeachScorer.swimming(forecast),
                BeachScorer.sunbathing(forecast),
            ),
            isSampleData = true,
        )
    }
}
