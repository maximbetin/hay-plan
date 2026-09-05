package com.mbk.hayplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import com.mbk.hayplan.domain.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    coastal: Boolean,
    onDayClick: () -> Unit,
    onWindowClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = strings("Show day score details"), onClick = onDayClick)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings(label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${strings("Score details")} ›", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            RatingValue(outlook.day?.rating, outlook.day?.score)
            if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, outlook.marineCoverage, strings.language),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings(outlook.day?.let { daylightAverageLabel(it.assessedHours, remainingToday) }
                ?: outlook.dayUnavailableReason.orEmpty()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary?.let { Text(strings(it.headline), style = MaterialTheme.typography.bodyMedium) }
            outlook.day?.takeIf { it.score < 80 }?.warnings?.firstOrNull()?.let {
                Text(strings(it), style = MaterialTheme.typography.bodySmall,
                    color = ratingColor(outlook.day.score))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = strings("Show best three-hour forecast"), onClick = onWindowClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings("Best 3 hours"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val window = outlook.bestWindow
                if (window == null) Text(strings(outlook.windowUnavailableReason.orEmpty()), style = MaterialTheme.typography.bodySmall)
                else {
                    Text(timeRange(window.start, window.end), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${strings.rating(window.rating)} · ${window.score}/100", style = MaterialTheme.typography.bodyMedium,
                        color = ratingColor(window.score), fontWeight = FontWeight.SemiBold)
                    if (outlook.activity == ActivityType.BEACH && coastal && window.marineCoverage != outlook.marineCoverage)
                        Text(beachCoverageLabel(true, window.marineCoverage, strings.language), style = MaterialTheme.typography.bodySmall)
                    if (window.score < 40) Text(strings("Not recommended"), style = MaterialTheme.typography.bodySmall,
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
    contextLabel: String,
    coastal: Boolean,
) {
    val strings = LocalUiStrings.current
    var target by remember(selectionKey) { mutableStateOf<DetailTarget?>(null) }
    val summary = remember(outlook, hours, coastal) { dayWeatherSummary(outlook, hours, coastal) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.padding(18.dp)) {
                DayOverview(outlook, label, summary, remainingToday, coastal, onDayClick = { target = DetailTarget.Day },
                    onWindowClick = { target = DetailTarget.Window })
            }
        }
        Text(strings("Daylight hours"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        outlook.hourly.forEach { hour ->
            HourRow(hour, outlook.bestWindow, outlook.marineCoverage == MarineCoverage.MIXED) {
                target = DetailTarget.Hour(hour.time)
            }
        }
        if (outlook.hourly.isEmpty()) Text(strings("No daylight hours remaining or available."), style = MaterialTheme.typography.bodyMedium)
    }
    val selected = target
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { target = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            // Keep place/activity/date visible even while scrolling a long explanation.
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(contextLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { target = null }) { Text(strings("Close")) }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (selected) {
                    DetailTarget.Day -> DayInspection(outlook, label, summary, coastal)
                    DetailTarget.Window -> WindowInspection(outlook, coastal)
                    is DetailTarget.Hour -> HourInspection(outlook.hourly.find { it.time == selected.time },
                        selected.time, outlook.activity, coastal)
                }
                SuitabilityNote(outlook.activity, coastal)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HourRow(hour: HourlyAssessment, best: BestWindow?, showCoverage: Boolean, onClick: () -> Unit) {
    val strings = LocalUiStrings.current
    val time = hour.time.toLocalTime()
    val isBest = best != null && !time.isBefore(best.start) && time.isBefore(best.end)
    val score = hour.evaluation?.score
    val scoreColor = score?.let(::ratingColor) ?: MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (isBest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(time.format(TIME), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(score?.let { strings.rating(ratingFor(it)) } ?: strings("Unavailable"),
                        style = MaterialTheme.typography.bodyMedium, color = scoreColor)
                    if (showCoverage) hour.evaluation?.let {
                        Text(strings.coverage(it.marineCoverage), style = MaterialTheme.typography.labelSmall)
                    }
                    if (best?.start == time) Text(strings("Best 3 hours"), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(score?.let { "$it/100" } ?: "—", fontWeight = FontWeight.SemiBold, color = scoreColor)
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
private fun DayInspection(outlook: ActivityOutlook, label: String, summary: DayWeatherSummary?, coastal: Boolean) {
    val strings = LocalUiStrings.current
    Text(strings(label), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val day = outlook.day
    RatingValue(day?.rating, day?.score)
    if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, outlook.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    summary?.let {
        Text(strings("Daylight conditions"), style = MaterialTheme.typography.titleMedium)
        it.values.forEach { value ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings(value.label), Modifier.weight(1f))
                Text(strings(value.value), fontWeight = FontWeight.Medium)
            }
        }
    }
    if (day == null) {
        Text(strings(outlook.dayUnavailableReason.orEmpty()))
        Text(strings("${outlook.hourly.count { it.evaluation != null }}/${outlook.hourly.size} hours rated"))
        return
    }
    Text(strings("${day.goodHours}/${day.assessedHours} daylight hours Good or better"))
    day.warnings.forEach { Text(strings(it), style = MaterialTheme.typography.bodySmall) }
    HorizontalDivider()
    var showCalculation by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showCalculation = !showCalculation }) {
        Text(strings(if (showCalculation) "Hide score calculation ▴" else "How is this score calculated? ▾"))
    }
    if (!showCalculation) return
    Text(strings("The day score is the average of the displayed daylight-hour scores, rounded to a whole number. " +
        "Each hour uses its available factors, scales their points to 100, then applies any condition limits. " +
        "Tap an hour to see its inputs and calculation."),
        style = MaterialTheme.typography.bodySmall)
    Text(Rating.entries.joinToString(" · ") { "${strings.rating(it)} ${ratingRange(it)}" },
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun WindowInspection(outlook: ActivityOutlook, coastal: Boolean) {
    val strings = LocalUiStrings.current
    val window = outlook.bestWindow
    Text(strings("Best 3 hours"), style = MaterialTheme.typography.titleMedium)
    Text(window?.let { timeRange(it.start, it.end) } ?: strings("Unavailable"),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (window == null) { Text(strings(outlook.windowUnavailableReason.orEmpty())); return }
    RatingValue(window.rating, window.score)
    if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, window.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    Text(strings("Average of these three hourly scores, with any limits for the whole period applied."),
        style = MaterialTheme.typography.bodySmall)
    // Values summarize the period; the score itself uses its individual hourly scores.
    window.factors.forEach { FactorRow(it, showPoints = false) }
    window.warnings.forEach { Text(strings(it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun HourInspection(hour: HourlyAssessment?, time: LocalDateTime, activity: ActivityType, coastal: Boolean) {
    val strings = LocalUiStrings.current
    Text(timeRange(time.toLocalTime(), time.plusHours(1).toLocalTime()),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val evaluation = hour?.evaluation
    RatingValue(evaluation?.let { ratingFor(it.score) }, evaluation?.score)
    if (evaluation == null) { Text(strings("Forecast data is incomplete for this hour.")); return }
    if (activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, evaluation.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    evaluation.factors.forEach { FactorRow(it, showPoints = true) }
    Text(strings("${evaluation.factors.sumOf { it.points }} / ${evaluation.availablePoints} available points × 100 " +
        "= ${evaluation.pointsBeforeLimits}/100 (rounded)"), style = MaterialTheme.typography.bodySmall)
    val deduction = evaluation.pointsBeforeLimits - evaluation.score
    if (deduction > 0) Text(strings("Score reduced by $deduction points"), color = MaterialTheme.colorScheme.error)
    evaluation.warnings.forEach { Text(strings(it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SuitabilityNote(activity: ActivityType, coastal: Boolean) {
    val strings = LocalUiStrings.current
    Text(strings("Personal weather-suitability score, not forecast confidence or safety."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(strings(if (activity == ActivityType.HIKING) "Town and nearby-area weather, not exact trail or elevation conditions."
        else if (coastal) "Only available sea data is used. Local shelter, beach flags and currents are not assessed."
        else "Weather for outdoor leisure; pool temperatures and river conditions are not assessed."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactorRow(factor: FactorResult, showPoints: Boolean) {
    val strings = LocalUiStrings.current
    val color = factorColor(factor.outcome)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape).clearAndSetSemantics { })
        Column(Modifier.weight(1f)) {
            Text(strings(factor.label))
            if (!showPoints) Text(strings(factor.detail), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(strings(factor.value), fontWeight = FontWeight.SemiBold, color = color)
            if (showPoints && factor.maximumPoints > 0) Text(strings("${factor.points}/${factor.maximumPoints} points"),
                style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
internal fun RatingValue(rating: Rating?, score: Int?) {
    val strings = LocalUiStrings.current
    val color = score?.let(::ratingColor) ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(rating?.let(strings::rating) ?: strings("Unavailable"), Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = color)
        Surface(shape = RoundedCornerShape(999.dp),
            color = score?.let(::ratingContainerColor) ?: MaterialTheme.colorScheme.surfaceVariant) {
            Text(score?.let { "$it/100" } ?: "—", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal fun timeRange(start: LocalTime, end: LocalTime) = "${start.format(TIME)}–${end.format(TIME)}"
private fun ratingRange(rating: Rating) = when (rating) {
    Rating.POOR -> "0–19"
    Rating.FAIR -> "20–39"
    Rating.GOOD -> "40–59"
    Rating.VERY_GOOD -> "60–79"
    Rating.EXCELLENT -> "80–100"
}
internal fun ratingColor(score: Int) = when {
    score >= 80 -> Color(0xFF087A63)
    score >= 60 -> Color(0xFF39734B)
    score >= 40 -> Color(0xFF8A6500)
    score >= 20 -> Color(0xFFA85D16)
    else -> Color(0xFFA34235)
}

internal fun ratingContainerColor(score: Int) = when {
    score >= 80 -> Color(0xFFD7F3EC)
    score >= 60 -> Color(0xFFE0F0E2)
    score >= 40 -> Color(0xFFFFF0C2)
    score >= 20 -> Color(0xFFFCE4CA)
    else -> Color(0xFFF9DDD8)
}

internal fun factorColor(outcome: FactorOutcome) = when (outcome) {
    FactorOutcome.POSITIVE -> Color(0xFF087A63)
    FactorOutcome.MIXED -> Color(0xFF8A6500)
    FactorOutcome.NEGATIVE -> Color(0xFFA34235)
}
