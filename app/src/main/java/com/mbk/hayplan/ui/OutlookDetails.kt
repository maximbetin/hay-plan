package com.mbk.hayplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbk.hayplan.R
import com.mbk.hayplan.domain.*
import java.time.LocalDate
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
    scorePrecision: ScorePrecision,
    onDayClick: () -> Unit,
    onWindowClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val primaryPeriod = primaryWarningPeriod(outlook.warningPeriods)
    val dayWarning = primaryPeriod?.warning ?: outlook.day?.warnings?.let(::primaryWarning)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = localizedString(if (scorePrecision == ScorePrecision.HIDDEN)
                    R.string.show_long_range_details else R.string.show_day_score_details), onClick = onDayClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings(label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${localizedString(if (scorePrecision == ScorePrecision.HIDDEN)
                    R.string.outlook_details else R.string.comfort_score_details)} ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            RatingValue(outlook.day?.rating, outlook.day?.score, average = true,
                showExactScore = scorePrecision == ScorePrecision.EXACT,
                severePeriod = dayWarning?.priority == 3)
            if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, outlook.marineCoverage, strings.language),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(outlook.day?.let {
                localizedPlural(if (remainingToday) R.plurals.remaining_daylight_average
                    else R.plurals.daylight_average, it.assessedHours, it.assessedHours)
            } ?: strings.dayUnavailable(outlook.dayUnavailableReason), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary?.let { Text(dayWeatherHeadline(it, strings.language), style = MaterialTheme.typography.bodyMedium) }
            dayWarning?.let { warning ->
                WarningText(primaryPeriod?.let(strings::warningPeriod) ?: strings(warning), warning)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = localizedString(R.string.show_best_forecast), onClick = onWindowClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedString(R.string.best_three_hours), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val window = outlook.bestWindow
                if (window == null) Text(strings.windowUnavailable(outlook.windowUnavailableReason), style = MaterialTheme.typography.bodySmall)
                else {
                    Text(if (scorePrecision != ScorePrecision.HIDDEN) timeRange(window.start, window.end)
                        else localizedString(R.string.long_range_timing_hidden),
                        style = if (scorePrecision != ScorePrecision.HIDDEN) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (scorePrecision != ScorePrecision.HIDDEN)
                            FontWeight.SemiBold else FontWeight.Normal)
                    Text(if (scorePrecision == ScorePrecision.EXACT)
                        "${strings.rating(window.rating)} · ${window.score}/100"
                    else strings.rating(window.rating), style = MaterialTheme.typography.bodyMedium,
                        color = currentRatingColor(window.score), fontWeight = FontWeight.SemiBold)
                    if (outlook.activity == ActivityType.BEACH && coastal && window.marineCoverage != outlook.marineCoverage)
                        Text(beachCoverageLabel(true, window.marineCoverage, strings.language), style = MaterialTheme.typography.bodySmall)
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
    scorePrecision: ScorePrecision,
    weeklyOutlook: List<DatedOutlook> = emptyList(),
    selectedDate: LocalDate? = null,
    today: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
) {
    val strings = LocalUiStrings.current
    var target by remember(selectionKey) { mutableStateOf<DetailTarget?>(null) }
    val summary = remember(outlook, hours, coastal) { dayWeatherSummary(outlook, hours, coastal) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.padding(18.dp)) {
                DayOverview(outlook, label, summary, remainingToday, coastal, scorePrecision,
                    onDayClick = { target = DetailTarget.Day },
                    onWindowClick = { target = DetailTarget.Window })
            }
        }
        if (selectedDate != null && today != null) {
            WeeklyScoreOutlook(weeklyOutlook, selectedDate, today, onDateSelected)
        }
        Text(localizedString(R.string.daylight_hours), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        outlook.hourly.forEach { hour ->
            HourRow(hour, if (scorePrecision != ScorePrecision.HIDDEN) outlook.bestWindow else null,
                outlook.marineCoverage == MarineCoverage.MIXED,
                scorePrecision == ScorePrecision.EXACT) {
                target = DetailTarget.Hour(hour.time)
            }
        }
        if (outlook.hourly.isEmpty() &&
            outlook.dayUnavailableReason != DayUnavailableReason.MissingDaylightBounds) Text(
            localizedString(R.string.no_daylight_hours),
            style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = { target = null }) { Text(localizedString(R.string.close)) }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (selected) {
                    DetailTarget.Day -> DayInspection(outlook, label, summary, coastal,
                        scorePrecision != ScorePrecision.HIDDEN)
                    DetailTarget.Window -> WindowInspection(outlook, coastal,
                        scorePrecision != ScorePrecision.HIDDEN)
                    is DetailTarget.Hour -> HourInspection(outlook.hourly.find { it.time == selected.time },
                        selected.time, outlook.activity, coastal,
                        scorePrecision != ScorePrecision.HIDDEN)
                }
                SuitabilityNote(outlook.activity, coastal)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HourRow(hour: HourlyAssessment, best: BestWindow?, showCoverage: Boolean,
                    showExactScore: Boolean, onClick: () -> Unit) {
    val strings = LocalUiStrings.current
    val time = hour.time.toLocalTime()
    val isBest = best != null && !time.isBefore(best.start) && time.isBefore(best.end)
    val score = hour.evaluation?.score
    val scoreColor = score?.let { currentRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, modifier = Modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (isBest) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(time.format(TIME), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(score?.let { strings.rating(ratingFor(it)) } ?: localizedString(R.string.unavailable),
                        style = MaterialTheme.typography.bodyMedium, color = scoreColor)
                    if (showCoverage) hour.evaluation?.let {
                        Text(strings.coverage(it.marineCoverage), style = MaterialTheme.typography.labelSmall)
                    }
                    if (best?.start == time) Text(localizedString(R.string.best_three_hours),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (showExactScore) Text(score?.let { "$it/100" } ?: "—",
                    fontWeight = FontWeight.SemiBold, color = scoreColor)
                Text("›", Modifier.clearAndSetSemantics { })
            }
            if (showExactScore && score != null) Box(Modifier.fillMaxWidth().height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
                .clearAndSetSemantics { }) {
                Box(Modifier.fillMaxWidth(score / 100f).fillMaxHeight()
                    .background(currentRatingColor(score), RoundedCornerShape(999.dp)))
            }
        }
    }
}

@Composable
private fun DayInspection(outlook: ActivityOutlook, label: String, summary: DayWeatherSummary?,
                          coastal: Boolean, showCalculationScores: Boolean) {
    val strings = LocalUiStrings.current
    var showAllConditions by rememberSaveable { mutableStateOf(false) }
    Text(strings(label), Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val day = outlook.day
    val severePeriod = (primaryWarningPeriod(outlook.warningPeriods)?.warning
        ?: day?.warnings?.let(::primaryWarning))?.priority == 3
    RatingValue(day?.rating, day?.score, average = true, showExactScore = showCalculationScores,
        severePeriod = severePeriod)
    if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, outlook.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    summary?.let {
        Text(localizedString(R.string.daylight_conditions), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        val primary = buildSet {
            addAll(listOf("Feels like", "Rain chance", "Wind gusts", "Cloud cover"))
            if (outlook.activity == ActivityType.BEACH && coastal) addAll(listOf("Water temperature", "Waves"))
        }
        val shown = if (showAllConditions) it.values else it.values.filter { value -> value.label in primary }
        shown.forEach { value ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings(value.label), Modifier.weight(1f))
                Text(strings(value.value), fontWeight = FontWeight.Medium)
            }
        }
        if (it.values.size > shown.size || showAllConditions) TextButton(onClick = { showAllConditions = !showAllConditions }) {
            Text(localizedString(if (showAllConditions) R.string.fewer_conditions else R.string.more_conditions))
        }
    }
    if (day == null) {
        Text(strings.dayUnavailable(outlook.dayUnavailableReason))
        val rated = outlook.hourly.count { it.evaluation != null }
        Text(localizedPlural(R.plurals.hours_rated, outlook.hourly.size, rated, outlook.hourly.size))
        outlook.warningPeriods.forEach {
            WarningText(strings.warningPeriod(it), it.warning)
        }
        return
    }
    Text(localizedPlural(R.plurals.good_daylight_hours, day.assessedHours,
        day.goodHours, day.assessedHours))
    if (day.warningPeriods.isNotEmpty()) day.warningPeriods.forEach {
        WarningText(strings.warningPeriod(it), it.warning)
    } else day.warnings.forEach {
        WarningText(strings(it), it)
    }
    if (!showCalculationScores) {
        Text(localizedString(R.string.long_range_scores_hidden),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    HorizontalDivider()
    var showCalculation by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showCalculation = !showCalculation }) {
        Text(strings(if (showCalculation) "Hide comfort score calculation ▴"
            else "How is this comfort score calculated? ▾"))
    }
    if (!showCalculation) return
    Text(strings(if (outlook.activity == ActivityType.BEACH)
        "The day comfort score is the average of the displayed daylight-hour scores, rounded to a whole number. " +
            "Each hour earns up to 70 weather points plus available sea points, always against 100 possible points, " +
            "then applies any condition limits. Tap an hour to see its inputs and calculation."
        else "The day comfort score is the average of the displayed daylight-hour scores, rounded to a whole number. " +
            "Each hour uses its available factors, scales their points to 100, then applies any condition limits. " +
            "Tap an hour to see its inputs and calculation."),
        style = MaterialTheme.typography.bodySmall)
    Text(Rating.entries.joinToString(" · ") { "${strings.rating(it)} ${ratingRange(it)}" },
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun WindowInspection(outlook: ActivityOutlook, coastal: Boolean, showCalculationScores: Boolean) {
    val strings = LocalUiStrings.current
    val window = outlook.bestWindow
    Text(localizedString(R.string.best_three_hours), Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
    Text(window?.let { if (showCalculationScores) timeRange(it.start, it.end)
        else localizedString(R.string.long_range_timing_hidden) } ?: localizedString(R.string.unavailable),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (window == null) { Text(strings.windowUnavailable(outlook.windowUnavailableReason)); return }
    RatingValue(window.rating, window.score, average = true, showExactScore = showCalculationScores)
    if (outlook.activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, window.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    Text(strings("Average of these three hourly scores, with any limits for the whole period applied."),
        style = MaterialTheme.typography.bodySmall)
    if (!showCalculationScores) Text(localizedString(R.string.long_range_scores_hidden),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Values summarize the period; the score itself uses its individual hourly scores.
    window.factors.forEach { FactorRow(it, showPoints = false) }
    if (window.warningPeriods.isNotEmpty()) window.warningPeriods.forEach {
        WarningText(strings.warningPeriod(it), it.warning)
    } else window.warnings.forEach {
        WarningText(strings(it), it)
    }
}

@Composable
private fun HourInspection(hour: HourlyAssessment?, time: LocalDateTime, activity: ActivityType,
                           coastal: Boolean, showExactScore: Boolean) {
    val strings = LocalUiStrings.current
    Text(timeRange(time.toLocalTime(), time.plusHours(1).toLocalTime()),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val evaluation = hour?.evaluation
    RatingValue(evaluation?.let { ratingFor(it.score) }, evaluation?.score,
        showExactScore = showExactScore)
    if (evaluation == null) { Text(localizedString(R.string.incomplete_hour)); return }
    if (activity == ActivityType.BEACH) Text(beachCoverageLabel(coastal, evaluation.marineCoverage, strings.language),
        style = MaterialTheme.typography.bodySmall)
    evaluation.factors.forEach { FactorRow(it, showPoints = showExactScore) }
    if (showExactScore) {
        if (activity == ActivityType.BEACH && evaluation.availablePoints < 100) {
            Text(localizedString(R.string.supported_score_total, evaluation.supportedScoreBeforeLimits),
                style = MaterialTheme.typography.bodySmall)
            Text(localizedString(R.string.known_inputs_score, evaluation.supportedScoreBeforeLimits,
                evaluation.availablePoints, evaluation.knownConditionsScore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(strings("Weighted total: ${evaluation.knownConditionsScore}/100"),
                style = MaterialTheme.typography.bodySmall)
        }
        val scoreBeforeLimits = if (activity == ActivityType.BEACH)
            evaluation.supportedScoreBeforeLimits else evaluation.knownConditionsScore
        val deduction = scoreBeforeLimits - evaluation.score
        if (deduction > 0) Text(localizedPlural(R.plurals.score_reduced_points, deduction, deduction),
            color = MaterialTheme.colorScheme.error)
    } else Text(localizedString(R.string.long_range_scores_hidden),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    evaluation.warnings.forEach { WarningText(strings(it), it) }
}

@Composable
private fun SuitabilityNote(activity: ActivityType, coastal: Boolean) {
    val strings = LocalUiStrings.current
    Text(localizedString(R.string.personal_score_note),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(strings(if (activity == ActivityType.HIKING) "Town and nearby-area weather, not exact trail or elevation conditions."
        else if (coastal) "Only available sea data is used. Local shelter, beach flags and currents are not assessed."
        else "Weather for outdoor leisure; pool temperatures and river conditions are not assessed."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactorRow(factor: FactorResult, showPoints: Boolean) {
    val strings = LocalUiStrings.current
    val color = currentFactorColor(factor.outcome)
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
internal fun RatingValue(rating: Rating?, score: Int?, average: Boolean = false,
                         showExactScore: Boolean = true, severePeriod: Boolean = false) {
    val strings = LocalUiStrings.current
    val color = score?.let { currentRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(rating?.let { if (average) strings.averageRating(it, severePeriod) else strings.rating(it) }
            ?: localizedString(R.string.unavailable), Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = color)
        if (showExactScore) {
            Surface(shape = RoundedCornerShape(999.dp),
                color = score?.let { currentRatingContainerColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant) {
                Text(score?.let { "$it/100" } ?: "—", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal fun timeRange(start: LocalTime, end: LocalTime) = "${start.format(TIME)}–${end.format(TIME)}"
@Composable
private fun warningColor(warning: ForecastWarning): Color =
    if (warning.priority >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

@Composable
private fun WarningText(text: String, warning: ForecastWarning) {
    val description = localizedString(R.string.warning_description, text)
    Text(text, Modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.bodySmall, color = warningColor(warning))
}
internal fun ratingColor(score: Int, darkTheme: Boolean = false) = when {
    score >= 90 -> if (darkTheme) Color(0xFF75D8C8) else Color(0xFF087A63)
    score >= 60 -> if (darkTheme) Color(0xFF9AD39F) else Color(0xFF39734B)
    score >= 40 -> if (darkTheme) Color(0xFFFFCC65) else Color(0xFF8A6500)
    score >= 20 -> if (darkTheme) Color(0xFFFFB77B) else Color(0xFFA85D16)
    else -> if (darkTheme) Color(0xFFFFB4A9) else Color(0xFFA34235)
}

internal fun ratingContainerColor(score: Int, darkTheme: Boolean = false) = when {
    score >= 90 -> if (darkTheme) Color(0xFF174D43) else Color(0xFFD7F3EC)
    score >= 60 -> if (darkTheme) Color(0xFF294B31) else Color(0xFFE0F0E2)
    score >= 40 -> if (darkTheme) Color(0xFF554500) else Color(0xFFFFF0C2)
    score >= 20 -> if (darkTheme) Color(0xFF5B3518) else Color(0xFFFCE4CA)
    else -> if (darkTheme) Color(0xFF5F2B27) else Color(0xFFF9DDD8)
}

internal fun factorColor(outcome: FactorOutcome, darkTheme: Boolean = false) = when (outcome) {
    FactorOutcome.POSITIVE -> if (darkTheme) Color(0xFF75D8C8) else Color(0xFF087A63)
    FactorOutcome.MIXED -> if (darkTheme) Color(0xFFFFCC65) else Color(0xFF8A6500)
    FactorOutcome.NEGATIVE -> if (darkTheme) Color(0xFFFFB4A9) else Color(0xFFA34235)
}

@Composable
internal fun currentRatingColor(score: Int) = ratingColor(score, isSystemInDarkTheme())

@Composable
internal fun currentRatingContainerColor(score: Int) = ratingContainerColor(score, isSystemInDarkTheme())

@Composable
internal fun currentFactorColor(outcome: FactorOutcome) = factorColor(outcome, isSystemInDarkTheme())
