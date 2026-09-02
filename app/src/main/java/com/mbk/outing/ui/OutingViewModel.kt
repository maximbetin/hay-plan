package com.mbk.outing.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbk.outing.data.ForecastCache
import com.mbk.outing.data.ForecastRepository
import com.mbk.outing.data.LocationCatalog
import com.mbk.outing.data.LocationForecast
import com.mbk.outing.domain.ActivityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class OutingUiState(
    val forecasts: List<LocationForecast> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val activity: ActivityType = ActivityType.BEACH,
    val openedLocationId: String? = null,
    val now: LocalDateTime = LocalDateTime.now(LocationCatalog.zone),
    val nowInstant: Instant = Instant.now(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

class OutingViewModel(
    private val repository: ForecastRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    var uiState by mutableStateOf(OutingUiState())
        private set
    private var loadJob: Job? = null
    private var nextAutomaticLoad = Instant.MIN

    init { load(forceRefresh = false) }

    fun selectDate(date: LocalDate) {
        if (date in uiState.dates) uiState = uiState.copy(selectedDate = date)
    }

    fun selectActivity(activity: ActivityType) {
        uiState = uiState.copy(activity = activity)
    }

    fun openLocation(id: String) { uiState = uiState.copy(openedLocationId = id) }
    fun closeLocation() { uiState = uiState.copy(openedLocationId = null) }

    /** Explicit refresh always bypasses the TTL. Concurrent button taps are coalesced. */
    fun refresh() { load(forceRefresh = true) }

    fun onVisibleTick() {
        updateTime()
        if (!clock.instant().isBefore(nextAutomaticLoad)) load(forceRefresh = false)
    }

    private fun updateTime() {
        val instant = clock.instant()
        val now = LocalDateTime.ofInstant(instant, LocationCatalog.zone)
        val dates = uiState.forecasts.flatMap { it.hiking.hours + it.beach?.hours.orEmpty() }
            .map { it.time.toLocalDate() }.distinct().filter { !it.isBefore(now.toLocalDate()) }.sorted()
        uiState = uiState.copy(
            now = now,
            nowInstant = instant,
            dates = dates,
            selectedDate = uiState.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
        )
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, message = null)
            try {
                val forecasts = repository.load(forceRefresh)
                val sources = forecasts.flatMap { it.hiking.sources + it.beach?.sources.orEmpty() }
                val errors = forecasts.flatMap { it.hiking.errors + it.beach?.errors.orEmpty() }
                val failed = errors.isNotEmpty() || sources.any { it.refreshFailed }
                // Retry automatic failures at most hourly, not every lifecycle tick.
                nextAutomaticLoad = if (failed) clock.instant().plus(ForecastCache.TTL) else
                    sources.minOfOrNull { it.fetchedAt.plus(ForecastCache.TTL) }
                        ?: clock.instant().plus(ForecastCache.TTL)
                uiState = uiState.copy(
                    forecasts = forecasts,
                    message = if (forecasts.all { it.hiking.hours.isEmpty() && it.beach?.hours.isNullOrEmpty() })
                        "Couldn't load forecasts. Check your connection and try Refresh." else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                nextAutomaticLoad = clock.instant().plus(ForecastCache.TTL)
                uiState = uiState.copy(message = "Couldn't refresh forecasts. Please try again.")
            } finally {
                uiState = uiState.copy(isLoading = false)
                updateTime()
            }
        }
    }
}
