package com.mbk.hayplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mbk.hayplan.R
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
    val preferences = remember(context) { context.getSharedPreferences("settings", 0) }
    var language by remember { mutableStateOf(AppLanguage.fromCode(preferences.getString("language", null))) }
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
    CompositionLocalProvider(LocalUiStrings provides UiStrings(language)) {
        HayPlanScreen(model.uiState, model::selectDate, model::selectActivity,
            model::openLocation, model::closeLocation, model::refresh, language) { selected ->
            preferences.edit().putString("language", selected.code).apply()
            language = selected
        }
    }
}

@Composable
fun HayPlanScreen(
    state: HayPlanUiState,
    onDateSelected: (LocalDate) -> Unit = {},
    onActivitySelected: (ActivityType) -> Unit = {},
    onLocationSelected: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    language: AppLanguage = AppLanguage.ENGLISH,
    onLanguageSelected: (AppLanguage) -> Unit = {},
) {
    val strings = LocalUiStrings.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
    val coastalRanked = remember(ranked) { ranked.filter { it.location.coast != null } }
    val inlandRanked = remember(ranked) { ranked.filter { it.location.coast == null } }
    val primaryRanked = if (state.activity == ActivityType.BEACH) coastalRanked else ranked
    val daylightFinished = daylightHasEnded(date, state.now, outlooks.values)
    val tomorrow = state.dates.firstOrNull { it.isAfter(date) }
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
                if (opened != null) IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = strings("Back"))
                }
                Text(opened?.location?.name ?: "Hay Plan", Modifier.weight(1f),
                    style = if (opened == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = strings("Settings"))
                    }
                    DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                        AppLanguage.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(strings(option.displayName)) },
                                leadingIcon = { RadioButton(selected = language == option, onClick = null) },
                                onClick = {
                                    onLanguageSelected(option)
                                    showSettings = false
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(strings(if (state.isLoading) "Updating…" else "Refresh"))
                }
            }
            DateStrip(state, onDateSelected)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityType.entries.forEach { activity ->
                    FilterChip(selected = state.activity == activity,
                        onClick = { onActivitySelected(activity) },
                        label = { Text(strings.activity(activity), Modifier.padding(vertical = 5.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (activity == ActivityType.BEACH)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = if (activity == ActivityType.BEACH)
                                MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.weight(1f))
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(strings(it), Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            LazyColumn(state = if (opened == null) overviewScroll else detailScroll,
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (date.isAfter(state.now.toLocalDate().plusDays(6))) item {
                    Text(strings("Long-range outlook"), style = MaterialTheme.typography.bodySmall)
                }
                if (state.forecasts.isEmpty()) item {
                    Text(strings(if (state.isLoading) "Loading forecasts…" else "No forecasts available. Try Refresh."))
                }
                if (opened == null) {
                    if (daylightFinished) item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings("No daylight remains today"), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(strings("Choose tomorrow to see useful rankings."),
                                    style = MaterialTheme.typography.bodyMedium)
                                tomorrow?.let { next ->
                                    TextButton(onClick = { onDateSelected(next) }) { Text(strings("View tomorrow")) }
                                }
                            }
                        }
                    }
                    if (!daylightFinished) {
                        if (ranked.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(strings(if (showAll && state.activity != ActivityType.BEACH)
                                    "All locations"
                                    else if (showAll) "Coastal locations"
                                    else if (state.activity == ActivityType.BEACH)
                                        "Best coastal locations"
                                    else "Best locations"),
                                    style = MaterialTheme.typography.labelLarge)
                                UpdatedLabel(state.forecasts.flatMap { it.forActivity(state.activity).sources }
                                    .minOfOrNull { it.fetchedAt })
                            }
                        }
                        items(if (showAll && state.activity == ActivityType.BEACH) coastalRanked
                            else if (showAll) ranked else primaryRanked.take(5), key = { it.location.id }) { forecast ->
                            TownCard(forecast, outlooks.getValue(forecast.location.id), state.activity,
                                state.nowInstant) { onLocationSelected(forecast.location.id) }
                        }
                        if (showAll && state.activity == ActivityType.BEACH && inlandRanked.isNotEmpty()) {
                            item { Text(strings("Inland alternatives"), style = MaterialTheme.typography.labelLarge) }
                            items(inlandRanked, key = { it.location.id }) { forecast ->
                                TownCard(forecast, outlooks.getValue(forecast.location.id), state.activity,
                                    state.nowInstant) { onLocationSelected(forecast.location.id) }
                            }
                        }
                        if (ranked.size > 5) item {
                            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                                Text(strings(if (showAll) "Show top 5" else "Show all ${ranked.size} locations"))
                            }
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
                            forecastContextLabel(opened.location, state.activity, date, language), opened.location.coast != null)
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
    val strings = LocalUiStrings.current
    val scroll = rememberLazyListState()
    LaunchedEffect(state.selectedDate) {
        val index = state.dates.indexOf(state.selectedDate)
        if (index >= 0 && scroll.layoutInfo.visibleItemsInfo.none { it.index == index }) scroll.animateScrollToItem(index)
    }
    LazyRow(state = scroll, contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.dates, key = { it.toString() }) { date ->
            FilterChip(selected = date == state.selectedDate, onClick = { onDateSelected(date) },
                label = { Text(formatDate(date, state.now.toLocalDate(), strings.language),
                    Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ))
        }
    }
}

@Composable
private fun TownCard(forecast: LocationForecast, outlook: ActivityOutlook, activity: ActivityType,
                     now: Instant, onOpen: () -> Unit) {
    val strings = LocalUiStrings.current
    val data = forecast.forActivity(activity)
    val summary = remember(outlook, data.hours, forecast.location.coast) {
        dayWeatherSummary(outlook, data.hours, forecast.location.coast != null)
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = outlook.day?.score?.let { BorderStroke(1.dp, ratingColor(it).copy(alpha = 0.20f)) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(forecast.location.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("${strings("Hourly")} ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            CardReferenceLabel(forecast.location, activity, outlook)
            CompactRatingValue(outlook.day?.rating, outlook.day?.score)
            if (outlook.day == null) Text(strings(outlook.dayUnavailableReason.orEmpty()), style = MaterialTheme.typography.bodySmall)
            outlook.day?.takeIf { it.score < 60 }?.warnings?.firstOrNull()?.let {
                Text(strings(it), style = MaterialTheme.typography.bodySmall, color = ratingColor(outlook.day.score),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            cardConditions(summary, activity, forecast.location.coast != null)?.let {
                Text(strings(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            val window = outlook.bestWindow
            if (window == null) Text(strings(outlook.windowUnavailableReason.orEmpty()), style = MaterialTheme.typography.bodySmall)
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings("Best 3 hours"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${timeRange(window.start, window.end)} · ${window.score}/100",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = ratingColor(window.score))
                }
            }
            DataNotice(data, now)
        }
    }
}

@Composable
private fun CompactRatingValue(rating: Rating?, score: Int?) {
    val strings = LocalUiStrings.current
    val color = score?.let(::ratingColor) ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rating?.let(strings::rating) ?: strings("Unavailable"), Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = color)
        Surface(shape = RoundedCornerShape(999.dp),
            color = score?.let(::ratingContainerColor) ?: MaterialTheme.colorScheme.surfaceVariant) {
            Text(score?.let { "$it/100" } ?: "—", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun CardReferenceLabel(location: HayPlanLocation, activity: ActivityType, outlook: ActivityOutlook) {
    val strings = LocalUiStrings.current
    val label = when {
        activity == ActivityType.BEACH && location.coast == null -> strings("Inland estimate · no beach")
        activity == ActivityType.BEACH -> strings("Sea: ${location.coast!!.name} · ${strings.coverage(outlook.marineCoverage)}")
        location.weatherReference != null -> strings("Weather: ${location.weatherReference}")
        else -> null
    }
    label?.let {
        Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (activity == ActivityType.BEACH && location.coast == null)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeatherReferenceLabel(location: HayPlanLocation) {
    val strings = LocalUiStrings.current
    location.weatherReference?.let {
        Text(strings("Weather reference: $it"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoastalReferenceLabel(location: HayPlanLocation) {
    val strings = LocalUiStrings.current
    location.coast?.let {
        Text(strings("Sea reference: ${it.name}"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataNotice(data: ActivityForecastData, now: Instant) {
    val strings = LocalUiStrings.current
    val warnings = buildList<Pair<String, Boolean>> {
        addAll(data.errors.map { it to true })
        if (data.sources.any { it.refreshFailed }) add("Refresh failed · saved forecast" to false)
        if (data.sources.any { !ForecastCache.isFresh(it.fetchedAt, now) }) add("Forecast may be outdated" to false)
        if (data.sources.any { it.persistenceFailed }) add("Couldn't save forecast" to false)
    }
    warnings.forEach { (message, error) ->
        Text(strings(message), style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun UpdatedLabel(data: ActivityForecastData) {
    UpdatedLabel(data.sources.minOfOrNull { it.fetchedAt })
}

@Composable
private fun UpdatedLabel(updatedAt: Instant?) {
    val strings = LocalUiStrings.current
    updatedAt?.let {
        Text(strings("Updated ${it.atZone(LocationCatalog.zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ENGLISH))}"),
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
