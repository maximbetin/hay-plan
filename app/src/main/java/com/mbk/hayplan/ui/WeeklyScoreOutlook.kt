package com.mbk.hayplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mbk.hayplan.R
import com.mbk.hayplan.domain.ActivityOutlook
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.DayPlanner
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.domain.primaryWarning
import com.mbk.hayplan.domain.primaryWarningPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

internal data class DatedOutlook(val date: LocalDate, val outlook: ActivityOutlook)

internal fun sevenDayOutlook(
    hours: List<HourlyConditions>,
    dates: List<LocalDate>,
    now: LocalDateTime,
    activity: ActivityType,
): List<DatedOutlook> = dates.asSequence()
    .filterNot { it.isBefore(now.toLocalDate()) }
    .takeWhile { !isLongRangeOutlook(it, now.toLocalDate()) }
    .take(7)
    .map { DatedOutlook(it, DayPlanner.forDate(hours, it, now, activity)) }
    .toList()

@Composable
internal fun WeeklyScoreOutlook(
    points: List<DatedOutlook>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    if (points.size < 2) return
    val strings = LocalUiStrings.current
    val darkTheme = isSystemInDarkTheme()
    val descriptionParts = mutableListOf<String>()
    for (point in points) {
        val warning = point.outlook.severeGraphWarning()
        val day = point.outlook.day
        val scoreDescription = day?.let {
            if (scorePrecision(point.date, today) == ScorePrecision.EXACT) "${it.score}/100"
            else strings.rating(it.rating)
        } ?: localizedString(R.string.unavailable)
        descriptionParts += localizedString(
            R.string.weekly_outlook_day_description,
            formatDate(point.date, today, strings.language),
            scoreDescription,
            warning?.let { localizedString(R.string.weekly_outlook_warning_suffix, strings(it)) }.orEmpty(),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(localizedString(R.string.weekly_outlook), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(localizedString(R.string.weekly_outlook_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val contentWidth = maxWidth.coerceAtLeast(MINIMUM_CHART_WIDTH)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(contentWidth)) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(CHART_HEIGHT)
                            .semantics { contentDescription = descriptionParts.joinToString(". ") },
                    ) {
                        val usableHeight = maxHeight - SCORE_SPACE
                        Row(Modifier.fillMaxSize()) {
                            points.forEach { point ->
                                val score = point.outlook.day?.score
                                val precision = scorePrecision(point.date, today)
                                val chartScore = score?.let {
                                    if (precision == ScorePrecision.EXACT) it else scoreBandMidpoint(it)
                                }
                                val barHeight = chartScore?.let { usableHeight * it.coerceIn(0, 100) / 100f }
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (score == null || barHeight == null) {
                                        Text("—", Modifier.align(Alignment.BottomCenter).offset(y = (-4).dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Box(Modifier.align(Alignment.BottomCenter).width(BAR_WIDTH).height(barHeight)
                                            .background(ratingColor(score, darkTheme), BAR_SHAPE))
                                        Text(if (precision == ScorePrecision.EXACT) "$score" else scoreBand(score),
                                            Modifier.align(Alignment.BottomCenter)
                                            .offset(y = -(barHeight + 4.dp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        points.forEach { point ->
                            val warning = point.outlook.severeGraphWarning()
                            val selected = point.date == selectedDate
                            TextButton(
                                onClick = { onDateSelected(point.date) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent,
                                ),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(compactDate(point.date, strings.language),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1)
                                    if (warning != null) {
                                        Text("!", style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (points.firstOrNull()?.date == today) {
            Text(localizedString(R.string.today_uses_remaining_daylight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun compactDate(date: LocalDate, language: AppLanguage): String {
    val locale = if (language == AppLanguage.SPANISH) Locale.forLanguageTag("es-ES") else Locale.ENGLISH
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".")
    return "$weekday ${date.dayOfMonth}"
}

private fun ActivityOutlook.severeGraphWarning() =
    (primaryWarningPeriod(warningPeriods)?.warning ?: day?.warnings?.let(::primaryWarning))
        ?.takeIf { it.priority >= 2 }

private val BAR_SHAPE = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
private val BAR_WIDTH = 24.dp
private val CHART_HEIGHT = 160.dp
private val SCORE_SPACE = 28.dp
private val MINIMUM_CHART_WIDTH = 354.dp
