package com.mbk.outing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbk.outing.domain.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface DetailTarget {
    data object Day : DetailTarget
    data object Window : DetailTarget
    data class Hour(val time: LocalDateTime) : DetailTarget
}

@Composable
internal fun DayOverview(
    outlook: ActivityOutlook,
    label: String,
    summary: DayWeatherSummary?,
    remainingToday: Boolean,
    onDayClick: () -> Unit,
    onWindowClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = "Show day score details", onClick = onDayClick)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Score details ›", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            RatingValue(outlook.day?.rating, outlook.day?.score)
            Text(outlook.day?.let { daylightAverageLabel(it.assessedHours, remainingToday) }
                ?: outlook.dayUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary?.let { Text(it.headline, style = MaterialTheme.typography.bodyMedium) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = "Show best three-hour forecast", onClick = onWindowClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Best 3 hours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val window = outlook.bestWindow
                if (window == null) Text(outlook.windowUnavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
                else {
                    Text(timeRange(window.start, window.end), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${window.rating.label} · ${window.score}/100", style = MaterialTheme.typography.bodyMedium,
                        color = ratingColor(window.score))
                    if (window.score < 40) Text("Not recommended", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text("›", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutlookDetails(
    outlook: ActivityOutlook,
    hours: List<HourlyConditions>,
    label: String,
    selectionKey: String,
    remainingToday: Boolean,
    showHourly: Boolean = true,
) {
    var target by remember(selectionKey) { mutableStateOf<DetailTarget?>(null) }
    val summary = remember(outlook, hours) { dayWeatherSummary(outlook, hours) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Box(if (showHourly) Modifier.padding(18.dp) else Modifier) {
                DayOverview(outlook, label, summary, remainingToday, onDayClick = { target = DetailTarget.Day },
                    onWindowClick = { target = DetailTarget.Window })
            }
        }
        if (showHourly) {
            Text("Daylight hours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            outlook.hourly.forEach { hour ->
                HourRow(hour, outlook.bestWindow) { target = DetailTarget.Hour(hour.time) }
            }
            if (outlook.hourly.isEmpty()) Text("No daylight hours remaining or available.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    val selected = target
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { target = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (selected) {
                    DetailTarget.Day -> DayInspection(outlook, label, summary)
                    DetailTarget.Window -> WindowInspection(outlook)
                    is DetailTarget.Hour -> HourInspection(outlook.hourly.find { it.time == selected.time }, selected.time)
                }
                SuitabilityNote(outlook.activity)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HourRow(hour: HourlyAssessment, best: BestWindow?, onClick: () -> Unit) {
    val time = hour.time.toLocalTime()
    val isBest = best != null && !time.isBefore(best.start) && time.isBefore(best.end)
    val score = hour.evaluation?.score
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (isBest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(time.format(TIME), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(score?.let { ratingFor(it).label } ?: "Unavailable", style = MaterialTheme.typography.bodyMedium)
                    if (best?.start == time) Text("Best 3 hours", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(score?.let { "$it/100" } ?: "—", fontWeight = FontWeight.SemiBold)
                Text("›", Modifier.clearAndSetSemantics { })
            }
            if (score != null) LinearProgressIndicator(
                progress = { score / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics { },
                color = ratingColor(score), trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun DayInspection(outlook: ActivityOutlook, label: String, summary: DayWeatherSummary?) {
    Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val day = outlook.day
    RatingValue(day?.rating, day?.score)
    summary?.let {
        Text("Daylight conditions", style = MaterialTheme.typography.titleMedium)
        it.values.forEach { value ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(value.label, Modifier.weight(1f))
                Text(value.value, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (day == null) {
        Text(outlook.dayUnavailableReason.orEmpty())
        Text("${outlook.hourly.count { it.evaluation != null }}/${outlook.hourly.size} hours rated")
        return
    }
    Text("${day.goodHours}/${day.assessedHours} daylight hours Good or better")
    day.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    HorizontalDivider()
    var showCalculation by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showCalculation = !showCalculation }) {
        Text(if (showCalculation) "Hide score calculation ▴" else "How is this score calculated? ▾")
    }
    if (!showCalculation) return
    Text("Each daylight hour is scored first. The day score averages those scores after any reductions.",
        style = MaterialTheme.typography.bodySmall)
    Text(Rating.entries.joinToString(" · ") { "${it.label} ${ratingRange(it)}" },
        style = MaterialTheme.typography.bodySmall)
    Text("Average hourly points", style = MaterialTheme.typography.titleMedium)
    day.factors.forEach { factor ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(factor.label, Modifier.weight(1f))
            Text("${decimal(factor.averagePoints)} / ${factor.maximumPoints}", fontWeight = FontWeight.Medium)
        }
    }
    if (day.limitationPoints > 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Average score reduction", Modifier.weight(1f))
            Text("−${decimal(day.limitationPoints)}", color = MaterialTheme.colorScheme.error)
        }
        Text("Reductions apply only to the affected hours.", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WindowInspection(outlook: ActivityOutlook) {
    val window = outlook.bestWindow
    Text("Best 3 hours", style = MaterialTheme.typography.titleMedium)
    Text(window?.let { timeRange(it.start, it.end) } ?: "Unavailable",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (window == null) { Text(outlook.windowUnavailableReason.orEmpty()); return }
    RatingValue(window.rating, window.score)
    // Values summarize the period; the score itself uses its individual hourly scores.
    window.factors.forEach { FactorRow(it, showPoints = false) }
    window.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun HourInspection(hour: HourlyAssessment?, time: LocalDateTime) {
    Text(timeRange(time.toLocalTime(), time.plusHours(1).toLocalTime()),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val evaluation = hour?.evaluation
    RatingValue(evaluation?.let { ratingFor(it.score) }, evaluation?.score)
    if (evaluation == null) { Text("Forecast data is incomplete for this hour."); return }
    evaluation.factors.forEach { FactorRow(it, showPoints = true) }
    val deduction = evaluation.factors.sumOf { it.points } - evaluation.score
    if (deduction > 0) Text("Score reduced by $deduction points", color = MaterialTheme.colorScheme.error)
    evaluation.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SuitabilityNote(activity: ActivityType) {
    Text("Personal weather-suitability score, not forecast confidence or safety.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (activity == ActivityType.HIKING) "Town weather, not trail or elevation conditions."
        else "Local shelter, beach flags and currents are not assessed.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactorRow(factor: FactorResult, showPoints: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(factor.label)
            if (!showPoints) Text(factor.detail, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(factor.value, fontWeight = FontWeight.SemiBold)
            if (showPoints && factor.maximumPoints > 0) Text("${factor.points}/${factor.maximumPoints} points",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingValue(rating: Rating?, score: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(rating?.label ?: "Unavailable", Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = score?.let(::ratingColor) ?: MaterialTheme.colorScheme.onSurface)
        Text(score?.let { "$it/100" } ?: "—", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun timeRange(start: LocalTime, end: LocalTime) = "${start.format(TIME)}–${end.format(TIME)}"
private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun ratingRange(rating: Rating) = when (rating) {
    Rating.POOR -> "0–19"
    Rating.FAIR -> "20–39"
    Rating.GOOD -> "40–59"
    Rating.VERY_GOOD -> "60–79"
    Rating.EXCELLENT -> "80–100"
}
private fun ratingColor(score: Int) = when {
    score >= 80 -> Color(0xFF087A63)
    score >= 60 -> Color(0xFF39734B)
    score >= 40 -> Color(0xFF8A6500)
    else -> Color(0xFFA34235)
}
