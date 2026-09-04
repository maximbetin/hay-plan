package com.mbk.hayplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import com.mbk.hayplan.ui.theme.HayPlanTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HayPlanApp() {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        viewModelFactory {
            initializer {
                HayPlanViewModel(ForecastRepository(OpenMeteoClient(
                    ForecastCache(File(context.noBackupFilesDir, "forecasts")),
                )))
            }
        }
    }
    val model: HayPlanViewModel = viewModel(factory = factory)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                model.onVisibleTick()
                delay(60_000)
            }
        }
    }
    HayPlanScreen(model.uiState, model::selectDate, model::selectActivity,
        model::openLocation, model::closeLocation, model::refresh)
}

@Composable
fun HayPlanScreen(
    state: HayPlanUiState,
    onDateSelected: (LocalDate) -> Unit = {},
    onActivitySelected: (ActivityType) -> Unit = {},
    onLocationSelected: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val opened = state.opened
    val date = state.selectedDate ?: state.now.toLocalDate()
    val remaining = date == state.now.toLocalDate()
    val period = if (remaining) "Remaining daylight" else "Daylight overall"
    // Cards and details share these exact objects, including the selected best window.
    val outlooks = remember(state.forecasts, date, state.now, state.activity) {
        state.forecasts.associate { forecast ->
            forecast.location.id to DayPlanner.forDate(
                forecast.forActivity(state.activity).hours, date, state.now, state.activity)
        }
    }
    val ranked = remember(state.forecasts, outlooks) { rankLocations(state.forecasts, outlooks) }
    var showAll by rememberSaveable(date, state.activity) { mutableStateOf(false) }
    val overviewScroll = rememberLazyListState()
    val detailScroll = rememberLazyListState()
    LaunchedEffect(date, state.activity) { overviewScroll.scrollToItem(0); detailScroll.scrollToItem(0) }
    LaunchedEffect(opened?.location?.id) { detailScroll.scrollToItem(0) }
    BackHandler(enabled = opened != null, onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (opened != null) TextButton(onClick = onBack) { Text("‹ Back") }
                Text(opened?.location?.name ?: "Hay Plan", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(if (state.isLoading) "Updating…" else "Refresh")
                }
            }
            DateStrip(state, onDateSelected)
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityType.entries.forEach { activity ->
                    FilterChip(selected = state.activity == activity,
                        onClick = { onActivitySelected(activity) },
                        label = { Text(activity.label, Modifier.padding(vertical = 5.dp)) },
                        modifier = Modifier.weight(1f))
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(it, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            LazyColumn(state = if (opened == null) overviewScroll else detailScroll,
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (date.isAfter(state.now.toLocalDate().plusDays(6))) item {
                    Text("Long-range outlook", style = MaterialTheme.typography.bodySmall)
                }
                if (state.forecasts.isEmpty()) item {
                    Text(if (state.isLoading) "Loading forecasts…" else "No forecasts available. Try Refresh.")
                }
                if (opened == null) {
                    if (ranked.isNotEmpty()) item {
                        Text(if (showAll) "All locations · highest daylight score first"
                            else "Top ${minOf(5, ranked.size)} · highest daylight score first",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    items(if (showAll) ranked else ranked.take(5), key = { it.location.id }) { forecast ->
                        TownCard(forecast, outlooks.getValue(forecast.location.id), state.activity,
                            period, state.nowInstant) { onLocationSelected(forecast.location.id) }
                    }
                    if (ranked.size > 5) item {
                        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAll) "Show top 5" else "Show all ${ranked.size} locations")
                        }
                    }
                } else {
                    item {
                        WeatherReferenceLabel(opened.location)
                        if (state.activity == ActivityType.BEACH) CoastalReferenceLabel(opened.location)
                        UpdatedLabel(opened.forActivity(state.activity))
                        DataNotice(opened.forActivity(state.activity), state.nowInstant)
                    }
                    item {
                        OutlookDetails(outlooks.getValue(opened.location.id), opened.forActivity(state.activity).hours,
                            period, "${opened.location.id}/$date/${state.activity}", remaining,
                            forecastContextLabel(opened.location, state.activity, date), opened.location.coast != null)
                    }
                }
                item {
                    val uriHandler = LocalUriHandler.current
                    Text("Open-Meteo · CC BY 4.0",
                        Modifier.clickable { uriHandler.openUri("https://open-meteo.com/") },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DateStrip(state: HayPlanUiState, onDateSelected: (LocalDate) -> Unit) {
    val scroll = rememberLazyListState()
    LaunchedEffect(state.selectedDate) {
        val index = state.dates.indexOf(state.selectedDate)
        if (index >= 0 && scroll.layoutInfo.visibleItemsInfo.none { it.index == index }) scroll.animateScrollToItem(index)
    }
    LazyRow(state = scroll, contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.dates, key = { it.toString() }) { date ->
            FilterChip(selected = date == state.selectedDate, onClick = { onDateSelected(date) },
                label = { Text(formatDate(date), Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.SemiBold) })
        }
    }
}

@Composable
private fun TownCard(forecast: LocationForecast, outlook: ActivityOutlook, activity: ActivityType,
                     period: String, now: Instant, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(forecast.location.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Hourly ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            WeatherReferenceLabel(forecast.location)
            if (activity == ActivityType.BEACH) CoastalReferenceLabel(forecast.location)
            Text(period, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RatingValue(outlook.day?.rating, outlook.day?.score)
            if (outlook.day == null) Text(outlook.dayUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
            if (activity == ActivityType.BEACH) Text(outlook.marineCoverage.label,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val window = outlook.bestWindow
            if (window == null) Text(outlook.windowUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
            else {
                Text("Best 3 hours · ${timeRange(window.start, window.end)}",
                    style = MaterialTheme.typography.titleMedium)
                Text("${window.rating.label} · ${window.score}/100" +
                    if (activity == ActivityType.BEACH && window.marineCoverage != outlook.marineCoverage)
                        " · ${window.marineCoverage.label}" else "", style = MaterialTheme.typography.bodyMedium)
                if (window.score < 40) Text("Not recommended", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            DataNotice(forecast.forActivity(activity), now)
            UpdatedLabel(forecast.forActivity(activity))
        }
    }
}

@Composable
private fun WeatherReferenceLabel(location: HayPlanLocation) {
    location.weatherReference?.let {
        Text("Weather reference: $it", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoastalReferenceLabel(location: HayPlanLocation) {
    location.coast?.let {
        Text("Sea reference: ${it.name}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataNotice(data: ActivityForecastData, now: Instant) {
    val warnings = buildList {
        addAll(data.errors)
        if (data.sources.any { it.refreshFailed }) add("Refresh failed · saved forecast")
        if (data.sources.any { !ForecastCache.isFresh(it.fetchedAt, now) }) add("Forecast may be outdated")
        if (data.sources.any { it.persistenceFailed }) add("Couldn't save forecast")
    }
    warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun UpdatedLabel(data: ActivityForecastData) {
    data.sources.minOfOrNull { it.fetchedAt }?.let {
        Text("Updated ${it.atZone(LocationCatalog.zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ENGLISH))}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun HayPlanScreenPreview() {
    val date = LocalDate.of(2026, 9, 3)
    val data = ActivityForecastData(SampleForecast.forDate(date))
    val forecasts = LocationCatalog.locations.map { location ->
        val weather = data.copy(hours = data.hours.map { it.copy(seaTemperatureC = null, waveHeightM = null) })
        LocationForecast(location, weather, if (location.coast != null) data else weather)
    }
    HayPlanTheme {
        HayPlanScreen(HayPlanUiState(forecasts = forecasts, dates = listOf(date), selectedDate = date,
            now = date.atStartOfDay(), isLoading = false))
    }
}
