package com.mbk.hayplan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
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
    selectedPlace: LocationForecast?,
    scroll: LazyListState,
    onDayOpened: (String, LocalDate) -> Unit,
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
    val selectedDays = selectedPlace?.let { outlooks[it.location.id] }.orEmpty()
    val best = bestDay(selectedDays, today)
    val highlight = if (selectedPlace != null && best != null)
        WeekHighlight(selectedPlace, best.date, best.outlook) else null
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectedPlace == null) item {
            Text(localizedString(if (state.isLoading) R.string.loading_forecasts else R.string.no_forecasts))
        }
        if (week != null && dates.isNotEmpty() && selectedPlace != null) {
            item(key = "highlight") {
                Card(onClick = { highlight?.let { onDayOpened(it.location.location.id, it.date) } },
                    enabled = highlight != null, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    HighlightRow(activity, highlight, today, showWarning = true)
                }
            }
            stickyHeader(key = "days") { WeekHeader(dates, today) }
            item(key = "selected-place") {
                WeekRow(selectedPlace, selectedDays, dates, today, false, onDayOpened)
            }
            if (dates.first() == today) item {
                Text(localizedString(R.string.today_uses_remaining_daylight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Text(localizedString(R.string.week_grid_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        footer()
    }
}

/**
 * The answer before the grid, for both activities: the selected one leads and opens its best day; the other
 * sits below as a one-tap switch, so "no beach this week, but a fine walk" reads at a glance.
 */
@Composable
private fun WeekHighlights(
    highlight: WeekHighlight?,
    other: Pair<ActivityType, WeekHighlight?>?,
    activity: ActivityType,
    today: LocalDate,
    onDayOpened: (String, LocalDate) -> Unit,
    onActivitySelected: (ActivityType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localizedString(R.string.week_best), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val good = highlight?.takeIf { (it.outlook.day?.rating ?: Rating.POOR) >= Rating.GOOD }
        Card(onClick = { good?.let { onDayOpened(it.location.location.id, it.date) } },
            enabled = good != null,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            HighlightRow(activity, good, today, showWarning = true)
        }
        other?.let { (otherActivity, otherHighlight) ->
            Surface(onClick = { onActivitySelected(otherActivity) }, shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                HighlightRow(otherActivity,
                    otherHighlight?.takeIf { (it.outlook.day?.rating ?: Rating.POOR) >= Rating.GOOD }, today,
                    showWarning = false)
            }
        }
    }
}

/** One activity's week in a line: its icon, then the best place and day with its rating, or that nothing is Good. */
@Composable
private fun HighlightRow(activity: ActivityType, highlight: WeekHighlight?, today: LocalDate, showWarning: Boolean) {
    val strings = LocalUiStrings.current
    val day = highlight?.outlook?.day
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(activity.icon), contentDescription = strings.activity(activity), Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (highlight == null || day == null) {
                Text(localizedString(R.string.week_nothing_good),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                return@Column
            }
            Text("${highlight.location.location.name} · ${formatDate(highlight.date, today, strings.language)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            highlight.outlook.bestWindow?.takeIf { it.rating >= Rating.GOOD }?.let { window ->
                Text("${localizedString(R.string.best_three_hours)} · ${timeRange(window.start, window.end)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (showWarning) {
                val primaryPeriod = primaryWarningPeriod(highlight.outlook.warningPeriods)
                (primaryPeriod?.warning ?: day.warnings.let(::primaryWarning))?.let { warning ->
                    WarningLine(primaryPeriod?.let(strings::warningPeriod) ?: strings(warning), warning)
                }
            }
        }
        if (highlight != null && day != null) RatingValue(day.rating, day.score,
            showExactScore = scorePrecision(highlight.date, today) == ScorePrecision.EXACT, compact = true)
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
    noBeach: Boolean,
    onDayOpened: (String, LocalDate) -> Unit,
) {
    Column(Modifier.alpha(if (noBeach) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            dates.forEach { date ->
                WeekCell(forecast.location.name, date, today, days.find { it.date == date },
                    Modifier.weight(1f)) { onDayOpened(forecast.location.id, date) }
            }
        }
    }
}

/**
 * Colour carries the glance. Without colour, sooner days show their score and later days one to five dots for
 * the rating; "!" appears only where a good-looking day hides a severe warning.
 */
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
    val hiddenHazard = warning != null && day != null && day.rating >= Rating.GOOD
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
            val ink = day?.let { currentRatingOnContainerColor(it.score) } ?: MaterialTheme.colorScheme.onSurfaceVariant
            if (day != null && !exact) RatingDots(day.rating, ink)
            else Text(day?.let { "${it.score}" } ?: "—", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, color = ink)
            if (hiddenHazard) Text("!", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** One dot per rating step, so later days compare at a glance without implying an exact score. */
@Composable
private fun RatingDots(rating: Rating, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rating.ordinal + 1) { Box(Modifier.size(5.dp).background(color, CircleShape)) }
    }
}

private val CELL_GAP = 4.dp
