package com.mbk.outing.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbk.outing.data.BeachCatalog
import com.mbk.outing.data.BeachForecast
import com.mbk.outing.data.ForecastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class OutingUiState(
    val forecasts: List<BeachForecast> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val now: LocalDateTime = LocalDateTime.now(BeachCatalog.zone),
    val fetchedAt: LocalDateTime? = null,
    val isLoading: Boolean = true,
    val message: String? = null,
)

class OutingViewModel(
    private val repository: ForecastRepository = ForecastRepository(),
    private val clock: Clock = Clock.system(BeachCatalog.zone),
) : ViewModel() {
    var uiState by mutableStateOf(OutingUiState(now = LocalDateTime.now(clock)))
        private set

    private var refreshJob: Job? = null

    init { refresh() }

    fun selectDate(date: LocalDate) {
        if (date in uiState.dates) uiState = uiState.copy(selectedDate = date)
    }

    fun updateTime() {
        val now = LocalDateTime.now(clock)
        val dates = availableDates(uiState.forecasts, now.toLocalDate())
        uiState = uiState.copy(
            now = now,
            dates = dates,
            selectedDate = uiState.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
        )
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, message = null)
            val forecasts = repository.load()
            val now = LocalDateTime.now(clock)
            if (forecasts.all { it.weatherError }) {
                // Keep an earlier successful fetch visible, explicitly marked as such.
                uiState = uiState.copy(
                    isLoading = false,
                    now = now,
                    message = if (uiState.forecasts.isEmpty()) {
                        "Couldn't load forecasts. Check your connection and try again."
                    } else {
                        "Refresh failed. Showing the last fetched forecasts."
                    },
                )
                updateTime()
            } else {
                val dates = availableDates(forecasts, now.toLocalDate())
                uiState = uiState.copy(
                    forecasts = forecasts,
                    dates = dates,
                    selectedDate = uiState.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
                    now = now,
                    fetchedAt = now,
                    isLoading = false,
                    message = null,
                )
            }
        }
    }

    private fun availableDates(forecasts: List<BeachForecast>, today: LocalDate) =
        forecasts.flatMap { it.hours }.map { it.time.toLocalDate() }
            .distinct().filter { !it.isBefore(today) }.sorted()
}
