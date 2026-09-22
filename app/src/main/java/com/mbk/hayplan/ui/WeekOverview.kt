package com.mbk.hayplan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbk.hayplan.R
import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.DayUnavailableReason
import com.mbk.hayplan.domain.Rating
import com.mbk.hayplan.domain.primaryWarning
import com.mbk.hayplan.domain.primaryWarningPeriod
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every location across the week at a glance: one headline answer, then a row of day cells per place.
 * A cell opens that place on that day; the rows keep the day overview's order rules.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WeekOverview(
    state: HayPlanUiState,
    activity: ActivityType,
    scroll: LazyListState,
    onDayOpened: (String, LocalDate) -> Unit,
    onActivitySelected: (ActivityType) -> Unit,
    footer: LazyListScope.() -> Unit,
) {
    val week = state.week?.takeIf { it.activity == activity }
    val today = state.now.toLocalDate()
    val outlooks = week?.outlooks.orEmpty()
    // After sunset today is blank everywhere, so its column would only push the useful days aside.
    val dates = week?.dates.orEmpty().filterNot { date ->
        outlooks.values.all { days ->
            days.find { it.date == date }?.outlook?.dayUnavailableReason == DayUnavailableReason.NoHoursRemaining
        }
    }
    val sections = remember(week) {
        locationSections(rankLocationsForWeek(state.forecasts.filter { it.location.id in outlooks }, outlooks))
    }
    val highlight = remember(sections) { weekHighlight(sections.all, outlooks) }
    var showAll by rememberSaveable(activity) { mutableStateOf(false) }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.forecasts.isEmpty()) item {
            Text(localizedString(if (state.isLoading) R.string.loading_forecasts else R.string.no_forecasts))
        }
        if (week != null && dates.isNotEmpty()) {
            item(key = "highlight") {
                WeekHighlightCard(highlight, activity, today, onDayOpened, onActivitySelected)
            }
            stickyHeader(key = "days") { WeekHeader(dates, today) }
            if (sections.main.isNotEmpty()) item { WeekSectionHeading(localizedString(R.string.main_towns)) }
            items(sections.main, key = { it.location.id }) { forecast ->
                WeekRow(forecast, outlooks[forecast.location.id].orEmpty(), dates, today, onDayOpened)
            }
            if (sections.allOthers.isNotEmpty()) item {
                WeekSectionHeading(localizedString(R.string.best_other_locations))
            }
            items(if (showAll) sections.allOthers else sections.topOthers, key = { it.location.id }) { forecast ->
                WeekRow(forecast, outlooks[forecast.location.id].orEmpty(), dates, today, onDayOpened)
            }
            if (sections.allOthers.size > OTHER_LOCATION_LIMIT) item {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAll) localizedString(R.string.show_top_ten) else localizedPlural(
                        R.plurals.show_all_other_locations, sections.allOthers.size, sections.allOthers.size))
                }
            }
            if (dates.first() == today) item {
                Text(localizedString(R.string.today_uses_remaining_daylight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        footer()
    }
}

/** The answer before the grid: where and when is best, or that nothing is worth it and the other activity may be. */
@Composable
private fun WeekHighlightCard(
    highlight: WeekHighlight?,
    activity: ActivityType,
    today: LocalDate,
    onDayOpened: (String, LocalDate) -> Unit,
    onActivitySelected: (ActivityType) -> Unit,
) {
    val strings = LocalUiStrings.current
    val day = highlight?.outlook?.day
    if (highlight == null || day == null || day.rating < Rating.GOOD) {
        val other = ActivityType.entries.first { it != activity }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(localizedString(R.string.week_nothing_good, strings.activity(activity)), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                TextButton(onClick = { onActivitySelected(other) }) {
                    Text(localizedString(R.string.week_try_activity, strings.activity(other)))
                }
            }
        }
        return
    }
    Card(onClick = { onDayOpened(highlight.location.location.id, highlight.date) },
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(localizedString(R.string.week_best), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${highlight.location.location.name} · ${formatDate(highlight.date, today, strings.language)}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                RatingValue(day.rating, day.score,
                    showExactScore = scorePrecision(highlight.date, today) == ScorePrecision.EXACT, compact = true)
            }
            val primaryPeriod = primaryWarningPeriod(highlight.outlook.warningPeriods)
            (primaryPeriod?.warning ?: day.warnings.let(::primaryWarning))?.let { warning ->
                WarningLine(primaryPeriod?.let(strings::warningPeriod) ?: strings(warning), warning)
            }
        }
    }
}

@Composable
private fun WeekSectionHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}

/** Day labels pinned above the rows so every column stays readable while scrolling. */
@Composable
private fun WeekHeader(dates: List<LocalDate>, today: LocalDate) {
    val language = LocalUiStrings.current.language
    val locale = if (language == AppLanguage.SPANISH) Locale.forLanguageTag("es-ES") else Locale.ENGLISH
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        dates.forEach { date ->
            val isToday = date == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix("."),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WeekRow(
    forecast: LocationForecast,
    days: List<DatedOutlook>,
    dates: List<LocalDate>,
    today: LocalDate,
    onDayOpened: (String, LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(forecast.location.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            dates.forEach { date ->
                WeekCell(forecast.location.name, date, today, days.find { it.date == date },
                    Modifier.weight(1f)) { onDayOpened(forecast.location.id, date) }
            }
        }
    }
}

/** Colour carries the glance; the score or band and a "!" for severe warnings carry it without colour. */
@Composable
private fun WeekCell(
    place: String,
    date: LocalDate,
    today: LocalDate,
    point: DatedOutlook?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val day = point?.outlook?.day
    val exact = scorePrecision(date, today) == ScorePrecision.EXACT
    val warning = point?.outlook?.severeGraphWarning()
    val scoreDescription = day?.let { strings.rating(it.rating) + if (exact) ", ${it.score}/100" else "" }
        ?: localizedString(R.string.unavailable)
    val description = localizedString(R.string.week_cell_description, place,
        formatDate(date, today, strings.language), scoreDescription,
        warning?.let { localizedString(R.string.weekly_outlook_warning_suffix, strings(it)) }.orEmpty())
    Surface(onClick = onClick, modifier = modifier.height(48.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(10.dp),
        color = day?.let { currentRatingContainerColor(it.score) } ?: MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().clearAndSetSemantics { }, verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day?.let { if (exact) "${it.score}" else scoreBand(it.score) } ?: "—",
                style = if (exact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                color = day?.let { currentRatingColor(it.score) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            if (warning != null) Text("!", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
    }
}

private val CELL_GAP = 4.dp
