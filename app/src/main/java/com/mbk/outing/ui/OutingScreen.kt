package com.mbk.outing.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mbk.outing.data.*
import com.mbk.outing.domain.*
import com.mbk.outing.ui.theme.OutingTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OutingApp() {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        viewModelFactory {
            initializer {
                OutingViewModel(ForecastRepository(OpenMeteoClient(
                    ForecastCache(File(context.noBackupFilesDir, "forecasts")),
                )))
            }
        }
    }
    val model: OutingViewModel = viewModel(factory = factory)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                model.onVisibleTick()
                delay(60_000)
            }
        }
    }
    OutingScreen(
        state = model.uiState,
        onDateSelected = model::selectDate,
        onActivitySelected = model::selectActivity,
        onLocationSelected = model::openLocation,
        onBeachSelected = model::selectBeach,
        onBack = model::closeLocation,
        onRefresh = model::refresh,
    )
}

@Composable
fun OutingScreen(
    state: OutingUiState,
    onDateSelected: (LocalDate) -> Unit = {},
    onActivitySelected: (ActivityType) -> Unit = {},
    onLocationSelected: (String) -> Unit = {},
    onBeachSelected: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val opened = state.forecasts.find { it.location.id == state.openedLocationId }
    BackHandler(enabled = opened != null, onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (opened != null) TextButton(onClick = onBack) { Text("‹ Back") }
                Text(opened?.location?.name ?: "Outing", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !state.isLoading && state.loadingBeaches.isEmpty()) {
                    Text(if (state.isLoading) "Updating…" else "Refresh")
                }
            }
            if (state.isLoading || state.loadingBeaches.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(it, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (state.dates.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (state.isLoading) "Loading forecast…" else "No forecast available.", Modifier.padding(24.dp))
                }
            } else {
                DateStrip(state, onDateSelected)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityType.entries.forEach { activity ->
                        FilterChip(
                            selected = state.activity == activity,
                            onClick = { onActivitySelected(activity) },
                            label = { Text(activity.label, Modifier.padding(vertical = 5.dp)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val date = state.selectedDate ?: state.dates.first()
                val scrollState = rememberLazyListState()
                LaunchedEffect(date, state.activity, opened?.location?.id, state.selectedBeachIds) {
                    scrollState.scrollToItem(0)
                }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (date.isAfter(state.now.toLocalDate().plusDays(6))) {
                            Text("Long-range outlook", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (opened == null) {
                        items(state.forecasts, key = { it.location.id }) { forecast ->
                            LocationCard(forecast, date, state,
                                onOpen = { onLocationSelected(forecast.location.id) },
                                onBeachSelected = { onBeachSelected(forecast.location.id, it) })
                        }
                    } else {
                        item {
                            val beachId = state.beachId(opened.location)
                            val data = opened.forActivity(state.activity, beachId)
                            val outlook = remember(data, date, state.now, state.activity) {
                                DayPlanner.forDate(data?.hours.orEmpty(), date, state.now, state.activity)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.activity == ActivityType.BEACH) {
                                    BeachSelector(opened.location, beachId, !state.isLoading) {
                                        onBeachSelected(opened.location.id, it)
                                    }
                                }
                                if (data == null && state.beachLoading(opened.location)) {
                                    Text("Loading forecast…")
                                } else if (data == null) {
                                    Text("No forecast available.")
                                } else {
                                    DataNotice(data, state.nowInstant)
                                    OutlookDetails(outlook, period(date, state), "${date}/${state.activity}/$beachId")
                                    UpdatedLabel(data)
                                }
                            }
                        }
                    }
                    item {
                        val uriHandler = LocalUriHandler.current
                        Text("Open-Meteo · CC BY 4.0",
                            modifier = Modifier.clickable { uriHandler.openUri("https://open-meteo.com/") },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStrip(state: OutingUiState, onDateSelected: (LocalDate) -> Unit) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(state.selectedDate) {
        val index = state.dates.indexOf(state.selectedDate)
        if (index >= 0 && scrollState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            scrollState.animateScrollToItem(index)
        }
    }
    LazyRow(state = scrollState, contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.dates, key = { it.toString() }) { date ->
            FilterChip(
                selected = date == state.selectedDate,
                onClick = { onDateSelected(date) },
                label = {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(dayLabel(date, state.now.toLocalDate()), fontWeight = FontWeight.SemiBold)
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
                            style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        }
    }
}

@Composable
private fun LocationCard(
    forecast: LocationForecast,
    date: LocalDate,
    state: OutingUiState,
    onOpen: () -> Unit,
    onBeachSelected: (String) -> Unit,
) {
    val beachId = state.beachId(forecast.location)
    val data = forecast.forActivity(state.activity, beachId)
    val outlook = remember(data, date, state.now, state.activity) {
        DayPlanner.forDate(data?.hours.orEmpty(), date, state.now, state.activity)
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(forecast.location.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.activity == ActivityType.BEACH) {
                BeachSelector(forecast.location, beachId, !state.isLoading, onBeachSelected)
            }
            if (data == null && state.beachLoading(forecast.location)) Text("Loading forecast…")
            else if (data == null) Text("No forecast available.")
            else {
                DataNotice(data, state.nowInstant)
                DayOverview(outlook, period(date, state))
            }
        }
    }
}

@Composable
private fun BeachSelector(location: OutingLocation, selectedId: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (location.beaches.isEmpty()) {
        Text("No beaches configured.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("${location.beach(selectedId)?.name}  ▾", style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            location.beaches.forEach { beach ->
                DropdownMenuItem(
                    text = { Text(beach.name, fontWeight = if (beach.id == selectedId) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { expanded = false; onSelect(beach.id) },
                    trailingIcon = if (beach.id == selectedId) { { Text("✓") } } else null,
                )
            }
        }
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
        Text("Updated ${it.atZone(LocationCatalog.zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH))}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun period(date: LocalDate, state: OutingUiState) =
    if (date == state.now.toLocalDate()) "Rest of today" else "Day overall"

private fun dayLabel(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun OutingScreenPreview() {
    val hours = SampleForecast.tomorrow()
    val date = hours.first().time.toLocalDate()
    val location = LocationCatalog.locations.first()
    val data = ActivityForecastData(hours)
    OutingTheme {
        OutingScreen(OutingUiState(
            forecasts = listOf(LocationForecast(location, data, mapOf(location.mainBeach!!.id to data))),
            dates = listOf(date), selectedDate = date, isLoading = false,
        ))
    }
}
