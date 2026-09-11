package com.mbk.hayplan.ui

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
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
import androidx.core.content.edit
import com.mbk.hayplan.R
import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import com.mbk.hayplan.notification.DailyNotificationPreferences
import com.mbk.hayplan.notification.DailyNotificationSettings
import com.mbk.hayplan.notification.DailyPlanScheduler
import com.mbk.hayplan.ui.theme.HayPlanTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HayPlanApp() {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("settings", 0) }
    var language by remember {
        mutableStateOf(if (preferences.contains("language"))
            AppLanguage.fromCode(preferences.getString("language", null)) else AppLanguage.fromSystem())
    }
    var rankingMode by remember {
        mutableStateOf(RankingMode.fromCode(preferences.getString("ranking_mode", null)))
    }
    var dailyNotification by remember { mutableStateOf(DailyNotificationPreferences.read(context)) }
    var notificationsAllowed by remember { mutableStateOf(canPostNotifications(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = canPostNotifications(context)
        if (granted) {
            val updated = DailyNotificationPreferences.setEnabled(context, true)
            dailyNotification = updated
            DailyPlanScheduler.replace(context, updated)
        }
    }
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
                notificationsAllowed = canPostNotifications(context)
                delay(60_000)
            }
        }
    }
    LaunchedEffect(Unit) {
        DailyPlanScheduler.ensure(context, dailyNotification)
    }
    CompositionLocalProvider(LocalUiStrings provides UiStrings(language)) {
        HayPlanScreen(
            state = model.uiState,
            onDateSelected = model::selectDate,
            onActivitySelected = model::selectActivity,
            onLocationSelected = model::openLocation,
            onBack = model::closeLocation,
            onRefresh = model::refresh,
            language = language,
            rankingMode = rankingMode,
            dailyNotification = dailyNotification,
            notificationsAllowed = notificationsAllowed,
            onRankingModeSelected = { selected ->
                preferences.edit { putString("ranking_mode", selected.code) }
                rankingMode = selected
            },
            onDailyNotificationEnabled = { enabled ->
                if (!enabled) {
                    val updated = DailyNotificationPreferences.setEnabled(context, false)
                    dailyNotification = updated
                    DailyPlanScheduler.replace(context, updated)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!canPostNotifications(context)) {
                    val updated = DailyNotificationPreferences.setEnabled(context, true)
                    dailyNotification = updated
                    DailyPlanScheduler.replace(context, updated)
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } else {
                    val updated = DailyNotificationPreferences.setEnabled(context, true)
                    dailyNotification = updated
                    DailyPlanScheduler.replace(context, updated)
                }
            },
            onDailyNotificationTimeSelected = { time ->
                val updated = DailyNotificationPreferences.setTime(context, time)
                dailyNotification = updated
                DailyPlanScheduler.replace(context, updated)
            },
            onLanguageSelected = { selected ->
                preferences.edit { putString("language", selected.code) }
                language = selected
            },
        )
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
    rankingMode: RankingMode = RankingMode.WHOLE_DAY,
    dailyNotification: DailyNotificationSettings = DailyNotificationSettings(),
    notificationsAllowed: Boolean = true,
    onRankingModeSelected: (RankingMode) -> Unit = {},
    onDailyNotificationEnabled: (Boolean) -> Unit = {},
    onDailyNotificationTimeSelected: (LocalTime) -> Unit = {},
    onLanguageSelected: (AppLanguage) -> Unit = {},
) {
    val strings = LocalUiStrings.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationTime = dailyNotification.time.format(SETTINGS_TIME)
    val notificationSubtitle = when {
        notificationsAllowed -> localizedString(R.string.daily_notification_around, notificationTime)
        dailyNotification.enabled -> localizedString(R.string.notification_blocked)
        else -> localizedString(R.string.notification_permission_required)
    }
    val showTimePicker = {
        showSettings = false
        TimePickerDialog(context, { _, hour, minute ->
            onDailyNotificationTimeSelected(LocalTime.of(hour, minute))
        }, dailyNotification.time.hour, dailyNotification.time.minute, true).show()
    }
    val opened = state.opened
    val date = state.selectedDate ?: state.now.toLocalDate()
    val remaining = date == state.now.toLocalDate()
    val precision = scorePrecision(date, state.now.toLocalDate())
    val period = if (remaining) "Remaining daylight" else "Daylight overall"
    val weeklyOutlook = remember(opened, state.dates, state.now, state.activity) {
        opened?.let {
            sevenDayOutlook(it.forActivity(state.activity).hours, state.dates, state.now, state.activity)
        }.orEmpty()
    }
    // Cards and details share these exact objects, including the selected best window.
    val outlooks = remember(state.forecasts, date, state.now, state.activity) {
        state.forecasts.associate { forecast ->
            forecast.location.id to DayPlanner.forDate(
                forecast.forActivity(state.activity).hours, date, state.now, state.activity)
        }
    }
    val ranked = remember(state.forecasts, outlooks, rankingMode, precision) {
        rankLocations(state.forecasts, outlooks, rankingMode,
            coarseScores = precision != ScorePrecision.EXACT)
    }
    val sections = remember(ranked) { locationSections(ranked) }
    val daylightFinished = daylightHasEnded(date, state.now, outlooks.values)
    val tomorrow = state.dates.firstOrNull { it.isAfter(date) }
    var showAll by rememberSaveable(date, state.activity, rankingMode) { mutableStateOf(false) }
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
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = localizedString(R.string.back))
                }
                Text(opened?.location?.name ?: localizedString(R.string.app_name),
                    Modifier.weight(1f).semantics { heading() },
                    style = if (opened == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = localizedString(R.string.settings))
                    }
                    DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(localizedString(R.string.daily_notification))
                                    Text(notificationSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = { Switch(checked = dailyNotification.enabled, onCheckedChange = null) },
                            onClick = {
                                showSettings = false
                                onDailyNotificationEnabled(!dailyNotification.enabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(localizedString(R.string.daily_notification_time, notificationTime)) },
                            onClick = showTimePicker,
                        )
                        HorizontalDivider()
                        Text(localizedString(R.string.language), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_refresh), contentDescription = localizedString(R.string.refresh))
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
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(strings(it), Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            LazyColumn(state = if (opened == null) overviewScroll else detailScroll,
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                forecastConfidenceLabel(date, state.now.toLocalDate())?.let { confidence -> item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(strings(confidence), Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                if (opened == null && state.forecasts.isNotEmpty()) item {
                    Text(localizedString(R.string.comfort_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (opened == null && state.forecasts.isNotEmpty() && !daylightFinished) item {
                    RankingSelector(rankingMode, onRankingModeSelected)
                }
                if (state.forecasts.isEmpty()) item {
                    Text(strings(if (state.isLoading) "Loading forecasts…" else "No forecasts available. Try Refresh."))
                }
                if (opened == null) {
                    if (daylightFinished) item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(localizedString(R.string.no_daylight_remains),
                                    Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(localizedString(R.string.choose_tomorrow),
                                    style = MaterialTheme.typography.bodyMedium)
                                tomorrow?.let { next ->
                                    TextButton(onClick = { onDateSelected(next) }) {
                                        Text(localizedString(R.string.view_tomorrow))
                                    }
                                }
                            }
                        }
                    }
                    if (!daylightFinished) {
                        if (sections.main.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(localizedString(R.string.main_towns), Modifier.semantics { heading() },
                                    style = MaterialTheme.typography.labelLarge)
                                UpdatedLabel(state.forecasts.flatMap { it.forActivity(state.activity).sources }
                                    .minOfOrNull { it.fetchedAt })
                            }
                        }
                        items(sections.main, key = { it.location.id }) { forecast ->
                            TownCard(forecast, outlooks.getValue(forecast.location.id), state.activity,
                                state.nowInstant, precision) { onLocationSelected(forecast.location.id) }
                        }
                        if (sections.allOthers.isNotEmpty()) {
                            item { Text(localizedString(R.string.best_other_locations),
                                Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge) }
                            items(if (showAll) sections.allOthers else sections.topOthers,
                                key = { it.location.id }) { forecast ->
                                TownCard(forecast, outlooks.getValue(forecast.location.id), state.activity,
                                    state.nowInstant, precision) { onLocationSelected(forecast.location.id) }
                            }
                        }
                        if (sections.allOthers.size > OTHER_LOCATION_LIMIT) item {
                            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (showAll) localizedString(R.string.show_top_ten) else localizedPlural(
                                    R.plurals.show_all_other_locations, sections.allOthers.size,
                                    sections.allOthers.size))
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
                            forecastContextLabel(opened.location, state.activity, date, language),
                            opened.location.coast != null, precision,
                            weeklyOutlook, date, state.now.toLocalDate(), onDateSelected)
                    }
                }
                item {
                    val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) {
                    Text("Open-Meteo · CC BY 4.0", style = MaterialTheme.typography.bodySmall)
                }
                }
            }
        }
    }
}

private fun canPostNotifications(context: android.content.Context): Boolean {
    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permissionGranted && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
}

private val SETTINGS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun RankingSelector(selected: RankingMode, onSelected: (RankingMode) -> Unit) {
    val strings = LocalUiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(localizedString(R.string.rank_other_locations), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RankingMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(strings.rankingMode(mode), Modifier.padding(vertical = 4.dp)) },
                    modifier = Modifier.weight(1f),
                )
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
                     now: Instant, scorePrecision: ScorePrecision, onOpen: () -> Unit) {
    val strings = LocalUiStrings.current
    val data = forecast.forActivity(activity)
    val summary = remember(outlook, data.hours, forecast.location.coast) {
        dayWeatherSummary(outlook, data.hours, forecast.location.coast != null)
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = outlook.day?.score?.let { BorderStroke(1.dp, currentRatingColor(it).copy(alpha = 0.35f)) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(forecast.location.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("${localizedString(R.string.hourly)} ›", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            CardReferenceLabel(forecast.location, activity, outlook)
            val primaryPeriod = primaryWarningPeriod(outlook.warningPeriods)
            val cardWarning = primaryPeriod?.warning ?: outlook.day?.warnings?.let(::primaryWarning)
            CompactRatingValue(outlook.day?.rating, outlook.day?.score, average = true,
                showExactScore = scorePrecision == ScorePrecision.EXACT,
                severePeriod = cardWarning?.priority == 3)
            if (outlook.day == null) Text(strings.dayUnavailable(outlook.dayUnavailableReason), style = MaterialTheme.typography.bodySmall)
            cardWarning?.let { warning ->
                val warningText = primaryPeriod?.let(strings::warningPeriod) ?: strings(warning)
                val warningDescription = localizedString(R.string.warning_description, warningText)
                Text(warningText, Modifier.semantics { contentDescription = warningDescription },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning.priority >= 3) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            cardConditions(summary, activity, forecast.location.coast != null, strings.language)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            val window = outlook.bestWindow
            if (window == null) Text(strings.windowUnavailable(outlook.windowUnavailableReason), style = MaterialTheme.typography.bodySmall)
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedString(R.string.best_three_hours), Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val windowSummary = when (scorePrecision) {
                        ScorePrecision.EXACT -> "${timeRange(window.start, window.end)} · ${window.score}/100"
                        ScorePrecision.BAND -> "${timeRange(window.start, window.end)} · ${strings.rating(window.rating)}"
                        ScorePrecision.HIDDEN -> strings.rating(window.rating)
                    }
                    Text(windowSummary,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = currentRatingColor(window.score))
                }
            }
            DataNotice(data, now)
        }
    }
}

@Composable
private fun CompactRatingValue(rating: Rating?, score: Int?, average: Boolean = false,
                               showExactScore: Boolean = true, severePeriod: Boolean = false) {
    val strings = LocalUiStrings.current
    val color = score?.let { currentRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rating?.let { if (average) strings.averageRating(it, severePeriod) else strings.rating(it) }
            ?: localizedString(R.string.unavailable), Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = color)
        if (showExactScore) {
            Surface(shape = RoundedCornerShape(999.dp),
                color = score?.let { currentRatingContainerColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant) {
                Text(score?.let { "$it/100" } ?: "—", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun CardReferenceLabel(location: HayPlanLocation, activity: ActivityType, outlook: ActivityOutlook) {
    val strings = LocalUiStrings.current
    val label = when {
        activity == ActivityType.BEACH && location.coast == null -> strings("Inland estimate · no beach")
        activity == ActivityType.BEACH -> strings.seaSource(location.coast!!.name, outlook.marineCoverage)
        location.weatherReference != null -> strings.weatherSource(location.weatherReference)
        else -> null
    }
    label?.let {
        Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (activity == ActivityType.BEACH && location.coast == null)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeatherReferenceLabel(location: HayPlanLocation) {
    val strings = LocalUiStrings.current
    location.weatherReference?.let {
        Text(strings.weatherReference(it), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoastalReferenceLabel(location: HayPlanLocation) {
    val strings = LocalUiStrings.current
    location.coast?.let {
        Text(strings.seaReference(it.name), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataNotice(data: ActivityForecastData, now: Instant) {
    val strings = LocalUiStrings.current
    val warnings = buildList<Pair<String, Boolean>> {
        addAll(data.errors.map { strings(it) to true })
        if (data.sources.any { it.refreshFailed }) add("Refresh failed · saved forecast" to false)
        if (data.sources.any { !ForecastCache.isFresh(it.fetchedAt, now) }) add("Forecast may be outdated" to false)
        if (data.sources.any { it.persistenceFailed }) add("Couldn't save forecast" to false)
    }
    warnings.forEach { (message, error) ->
        val text = strings(message)
        val description = localizedString(R.string.notice_description, text)
        Text(text, Modifier.semantics { contentDescription = description },
            style = MaterialTheme.typography.bodySmall,
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
        Text(strings.updated(it.atZone(LocationCatalog.zone)
            .format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ENGLISH))),
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
