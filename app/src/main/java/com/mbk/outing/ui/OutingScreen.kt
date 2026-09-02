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
import androidx.compose.ui.graphics.Color
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
    OutingScreen(model.uiState, model::selectDate, model::selectActivity,
        model::openLocation, model::closeLocation, model::refresh)
}

@Composable
fun OutingScreen(
    state: OutingUiState,
    onDateSelected: (LocalDate) -> Unit = {},
    onActivitySelected: (ActivityType) -> Unit = {},
    onLocationSelected: (String) -> Unit = {},
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
                Column(Modifier.weight(1f)) {
                    Text(opened?.location?.name ?: "Outing",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(opened?.location?.region ?: "Asturias",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(if (state.isLoading) "Updating…" else "Refresh")
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(it, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (state.dates.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (state.isLoading) "Loading your forecast…" else "No forecast available.",
                        Modifier.padding(24.dp))
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
                LaunchedEffect(date, state.activity, opened?.location?.id) { scrollState.scrollToItem(0) }
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
                            Text("Long-range outlook · timings may change",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (opened == null) {
                        items(state.forecasts, key = { it.location.id }) { forecast ->
                            LocationCard(forecast, date, state) { onLocationSelected(forecast.location.id) }
                        }
                    } else {
                        item { LocationDetails(opened, date, state) }
                    }
                    item {
                        Text("Daylight only · times in Asturias",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val uriHandler = LocalUriHandler.current
                        Text("Open-Meteo · CC BY 4.0",
                            modifier = Modifier.padding(top = 8.dp).clickable { uriHandler.openUri("https://open-meteo.com/") },
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
private fun LocationCard(forecast: LocationForecast, date: LocalDate, state: OutingUiState, onOpen: () -> Unit) {
    val data = forecast.forActivity(state.activity)
    val outlook = remember(data, date, state.now, state.activity) {
        DayPlanner.forDate(data?.hours.orEmpty(), date, state.now, state.activity)
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(forecast.location.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(subject(forecast, state.activity), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
            if (data == null) {
                Text("No beach assigned to this location.")
            } else {
                DataNotice(data, state.nowInstant)
                DaySummary(outlook, period(date, state))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WindowSummary(outlook)
                if (outlook.day?.warnings?.isNotEmpty() == true) {
                    Text("Limiting conditions during part of the day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UpdatedLabel(data)
            }
        }
    }
}

@Composable
private fun LocationDetails(forecast: LocationForecast, date: LocalDate, state: OutingUiState) {
    val data = forecast.forActivity(state.activity)
    val outlook = remember(data, date, state.now, state.activity) {
        DayPlanner.forDate(data?.hours.orEmpty(), date, state.now, state.activity)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(subject(forecast, state.activity), style = MaterialTheme.typography.titleMedium)
        if (data == null) {
            Text("No beach assigned to this location.")
        } else {
            DataNotice(data, state.nowInstant)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DaySummary(outlook, period(date, state))
                    outlook.day?.let { day ->
                        Text("${day.goodHours} of ${day.assessedHours} daylight hours rated Good or better.",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("The day rating averages every assessed hour—not just the best window.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (day.warnings.isNotEmpty()) {
                            Text("During part of the day", style = MaterialTheme.typography.labelLarge)
                            day.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WindowSummary(outlook)
                    outlook.bestWindow?.let { window ->
                        Text("${window.rating.label} during this window",
                            color = ratingColor(window.score), fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        window.factors.forEach { FactorRow(it) }
                        window.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Text(if (state.activity == ActivityType.BEACH)
                "A combined beach-day estimate, including swimming conditions. Not a safety assessment: check beach flags and lifeguard advice."
                else "Town-level weather for a hike or a walk. No route is assumed; elevation, terrain and trail conditions are not assessed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            data.sources.forEach { source ->
                Text("${source.label} fetched ${formatInstant(source.fetchedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DaySummary(outlook: ActivityOutlook, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val day = outlook.day
        Text(day?.rating?.label ?: "Unavailable", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = day?.let { ratingColor(it.score) } ?: MaterialTheme.colorScheme.onSurface)
        if (day == null) Text(outlook.dayUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WindowSummary(outlook: ActivityOutlook) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Best window", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val window = outlook.bestWindow
        if (window == null) Text(outlook.windowUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
        else {
            Text(formatWindow(window), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (outlook.day == null) Text("Based on available hours only.", style = MaterialTheme.typography.bodySmall)
            if (window.score < 40) Text("Even this window is not recommended.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DataNotice(data: ActivityForecastData, now: Instant) {
    val warnings = buildList {
        addAll(data.errors)
        if (data.sources.any { it.refreshFailed }) add("Refresh failed. Using the saved forecast.")
        if (data.sources.any { !ForecastCache.isFresh(it.fetchedAt, now) }) add("Saved data is over an hour old or its timestamp cannot be verified.")
        if (data.sources.any { it.persistenceFailed }) add("Forecast could not be saved on this device.")
    }
    warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun UpdatedLabel(data: ActivityForecastData) {
    data.sources.minOfOrNull { it.fetchedAt }?.let {
        Text("Forecast: ${formatInstant(it)} · 1 h cache",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FactorRow(factor: FactorResult) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(when (factor.outcome) {
            FactorOutcome.POSITIVE -> "✓"
            FactorOutcome.MIXED -> "~"
            FactorOutcome.NEGATIVE -> "!"
        }, color = when (factor.outcome) {
            FactorOutcome.POSITIVE -> ratingColor(90)
            FactorOutcome.MIXED -> ratingColor(50)
            FactorOutcome.NEGATIVE -> ratingColor(10)
        })
        Column(Modifier.weight(1f)) {
            Text(factor.label)
            Text(factor.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(factor.value, fontWeight = FontWeight.SemiBold)
    }
}

private fun subject(forecast: LocationForecast, activity: ActivityType) = when (activity) {
    ActivityType.BEACH -> "Beach · ${forecast.location.mainBeach?.name ?: "Not configured"}"
    ActivityType.HIKING -> "Hiking · town and surrounding area"
}

private fun period(date: LocalDate, state: OutingUiState) =
    if (date == state.now.toLocalDate()) "Rest of today" else "Day overall"

private fun dayLabel(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
}

private fun formatWindow(window: BestWindow): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return "${window.start.format(formatter)}–${window.end.format(formatter)}"
}

private fun formatInstant(instant: Instant): String =
    instant.atZone(LocationCatalog.zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH))

private fun ratingColor(score: Int) = when {
    score >= 80 -> Color(0xFF087A63)
    score >= 60 -> Color(0xFF39734B)
    score >= 40 -> Color(0xFF8A6500)
    else -> Color(0xFFA34235)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun OutingScreenPreview() {
    val hours = SampleForecast.tomorrow()
    val date = hours.first().time.toLocalDate()
    val data = ActivityForecastData(hours)
    OutingTheme {
        OutingScreen(OutingUiState(
            forecasts = listOf(LocationForecast(LocationCatalog.locations.first(), data, data)),
            dates = listOf(date), selectedDate = date, isLoading = false,
        ))
    }
}
