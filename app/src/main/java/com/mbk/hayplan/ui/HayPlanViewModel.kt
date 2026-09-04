package com.mbk.hayplan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.ActivityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class HayPlanUiState(
    val forecasts: List<LocationForecast> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val activity: ActivityType = ActivityType.BEACH,
    val openedLocationId: String? = null,
    val now: LocalDateTime = LocalDateTime.now(LocationCatalog.zone),
    val nowInstant: Instant = Instant.now(),
    val isLoading: Boolean = true,
    val message: String? = null,
) {
    val opened: LocationForecast? get() = forecasts.find { it.location.id == openedLocationId }

    fun openLocation(id: String): HayPlanUiState =
        if (forecasts.any { it.location.id == id }) copy(openedLocationId = id) else this

    fun selectActivity(selected: ActivityType): HayPlanUiState =
        copy(activity = selected)

    fun atTime(instant: Instant): HayPlanUiState {
        val localNow = LocalDateTime.ofInstant(instant, LocationCatalog.zone)
        val availableDates = forecasts.flatMap { it.weather.hours }.map { it.time.toLocalDate() }
            .distinct().filter { !it.isBefore(localNow.toLocalDate()) }.sorted()
        return copy(now = localNow, nowInstant = instant, dates = availableDates,
            selectedDate = selectedDate?.takeIf { it in availableDates } ?: availableDates.firstOrNull())
    }
}

class HayPlanViewModel(
    private val repository: ForecastRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    var uiState by mutableStateOf(HayPlanUiState())
        private set
    private var loadJob: Job? = null
    private var nextAutomaticLoad = Instant.MIN

    init { load(forceRefresh = false) }

    fun selectDate(date: LocalDate) {
        if (date in uiState.dates) uiState = uiState.copy(selectedDate = date)
    }
    fun selectActivity(activity: ActivityType) { uiState = uiState.selectActivity(activity) }
    fun openLocation(id: String) { uiState = uiState.openLocation(id) }
    fun closeLocation() { uiState = uiState.copy(openedLocationId = null) }
    fun refresh() { load(forceRefresh = true) }

    fun onVisibleTick() {
        uiState = uiState.atTime(clock.instant())
        if (!clock.instant().isBefore(nextAutomaticLoad)) load(forceRefresh = false)
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, message = null)
            try {
                val forecasts = repository.load(forceRefresh)
                val sources = forecasts.flatMap { it.beach.sources }
                val failed = forecasts.any { it.beach.errors.isNotEmpty() } || sources.any { it.refreshFailed }
                nextAutomaticLoad = if (failed) clock.instant().plus(ForecastCache.TTL) else
                    sources.minOfOrNull { it.fetchedAt.plus(ForecastCache.TTL) }
                        ?: clock.instant().plus(ForecastCache.TTL)
                uiState = uiState.copy(forecasts = forecasts,
                    message = if (forecasts.all { it.weather.hours.isEmpty() })
                        "Couldn't load forecasts. Try Refresh." else null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                nextAutomaticLoad = clock.instant().plus(ForecastCache.TTL)
                uiState = uiState.copy(message = "Couldn't refresh forecasts. Please try again.")
            } finally {
                uiState = uiState.copy(isLoading = false).atTime(clock.instant())
            }
        }
    }
}
