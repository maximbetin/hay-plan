package com.mbk.hayplan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.DayPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Scored outlooks for one date and activity; cards and details share these exact objects. */
data class DayPlan(
    val date: LocalDate,
    val activity: ActivityType,
    val now: LocalDateTime,
    val outlooks: Map<String, ActivityOutlook>,
)

/** The seven-day chart for the opened location. */
data class WeeklyPlan(
    val locationId: String,
    val activity: ActivityType,
    val now: LocalDateTime,
    val outlooks: List<DatedOutlook>,
)

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
    val plan: DayPlan? = null,
    val weekly: WeeklyPlan? = null,
) {
    val opened: LocationForecast? get() = forecasts.find { it.location.id == openedLocationId }
    val planDate: LocalDate get() = selectedDate ?: now.toLocalDate()

    fun openLocation(id: String): HayPlanUiState =
        if (forecasts.any { it.location.id == id }) copy(openedLocationId = id) else this

    fun selectActivity(selected: ActivityType): HayPlanUiState =
        copy(activity = selected)

    fun atTime(instant: Instant): HayPlanUiState {
        val localNow = LocalDateTime.ofInstant(instant, LocationCatalog.zone)
        val today = localNow.toLocalDate()
        val hours = forecasts.flatMap { it.weather.hours }
        val availableDates = hours.map { it.time.toLocalDate() }
            .distinct().filter { !it.isBefore(today) }.sorted()
        // Nothing left to plan today once every daylight hour has passed, so start on the next date.
        val daylightLeftToday = hours.any { it.time.toLocalDate() == today && it.isDaylight && !it.time.isBefore(localNow) }
        val defaultDate = availableDates.firstOrNull { daylightLeftToday || it != today } ?: availableDates.firstOrNull()
        return copy(now = localNow, nowInstant = instant, dates = availableDates,
            selectedDate = selectedDate?.takeIf { it in availableDates } ?: defaultDate)
    }

    /** True when [plan] or [weekly] no longer describe the current selection. */
    val needsPlanning: Boolean get() = forecasts.isNotEmpty() && (
        plan == null || plan.date != planDate || plan.activity != activity || plan.now != now ||
            (openedLocationId != null && (weekly == null || weekly.locationId != openedLocationId ||
                weekly.activity != activity || weekly.now != now)))

    /** Runs the scoring for the current selection. Pure and synchronous: call it off the main thread. */
    fun planned(): HayPlanUiState {
        if (!needsPlanning) return this
        val date = planDate
        val plan = plan?.takeIf { it.date == date && it.activity == activity && it.now == now }
            ?: DayPlan(date, activity, now, forecasts.associate { forecast ->
                forecast.location.id to DayPlanner.forDate(forecast.forActivity(activity).hours, date, now, activity)
            })
        val weekly = opened?.let { location ->
            weekly?.takeIf { it.locationId == location.location.id && it.activity == activity && it.now == now }
                ?: WeeklyPlan(location.location.id, activity, now,
                    sevenDayOutlook(location.forActivity(activity).hours, dates, now, activity))
        }
        return copy(plan = plan, weekly = weekly)
    }
}

class HayPlanViewModel(
    private val repository: ForecastRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var uiState by mutableStateOf(HayPlanUiState())
        private set
    private var loadJob: Job? = null
    private var planJob: Job? = null
    private var nextAutomaticLoad = Instant.MIN

    init { load(forceRefresh = false) }

    fun selectDate(date: LocalDate) {
        if (date in uiState.dates) update(uiState.copy(selectedDate = date))
    }
    fun selectActivity(activity: ActivityType) { update(uiState.selectActivity(activity)) }
    fun openLocation(id: String) { update(uiState.openLocation(id)) }
    fun closeLocation() { update(uiState.copy(openedLocationId = null)) }
    fun refresh() { load(forceRefresh = true) }

    fun onVisibleTick() {
        update(uiState.atTime(clock.instant()))
        if (!clock.instant().isBefore(nextAutomaticLoad)) load(forceRefresh = false)
    }

    /** Publishes the state immediately so controls respond, then scores the new selection in the background. */
    private fun update(state: HayPlanUiState) {
        uiState = state
        if (!state.needsPlanning) return
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val planned = withContext(planningDispatcher) { state.planned() }
            // The user may have moved on meanwhile; a stale plan is simply replaced by the next pass.
            update(uiState.copy(plan = planned.plan, weekly = planned.weekly))
        }
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, message = null)
            try {
                val forecasts = repository.load(forceRefresh)
                val sources = forecasts.flatMap { it.weather.sources + it.beach.sources }.distinct()
                val failed = forecasts.any { it.weather.errors.isNotEmpty() || it.beach.errors.isNotEmpty() } ||
                    sources.any { it.refreshFailed }
                nextAutomaticLoad = if (failed) clock.instant().plus(RETRY_DELAY) else
                    sources.minOfOrNull { it.fetchedAt.plus(ForecastCache.TTL) }
                        ?: clock.instant().plus(ForecastCache.TTL)
                val message = if (forecasts.all { it.weather.hours.isEmpty() && it.beach.hours.isEmpty() })
                    "Couldn't load forecasts. Try Refresh." else null
                // Score before publishing so loaded forecasts never appear without their cards.
                val instant = clock.instant()
                val planned = withContext(planningDispatcher) {
                    uiState.copy(forecasts = forecasts, message = message).atTime(instant).planned()
                }
                // Re-derive from the live state in case the selection changed while scoring ran.
                update(uiState.copy(forecasts = forecasts, message = message,
                    plan = planned.plan, weekly = planned.weekly).atTime(instant))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                nextAutomaticLoad = clock.instant().plus(ForecastCache.TTL)
                update(uiState.copy(message = "Couldn't refresh forecasts. Please try again.").atTime(clock.instant()))
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    companion object {
        val RETRY_DELAY: Duration = Duration.ofMinutes(15)
    }
}
