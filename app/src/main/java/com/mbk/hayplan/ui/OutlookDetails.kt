package com.mbk.hayplan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.painterResource
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

internal sealed interface DetailTarget {
    data object Day : DetailTarget
    data object Window : DetailTarget
    data class Hour(val time: LocalDateTime) : DetailTarget
}

/** Same reading order as an overview card: rating, context, conditions, warning, then the best window. */
@Composable
internal fun DayOverview(
    outlook: ActivityOutlook,
    @StringRes label: Int,
    summary: DayWeatherSummary?,
    remainingToday: Boolean,
    coastal: Boolean,
    scorePrecision: ScorePrecision,
    sourceLine: String?,
    notices: @Composable () -> Unit,
    onDayClick: () -> Unit,
    onWindowClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val primaryPeriod = primaryWarningPeriod(outlook.warningPeriods)
    val dayWarning = primaryPeriod?.warning ?: outlook.day?.warnings?.let(::primaryWarning)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = localizedString(if (scorePrecision == ScorePrecision.HIDDEN)
                    R.string.show_long_range_details else R.string.show_day_score_details), onClick = onDayClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localizedString(label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${localizedString(R.string.details)} ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            RatingValue(outlook.day?.rating, outlook.day?.score,
                showExactScore = scorePrecision == ScorePrecision.EXACT)
            if (outlook.day == null) Text(strings.dayUnavailable(outlook.dayUnavailableReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            dayWarning?.let { warning ->
                WarningLine(primaryPeriod?.let(strings::warningPeriod) ?: strings(warning), warning)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = localizedString(R.string.show_best_forecast), onClick = onWindowClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(localizedString(R.string.best_three_hours), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val window = outlook.bestWindow
                if (window == null) Text(strings.windowUnavailable(outlook.windowUnavailableReason), style = MaterialTheme.typography.bodySmall)
                else {
                    val wholePeriod = scorePrecision != ScorePrecision.HIDDEN && windowCoversAllHours(outlook)
                    Text(when {
                        scorePrecision == ScorePrecision.HIDDEN -> localizedString(R.string.long_range_timing_hidden)
                        wholePeriod -> localizedString(R.string.window_all_remaining_daylight)
                        else -> timeRange(window.start, window.end)
                    },
                        style = if (scorePrecision != ScorePrecision.HIDDEN) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (scorePrecision != ScorePrecision.HIDDEN)
                            FontWeight.SemiBold else FontWeight.Normal)
                    // The whole-period case has the day's score directly above it.
                    if (!wholePeriod) Text(if (scorePrecision == ScorePrecision.EXACT)
                        "${strings.rating(window.rating)} · ${window.score}/100"
                    else strings.rating(window.rating), style = MaterialTheme.typography.bodyMedium,
                        color = currentRatingColor(window.score), fontWeight = FontWeight.SemiBold)
                    if (outlook.activity == ActivityType.BEACH && coastal && window.marineCoverage != outlook.marineCoverage)
                        Text(beachCoverageLabel(true, window.marineCoverage, strings.language), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("›", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Detail content as lazy items, so opening a location composes only the rows on screen.
 * [target] is shared with [DetailSheet], which the caller places outside the list.
 */
internal fun LazyListScope.outlookDetails(
    outlook: ActivityOutlook,
    summary: DayWeatherSummary?,
    @StringRes label: Int,
    remainingToday: Boolean,
    coastal: Boolean,
    scorePrecision: ScorePrecision,
    weeklyOutlook: List<DatedOutlook>,
    selectedDate: LocalDate,
    today: LocalDate,
    sourceLine: String?,
    notices: @Composable () -> Unit,
    target: MutableState<DetailTarget?>,
) {
    item(key = "overview") {
        Surface(Modifier.padding(bottom = 14.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                DayOverview(outlook, label, summary, remainingToday, coastal, scorePrecision,
                    sourceLine, notices,
                    onDayClick = { target.value = DetailTarget.Day },
                    onWindowClick = { target.value = DetailTarget.Window })
            }
        }
    }
    if (outlook.hourly.isNotEmpty()) {
        item(key = "hours-heading") {
            Text(localizedString(R.string.daylight_hours), Modifier.padding(bottom = 6.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        hourRows(outlook, if (scorePrecision != ScorePrecision.HIDDEN) outlook.bestWindow else null,
            outlook.marineCoverage == MarineCoverage.MIXED,
            scorePrecision == ScorePrecision.EXACT) { target.value = DetailTarget.Hour(it) }
    } else if (outlook.dayUnavailableReason != DayUnavailableReason.MissingDaylightBounds) item {
        Text(localizedString(R.string.no_daylight_hours), style = MaterialTheme.typography.bodyMedium)
    }
    if (weeklyOutlook.size >= 2) item(key = "weekly") {
        Box(Modifier.padding(top = 18.dp, bottom = 14.dp)) {
            WeeklyScoreOutlook(weeklyOutlook, selectedDate, today)
        }
    }
    item(key = "context") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourceLine?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            notices()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailSheet(
    target: MutableState<DetailTarget?>,
    outlook: ActivityOutlook,
    summary: DayWeatherSummary?,
    @StringRes label: Int,
    contextLabel: String,
    coastal: Boolean,
    scorePrecision: ScorePrecision,
) {
    val selected = target.value ?: return
    ModalBottomSheet(
        onDismissRequest = { target.value = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Keep place/activity/date visible even while scrolling a long explanation.
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(contextLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { target.value = null }) { Text(localizedString(R.string.close)) }
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

/** Dense rows on one visual surface; the best window is a tinted run rather than a border on each hour. */
private fun LazyListScope.hourRows(outlook: ActivityOutlook, best: BestWindow?, showCoverage: Boolean,
                                   showExactScore: Boolean, onClick: (LocalDateTime) -> Unit) {
    val last = outlook.hourly.lastIndex
    itemsIndexed(outlook.hourly, key = { _, hour -> hour.time.toString() }) { index, hour ->
        val shape = RoundedCornerShape(
            topStart = if (index == 0) 22.dp else 0.dp, topEnd = if (index == 0) 22.dp else 0.dp,
            bottomStart = if (index == last) 22.dp else 0.dp, bottomEnd = if (index == last) 22.dp else 0.dp)
        Surface(shape = shape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(top = if (index == 0) 4.dp else 0.dp, bottom = if (index == last) 4.dp else 0.dp)) {
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)
                HourRow(hour, best, showCoverage, showExactScore) { onClick(hour.time) }
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
    val rating = score?.let { strings.rating(ratingFor(it)) } ?: localizedString(R.string.unavailable)
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .background(if (isBest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(time.format(TIME), Modifier.width(52.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val tags = listOfNotNull(
                localizedString(R.string.best_three_hours).takeIf { best?.start == time },
                hour.evaluation?.takeIf { showCoverage }?.let { strings.coverage(it.marineCoverage) },
            )
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The bar carries the rating visually; screen readers get the word instead.
            if (showExactScore && score != null) Box(Modifier.fillMaxWidth().height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
                .semantics { contentDescription = rating }) {
                Box(Modifier.fillMaxWidth(score / 100f).fillMaxHeight()
                    .background(currentRatingColor(score), RoundedCornerShape(999.dp)))
            } else Text(rating, style = MaterialTheme.typography.bodyMedium, color = scoreColor)
        }
        if (showExactScore && score != null) Text("$score", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scoreColor)
        Text("›", Modifier.clearAndSetSemantics { }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayInspection(outlook: ActivityOutlook, @StringRes label: Int, summary: DayWeatherSummary?,
                          coastal: Boolean, showCalculationScores: Boolean) {
    val strings = LocalUiStrings.current
    var showAllConditions by rememberSaveable { mutableStateOf(false) }
    Text(localizedString(label), Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    val day = outlook.day
    RatingValue(day?.rating, day?.score, showExactScore = showCalculationScores)
    if (outlook.activity == ActivityType.BEACH) coverageNote(coastal, outlook.marineCoverage, strings.language)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
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
            WarningLine(strings.warningPeriod(it), it.warning)
        }
        return
    }
    Text(localizedPlural(R.plurals.good_daylight_hours, day.assessedHours,
        day.goodHours, day.assessedHours))
    if (day.warningPeriods.isNotEmpty()) day.warningPeriods.forEach {
        WarningLine(strings.warningPeriod(it), it.warning)
    } else day.warnings.forEach {
        WarningLine(strings(it), it)
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
        Text(localizedString(if (showCalculation) R.string.hide_score_calculation
            else R.string.show_score_calculation))
    }
    if (!showCalculation) return
    Text(localizedString(R.string.personal_score_note),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(localizedString(if (outlook.activity == ActivityType.BEACH) R.string.score_calculation_beach
        else R.string.score_calculation_hiking),
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
    RatingValue(window.rating, window.score, showExactScore = showCalculationScores)
    if (outlook.activity == ActivityType.BEACH) coverageNote(coastal, window.marineCoverage, strings.language)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (!showCalculationScores) Text(localizedString(R.string.long_range_scores_hidden),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Values summarize the period; the score itself uses its individual hourly scores.
    window.factors.forEach { FactorRow(it, showPoints = false) }
    if (window.warningPeriods.isNotEmpty()) window.warningPeriods.forEach {
        WarningLine(strings.warningPeriod(it), it.warning)
    } else window.warnings.forEach {
        WarningLine(strings(it), it)
    }
    Text(localizedString(R.string.window_calculation),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (activity == ActivityType.BEACH) coverageNote(coastal, evaluation.marineCoverage, strings.language)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
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
    evaluation.warnings.forEach { WarningLine(strings(it), it) }
}

@Composable
private fun SuitabilityNote(activity: ActivityType, coastal: Boolean) {
    Text(localizedString(when {
        activity == ActivityType.HIKING -> R.string.suitability_hiking
        coastal -> R.string.suitability_coastal
        else -> R.string.suitability_inland
    }),
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

/** One statement of a score: the rating word and, when exact, the number. Cards use the compact pill. */
@Composable
internal fun RatingValue(rating: Rating?, score: Int?, showExactScore: Boolean = true, compact: Boolean = false) {
    val strings = LocalUiStrings.current
    val color = score?.let { currentRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val container = score?.let { currentRatingContainerColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val onContainer = score?.let { currentRatingOnContainerColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val word = rating?.let { strings.rating(it) } ?: localizedString(R.string.unavailable)
    if (compact) {
        Surface(shape = RoundedCornerShape(999.dp), color = container) {
            Text(if (showExactScore && score != null) "$word · $score" else word,
                Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = onContainer)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = color)
        if (showExactScore) Surface(shape = RoundedCornerShape(999.dp), color = container) {
            Text(score?.let { "$it/100" } ?: "—", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onContainer)
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal fun timeRange(start: LocalTime, end: LocalTime) = "${start.format(TIME)}–${end.format(TIME)}"
/** Severe warnings read in the error colour; the icon marks the rest without borrowing a rating colour. */
@Composable
internal fun WarningLine(text: String, warning: ForecastWarning) {
    val severe = warning.priority >= 3
    val description = localizedString(R.string.warning_description, text)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, Modifier.size(16.dp),
            tint = if (severe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = if (severe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
// A clearly ordered ramp, red to deep green, so neighbouring ratings never read as the same colour.
internal fun ratingColor(score: Int, darkTheme: Boolean = false) = when {
    score >= 90 -> if (darkTheme) Color(0xFF8EE6AE) else Color(0xFF17693A)
    score >= 60 -> if (darkTheme) Color(0xFFC4E68A) else Color(0xFF4A7A12)
    score >= 40 -> if (darkTheme) Color(0xFFFFD86B) else Color(0xFF6E5200)
    score >= 20 -> if (darkTheme) Color(0xFFFFBE8A) else Color(0xFF8F4608)
    else -> if (darkTheme) Color(0xFFFFB4A9) else Color(0xFF8E2A1E)
}

internal fun ratingContainerColor(score: Int, darkTheme: Boolean = false) = when {
    score >= 90 -> if (darkTheme) Color(0xFF1E7A45) else Color(0xFF1F7A43)
    score >= 60 -> if (darkTheme) Color(0xFF355420) else Color(0xFFCDEBB5)
    score >= 40 -> if (darkTheme) Color(0xFF5A4700) else Color(0xFFFFE9A3)
    score >= 20 -> if (darkTheme) Color(0xFF66380F) else Color(0xFFFFCFA3)
    else -> if (darkTheme) Color(0xFF6E2A24) else Color(0xFFF7C1B9)
}

/** Text drawn on a [ratingContainerColor] fill: Excellent's fill is dark enough to need light text. */
internal fun ratingOnContainerColor(score: Int, darkTheme: Boolean = false) =
    if (score >= 90) (if (darkTheme) Color(0xFFE6FFEC) else Color.White) else ratingColor(score, darkTheme)

internal fun factorColor(outcome: FactorOutcome, darkTheme: Boolean = false) = when (outcome) {
    FactorOutcome.POSITIVE -> if (darkTheme) Color(0xFF75D8C8) else Color(0xFF087A63)
    FactorOutcome.MIXED -> if (darkTheme) Color(0xFFFFCC65) else Color(0xFF8A6500)
    FactorOutcome.NEGATIVE -> if (darkTheme) Color(0xFFFFB4A9) else Color(0xFFA34235)
}

@Composable
internal fun currentRatingColor(score: Int) = ratingColor(score, isSystemInDarkTheme())

@Composable
internal fun currentRatingOnContainerColor(score: Int) = ratingOnContainerColor(score, isSystemInDarkTheme())

@Composable
internal fun currentRatingContainerColor(score: Int) = ratingContainerColor(score, isSystemInDarkTheme())

@Composable
internal fun currentFactorColor(outcome: FactorOutcome) = factorColor(outcome, isSystemInDarkTheme())
