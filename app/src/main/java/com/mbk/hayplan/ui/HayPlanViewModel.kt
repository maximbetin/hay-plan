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

/** Up to seven scored days for every location: the week grid and each detail's chart. */
data class WeekPlan(
    val activity: ActivityType,
    val now: LocalDateTime,
    val dates: List<LocalDate>,
    val outlooks: Map<String, List<DatedOutlook>>,
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
    val week: WeekPlan? = null,
    /** The other activity's week, so the grid can say how it compares and switching needs no rescoring. */
    val otherWeek: WeekPlan? = null,
    /** The overview shows the week grid instead of one date's ranking; kept while a detail opened from it is shown. */
    val weekView: Boolean = false,
) {
    val opened: LocationForecast? get() = forecasts.find { it.location.id == openedLocationId }
    val planDate: LocalDate get() = selectedDate ?: now.toLocalDate()

    fun openLocation(id: String): HayPlanUiState =
        if (forecasts.any { it.location.id == id }) copy(openedLocationId = id) else this

    fun selectActivity(selected: ActivityType): HayPlanUiState =
        copy(activity = selected)

    /** A date chip in the overview leaves the week grid; inside a detail it only changes the day shown. */
    fun selectDate(date: LocalDate): HayPlanUiState =
        if (date in dates) copy(selectedDate = date, weekView = weekView && openedLocationId != null) else this

    fun showWeek(): HayPlanUiState = copy(weekView = true, openedLocationId = null)

    /** A week grid cell: that location on that date, returning to the grid on Back. */
    fun openDay(id: String, date: LocalDate): HayPlanUiState =
        if (date in dates) openLocation(id).copy(selectedDate = date) else openLocation(id)

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

    /** True when [plan] or [week] no longer describe the current selection. */
    val needsPlanning: Boolean get() = forecasts.isNotEmpty() && (
        plan == null || plan.date != planDate || plan.activity != activity || plan.now != now ||
            !current(week, activity) || !current(otherWeek, otherActivity))

    private val otherActivity: ActivityType get() = ActivityType.entries.first { it != activity }

    private fun current(plan: WeekPlan?, activity: ActivityType) =
        plan != null && plan.activity == activity && plan.now == now && plan.dates == weekDates()

    private fun weekFor(activity: ActivityType): WeekPlan = listOfNotNull(week, otherWeek)
        .firstOrNull { current(it, activity) }
        ?: WeekPlan(activity, now, weekDates(), forecasts.associate { forecast ->
            forecast.location.id to sevenDayOutlook(forecast.forActivity(activity).hours, weekDates(), now, activity)
        })

    private fun weekDates(): List<LocalDate> = sevenDayDates(dates, now)

    /** Runs the scoring for the current selection. Pure and synchronous: call it off the main thread. */
    fun planned(): HayPlanUiState {
        if (!needsPlanning) return this
        val date = planDate
        val week = weekFor(activity)
        // Days inside the week reuse its outlooks, so cards, grid and details show the exact same objects.
        val plan = plan?.takeIf { it.date == date && it.activity == activity && it.now == now }
            ?: DayPlan(date, activity, now, forecasts.associate { forecast ->
                forecast.location.id to (week.outlooks[forecast.location.id]?.find { it.date == date }?.outlook
                    ?: DayPlanner.forDate(forecast.forActivity(activity).hours, date, now, activity))
            })
        return copy(plan = plan, week = week, otherWeek = weekFor(otherActivity))
    }
}

class HayPlanViewModel(
    private val repository: ForecastRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // The week grid is the first screen: it answers "where and when" before any single day.
    var uiState by mutableStateOf(HayPlanUiState(weekView = true))
        private set
    private var loadJob: Job? = null
    private var planJob: Job? = null
    private var nextAutomaticLoad = Instant.MIN

    init { load(forceRefresh = false) }

    fun selectDate(date: LocalDate) { update(uiState.selectDate(date)) }
    fun showWeek() { update(uiState.showWeek()) }
    fun openDay(id: String, date: LocalDate) { update(uiState.openDay(id, date)) }
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
            update(uiState.copy(plan = planned.plan, week = planned.week, otherWeek = planned.otherWeek))
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
                    plan = planned.plan, week = planned.week, otherWeek = planned.otherWeek).atTime(instant))
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
