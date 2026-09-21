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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    // Chips follow the selection instantly; content follows the plan, which may lag a frame while scoring runs.
    val plan = state.plan
    val date = plan?.date ?: state.planDate
    val activity = plan?.activity ?: state.activity
    val outlooks = plan?.outlooks.orEmpty()
    val precision = scorePrecision(date, state.now.toLocalDate())
    val overviewScroll = rememberLazyListState()
    LaunchedEffect(date, activity) { overviewScroll.scrollToItem(0) }
    BackHandler(enabled = opened != null, onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = if (opened != null) 6.dp else 18.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
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
                                text = { Text(option.displayName) },
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
                ActivityType.entries.forEach { option ->
                    FilterChip(selected = state.activity == option,
                        onClick = { onActivitySelected(option) },
                        label = { ChipLabel(strings.activity(option)) },
                        colors = choiceChipColors(),
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
            // The detail slides over the overview so a card tap answers at once, and the overview keeps its scroll.
            AnimatedContent(
                targetState = opened?.location?.id,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState != null) slideInHorizontally { it / 3 } + fadeIn() togetherWith fadeOut()
                    else fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
                },
                label = "location",
            ) { openedId ->
                val detail = openedId?.let { id -> state.forecasts.find { it.location.id == id } }
                if (detail == null) OverviewList(state, plan, outlooks, date, activity, precision, rankingMode,
                    overviewScroll, onDateSelected, onLocationSelected, onRankingModeSelected)
                else DetailPane(state, detail, outlooks[detail.location.id], date, activity, precision, language)
            }
        }
    }
}

@Composable
private fun OverviewList(
    state: HayPlanUiState,
    plan: DayPlan?,
    outlooks: Map<String, ActivityOutlook>,
    date: LocalDate,
    activity: ActivityType,
    precision: ScorePrecision,
    rankingMode: RankingMode,
    scroll: LazyListState,
    onDateSelected: (LocalDate) -> Unit,
    onLocationSelected: (String) -> Unit,
    onRankingModeSelected: (RankingMode) -> Unit,
) {
    val strings = LocalUiStrings.current
    val today = state.now.toLocalDate()
    val ranked = remember(plan, rankingMode, precision) {
        if (plan == null) emptyList() else rankLocations(state.forecasts.filter { it.location.id in outlooks },
            outlooks, rankingMode, coarseScores = precision != ScorePrecision.EXACT)
    }
    val sections = remember(ranked) { locationSections(ranked) }
    val daylightFinished = daylightHasEnded(date, state.now, outlooks.values)
    val tomorrow = state.dates.firstOrNull { it.isAfter(date) }
    var showAll by rememberSaveable(date, activity, rankingMode) { mutableStateOf(false) }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        forecastConfidenceLabel(date, today)?.let { confidence -> item {
            Text(strings(confidence), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (state.forecasts.isEmpty()) item {
            Text(localizedString(if (state.isLoading) R.string.loading_forecasts else R.string.no_forecasts))
        }
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
        } else {
            if (sections.main.isNotEmpty()) item {
                SectionHeading(localizedString(R.string.main_towns))
            }
            items(sections.main, key = { it.location.id }) { forecast ->
                TownCard(forecast, outlooks.getValue(forecast.location.id), activity,
                    state.nowInstant, precision) { onLocationSelected(forecast.location.id) }
            }
            if (sections.allOthers.isNotEmpty()) {
                // The ranking choice only orders this section, so it lives under its heading.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeading(localizedString(R.string.best_other_locations))
                        RankingSelector(rankingMode, onRankingModeSelected)
                    }
                }
                items(if (showAll) sections.allOthers else sections.topOthers,
                    key = { it.location.id }) { forecast ->
                    TownCard(forecast, outlooks.getValue(forecast.location.id), activity,
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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.forecasts.isNotEmpty()) {
                    Text(localizedString(R.string.comfort_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UpdatedLabel(state.forecasts
                        .flatMap { it.forActivity(activity).sources }.minOfOrNull { it.fetchedAt })
                    AttributionLink()
                }
            }
        }
    }
}

@Composable
private fun DetailPane(
    state: HayPlanUiState,
    opened: LocationForecast,
    outlook: ActivityOutlook?,
    date: LocalDate,
    activity: ActivityType,
    precision: ScorePrecision,
    language: AppLanguage,
) {
    val strings = LocalUiStrings.current
    val today = state.now.toLocalDate()
    val remaining = date == today
    val coastal = opened.location.coast != null
    val label = if (remaining) R.string.remaining_daylight else R.string.daylight_overall
    val data = opened.forActivity(activity)
    val weeklyOutlook = state.weekly
        ?.takeIf { it.locationId == opened.location.id && it.activity == activity }?.outlooks.orEmpty()
    val target = remember(opened.location.id, date, activity) { mutableStateOf<DetailTarget?>(null) }
    val summary = remember(outlook, data.hours, coastal) { outlook?.let { dayWeatherSummary(it, data.hours, coastal) } }
    val scroll = rememberLazyListState()
    LaunchedEffect(date, activity) { scroll.scrollToItem(0) }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp)) {
        forecastConfidenceLabel(date, today)?.let { confidence -> item {
            Text(strings(confidence), Modifier.padding(bottom = 14.dp), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (outlook != null) {
            val sourceLine = listOfNotNull(
                sourceLabel(opened.location, activity, outlook.marineCoverage, strings),
                data.sources.minOfOrNull { it.fetchedAt }?.let { updatedLabel(it, strings) },
            ).joinToString(" · ").ifEmpty { null }
            outlookDetails(outlook, summary, label, remaining, coastal, precision,
                weeklyOutlook, date, today, sourceLine,
                notices = { DataNotice(data, state.nowInstant) }, target = target)
        }
        item { Box(Modifier.padding(top = 14.dp)) { AttributionLink() } }
    }
    if (outlook != null) DetailSheet(target, outlook, summary, label,
        forecastContextLabel(opened.location, activity, date, language), coastal, precision)
}

@Composable
private fun AttributionLink() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") },
        contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("Open-Meteo · CC BY 4.0", style = MaterialTheme.typography.bodySmall)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localizedString(R.string.rank_by), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        RankingMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { ChipLabel(strings.rankingMode(mode)) },
                colors = choiceChipColors(),
            )
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
                label = { ChipLabel(formatDate(date, state.now.toLocalDate(), strings.language)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ))
        }
    }
}

/** Dates stay neutral because they are position, not a choice; activity and ranking share one selected tint. */
@Composable
private fun choiceChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun ChipLabel(text: String) {
    Text(text, Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}

/** Name and one rating pill, then only what changes a decision: conditions, the best window, a warning. */
@Composable
private fun TownCard(forecast: LocationForecast, outlook: ActivityOutlook, activity: ActivityType,
                     now: Instant, scorePrecision: ScorePrecision, onOpen: () -> Unit) {
    val strings = LocalUiStrings.current
    val data = forecast.forActivity(activity)
    val coastal = forecast.location.coast != null
    val summary = remember(outlook, data.hours, coastal) { dayWeatherSummary(outlook, data.hours, coastal) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(forecast.location.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RatingValue(outlook.day?.rating, outlook.day?.score,
                    showExactScore = scorePrecision == ScorePrecision.EXACT, compact = true)
                Text("›", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (outlook.day == null) Text(strings.dayUnavailable(outlook.dayUnavailableReason),
                style = MaterialTheme.typography.bodySmall)
            cardConditions(summary, activity, coastal, strings.language)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            val window = outlook.bestWindow
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(localizedString(R.string.best_three_hours), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (window == null) Text(strings.windowUnavailable(outlook.windowUnavailableReason),
                    style = MaterialTheme.typography.bodySmall)
                else {
                    val windowSummary = when {
                        // When only the window's hours remain, repeating the identical range and score reads as an error.
                        scorePrecision != ScorePrecision.HIDDEN && windowCoversAllHours(outlook) ->
                            localizedString(R.string.window_all_remaining_daylight)
                        scorePrecision == ScorePrecision.EXACT ->
                            "${timeRange(window.start, window.end)} · ${window.score}"
                        scorePrecision == ScorePrecision.BAND ->
                            "${timeRange(window.start, window.end)} · ${strings.rating(window.rating)}"
                        else -> strings.rating(window.rating)
                    }
                    Text(windowSummary, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = currentRatingColor(window.score))
                }
            }
            val primaryPeriod = primaryWarningPeriod(outlook.warningPeriods)
            (primaryPeriod?.warning ?: outlook.day?.warnings?.let(::primaryWarning))?.let { warning ->
                WarningLine(primaryPeriod?.let(strings::warningPeriod) ?: strings(warning), warning)
            }
            // Only sources a reader could not assume belong on the card; the norm is stated in the detail.
            if (activity == ActivityType.BEACH && (!coastal || outlook.marineCoverage != MarineCoverage.FULL))
                sourceLabel(forecast.location, activity, outlook.marineCoverage, strings)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            DataNotice(data, now)
        }
    }
}

@Composable
internal fun DataNotice(data: ActivityForecastData, now: Instant) {
    val strings = LocalUiStrings.current
    val warnings = buildList<Pair<String, Boolean>> {
        addAll(data.errors.map { strings(it) to true })
        if (data.sources.any { it.refreshFailed }) add(localizedString(R.string.refresh_failed) to false)
        if (data.sources.any { !ForecastCache.isFresh(it.fetchedAt, now) }) add(localizedString(R.string.forecast_outdated) to false)
        if (data.sources.any { it.persistenceFailed }) add(localizedString(R.string.save_failed) to false)
    }
    warnings.forEach { (text, error) ->
        val description = localizedString(R.string.notice_description, text)
        Text(text, Modifier.semantics { contentDescription = description },
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun UpdatedLabel(updatedAt: Instant?) {
    updatedAt?.let {
        Text(updatedLabel(it, LocalUiStrings.current),
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
            now = date.atStartOfDay(), isLoading = false).planned())
    }
}
