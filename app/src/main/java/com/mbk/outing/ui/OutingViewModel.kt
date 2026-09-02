package com.mbk.outing.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbk.outing.data.*
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
    val selectedBeachIds: Map<String, String> = emptyMap(),
    val loadingBeaches: Set<String> = emptySet(),
    val now: LocalDateTime = LocalDateTime.now(LocationCatalog.zone),
    val nowInstant: Instant = Instant.now(),
    val isLoading: Boolean = true,
    val message: String? = null,
) {
    fun beachId(location: OutingLocation): String? = location.beach(selectedBeachIds[location.id])?.id
    fun beachLoading(location: OutingLocation): Boolean = "${location.id}/${beachId(location)}" in loadingBeaches
}

class OutingViewModel(
    private val repository: ForecastRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    var uiState by mutableStateOf(OutingUiState())
        private set
    private var loadJob: Job? = null
    private val beachJobs = mutableMapOf<String, Job>()
    private var nextAutomaticLoad = Instant.MIN

    init { load(forceRefresh = false) }

    fun selectDate(date: LocalDate) {
        if (date in uiState.dates) uiState = uiState.copy(selectedDate = date)
    }

    fun selectActivity(activity: ActivityType) { uiState = uiState.copy(activity = activity) }
    fun openLocation(id: String) { uiState = uiState.copy(openedLocationId = id) }
    fun closeLocation() { uiState = uiState.copy(openedLocationId = null) }

    fun selectBeach(locationId: String, beachId: String) {
        val forecast = uiState.forecasts.find { it.location.id == locationId } ?: return
        val beach = forecast.location.beaches.find { it.id == beachId } ?: return
        uiState = uiState.copy(selectedBeachIds = uiState.selectedBeachIds + (locationId to beachId))
        val data = forecast.beaches[beachId]
        if (data != null && data.errors.isEmpty() && data.sources.isNotEmpty() &&
            data.sources.all { ForecastCache.isFresh(it.fetchedAt, clock.instant()) }) return
        val key = "$locationId/$beachId"
        if (beachJobs[key]?.isActive == true) return
        beachJobs[key] = viewModelScope.launch {
            uiState = uiState.copy(loadingBeaches = uiState.loadingBeaches + key)
            val result = try {
                repository.loadBeach(beach)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ActivityForecastData(errors = listOf("Couldn't load this beach. Try Refresh."))
            } finally {
                uiState = uiState.copy(loadingBeaches = uiState.loadingBeaches - key)
                beachJobs.remove(key)
            }
            // A late response stays attached to its own beach, never the new selection.
            uiState = uiState.copy(forecasts = uiState.forecasts.map {
                if (it.location.id == locationId) it.copy(beaches = it.beaches + (beachId to result)) else it
            })
            updateTime()
        }
    }

    /** Refresh updates town weather and the currently selected beach, not every catalog entry. */
    fun refresh() { load(forceRefresh = true) }

    fun onVisibleTick() {
        updateTime()
        if (!clock.instant().isBefore(nextAutomaticLoad)) load(forceRefresh = false)
    }

    private fun updateTime() {
        val instant = clock.instant()
        val now = LocalDateTime.ofInstant(instant, LocationCatalog.zone)
        val dates = uiState.forecasts.flatMap { it.hiking.hours + it.beaches.values.flatMap { data -> data.hours } }
            .map { it.time.toLocalDate() }.distinct().filter { !it.isBefore(now.toLocalDate()) }.sorted()
        uiState = uiState.copy(
            now = now,
            nowInstant = instant,
            dates = dates,
            selectedDate = uiState.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
        )
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true || beachJobs.values.any { it.isActive }) return
        loadJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, message = null)
            try {
                val forecasts = repository.load(forceRefresh, uiState.selectedBeachIds)
                val sources = forecasts.flatMap { it.hiking.sources + it.beaches.values.flatMap { data -> data.sources } }
                val errors = forecasts.flatMap { it.hiking.errors + it.beaches.values.flatMap { data -> data.errors } }
                val failed = errors.isNotEmpty() || sources.any { it.refreshFailed }
                nextAutomaticLoad = if (failed) clock.instant().plus(ForecastCache.TTL) else
                    sources.minOfOrNull { it.fetchedAt.plus(ForecastCache.TTL) }
                        ?: clock.instant().plus(ForecastCache.TTL)
                val previous = uiState.forecasts.associateBy { it.location.id }
                uiState = uiState.copy(
                    forecasts = forecasts.map {
                        it.copy(beaches = previous[it.location.id]?.beaches.orEmpty() + it.beaches)
                    },
                    message = if (forecasts.all { it.hiking.hours.isEmpty() && it.beaches.values.all { data -> data.hours.isEmpty() } })
                        "Couldn't load forecasts. Try Refresh." else null,
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
