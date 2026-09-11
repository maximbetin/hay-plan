package com.mbk.hayplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
        val warning = point.outlook.graphWarning()
        descriptionParts += localizedString(
            R.string.weekly_outlook_day_description,
            formatDate(point.date, today, strings.language),
            point.outlook.day?.score?.let { "$it/100" } ?: localizedString(R.string.unavailable),
            point.outlook.bestWindow?.score?.let { "$it/100" } ?: localizedString(R.string.unavailable),
            warning?.let { localizedString(R.string.weekly_outlook_warning_suffix, strings(it)) }.orEmpty(),
        )
    }
    val descriptions = descriptionParts.joinToString(". ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            localizedString(R.string.weekly_outlook),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlookLegendItem(localizedString(R.string.daylight_average), average = true,
                modifier = Modifier.weight(1f))
            OutlookLegendItem(localizedString(R.string.best_three_hours), average = false,
                modifier = Modifier.weight(1f))
        }
        if (points.any { it.outlook.graphWarning() != null }) {
            Text("▲ ${localizedString(R.string.warning)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val contentWidth = maxWidth.coerceAtLeast(MINIMUM_CHART_WIDTH)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(contentWidth)) {
                    ScoreChart(
                        points = points,
                        selectedDate = selectedDate,
                        darkTheme = darkTheme,
                        modifier = Modifier.fillMaxWidth().height(176.dp)
                            .semantics { contentDescription = descriptions },
                    )
                    Row(Modifier.fillMaxWidth().padding(start = AXIS_WIDTH)) {
                        points.forEach { point ->
                            val warning = point.outlook.graphWarning()
                            TextButton(
                                onClick = { onDateSelected(point.date) },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(compactDate(point.date, strings.language),
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Text(point.outlook.day?.score?.toString() ?: "—",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (point.date == selectedDate) FontWeight.Bold else FontWeight.Normal,
                                        color = point.outlook.day?.score?.let { currentRatingColor(it) }
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (warning != null) Text("▲", style = MaterialTheme.typography.labelSmall,
                                        color = if (warning.priority >= 2) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.tertiary)
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

@Composable
private fun OutlookLegendItem(label: String, average: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 24.dp, height = 16.dp)) {
            if (average) {
                drawLine(color, Offset(1.dp.toPx(), center.y), Offset(size.width - 1.dp.toPx(), center.y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, 3.dp.toPx(), center)
            } else {
                drawLine(color.copy(alpha = 0.65f), Offset(center.x, size.height - 2.dp.toPx()),
                    Offset(center.x, 2.dp.toPx()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(center.x - 4.dp.toPx(), 2.dp.toPx()),
                    Offset(center.x + 4.dp.toPx(), 2.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreChart(
    points: List<DatedOutlook>,
    selectedDate: LocalDate,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selected = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val error = MaterialTheme.colorScheme.error
    val caution = MaterialTheme.colorScheme.tertiary
    val bandColors = listOf(0 to ratingContainerColor(0, darkTheme), 20 to ratingContainerColor(20, darkTheme),
        40 to ratingContainerColor(40, darkTheme), 60 to ratingContainerColor(60, darkTheme),
        90 to ratingContainerColor(90, darkTheme))
    val pointColors = points.map { it.outlook.day?.score?.let { score -> ratingColor(score, darkTheme) } ?: grid }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val axis = AXIS_WIDTH.toPx()
            val top = 12.dp.toPx()
            val bottom = 4.dp.toPx()
            val plotHeight = size.height - top - bottom
            val plotWidth = size.width - axis
            val columnWidth = plotWidth / points.size
            fun x(index: Int) = axis + columnWidth * (index + 0.5f)
            fun y(score: Int) = top + plotHeight * (100 - score.coerceIn(0, 100)) / 100f

            bandColors.forEachIndexed { index, (minimum, color) ->
                val maximum = bandColors.getOrNull(index + 1)?.first ?: 100
                drawRect(color.copy(alpha = 0.24f), Offset(axis, y(maximum)),
                    Size(plotWidth, y(minimum) - y(maximum)))
            }
            points.indexOfFirst { it.date == selectedDate }.takeIf { it >= 0 }?.let { index ->
                drawRoundRect(selected.copy(alpha = 0.65f),
                    Offset(axis + columnWidth * index + 2.dp.toPx(), 0f),
                    Size(columnWidth - 4.dp.toPx(), size.height), CornerRadius(10.dp.toPx()))
            }
            listOf(20, 40, 60, 90).forEach { threshold ->
                drawLine(grid, Offset(axis, y(threshold)), Offset(size.width, y(threshold)),
                    strokeWidth = 1.dp.toPx())
            }

            var previous: Pair<Int, Int>? = null
            points.forEachIndexed { index, point ->
                val average = point.outlook.day?.score
                val best = point.outlook.bestWindow?.score
                if (average != null) {
                    previous?.takeIf { it.first == index - 1 }?.let { (_, priorScore) ->
                        drawLine(primary, Offset(x(index - 1), y(priorScore)), Offset(x(index), y(average)),
                            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                    previous = index to average
                    if (best != null) {
                        drawLine(pointColors[index].copy(alpha = 0.65f), Offset(x(index), y(average)),
                            Offset(x(index), y(best)), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(pointColors[index], Offset(x(index) - 5.dp.toPx(), y(best)),
                            Offset(x(index) + 5.dp.toPx(), y(best)), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    drawCircle(pointColors[index], if (point.date == selectedDate) 6.dp.toPx() else 4.5.dp.toPx(),
                        Offset(x(index), y(average)))
                    drawCircle(surface, 1.5.dp.toPx(), Offset(x(index), y(average)))
                } else {
                    previous = null
                    best?.let { drawCircle(pointColors[index], 5.dp.toPx(), Offset(x(index), y(it)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())) }
                }
                val warning = point.outlook.graphWarning()
                warning?.let {
                    val markerX = x(index)
                    val path = Path().apply {
                        moveTo(markerX, 1.dp.toPx())
                        lineTo(markerX - 5.dp.toPx(), 10.dp.toPx())
                        lineTo(markerX + 5.dp.toPx(), 10.dp.toPx())
                        close()
                    }
                    drawPath(path, if (it.priority >= 2) error else caution)
                }
            }
        }
        listOf(90, 60, 40, 20).forEach { score ->
            Text(score.toString(), Modifier.width(AXIS_WIDTH).align(Alignment.TopStart)
                .offset(y = scoreOffset(score, 176.dp)), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun scoreOffset(score: Int, height: Dp): Dp =
    12.dp + (height - 16.dp) * (100 - score.coerceIn(0, 100)) / 100f - 7.dp

private fun compactDate(date: LocalDate, language: AppLanguage): String {
    val locale = if (language == AppLanguage.SPANISH) Locale.forLanguageTag("es-ES") else Locale.ENGLISH
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".")
    return "$weekday ${date.dayOfMonth}"
}

private fun ActivityOutlook.graphWarning() =
    primaryWarningPeriod(warningPeriods)?.warning ?: day?.warnings?.let(::primaryWarning)

private val AXIS_WIDTH = 18.dp
private val MINIMUM_CHART_WIDTH = 354.dp
