package com.mbk.outing.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mbk.outing.data.BeachCatalog
import com.mbk.outing.data.BeachForecast
import com.mbk.outing.data.SampleForecast
import com.mbk.outing.domain.ActivityOutlook
import com.mbk.outing.domain.ActivityRecommendation
import com.mbk.outing.domain.DayPlanner
import com.mbk.outing.domain.FactorOutcome
import com.mbk.outing.domain.FactorResult
import com.mbk.outing.ui.theme.OutingTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OutingApp(viewModel: OutingViewModel = viewModel()) {
    val owner = LocalLifecycleOwner.current
    // Re-evaluate remaining daylight on resume and while visible; no extra API calls.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.updateTime()
                delay(60_000)
            }
        }
    }
    BeachScreen(viewModel.uiState, viewModel::selectDate, viewModel::refresh)
}

@Composable
fun BeachScreen(
    state: OutingUiState,
    onDateSelected: (LocalDate) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Beach days", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Gijón · Asturias", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(if (state.isLoading) "Loading…" else "Refresh")
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(message, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (state.dates.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.isLoading) "Finding your next beach day…" else "No forecast available.")
                        if (!state.isLoading) Button(onClick = onRefresh) { Text("Try again") }
                    }
                }
            } else {
                DateStrip(state, onDateSelected)
                val date = state.selectedDate ?: state.dates.first()
                val listState = rememberLazyListState()
                LaunchedEffect(date) { listState.scrollToItem(0) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (date == state.now.toLocalDate()) "Remaining daylight · times in Asturias"
                                else "Daylight only · times in Asturias",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (date.isAfter(state.now.toLocalDate().plusDays(6))) {
                                Text("Further ahead: treat ratings and time windows as a rough outlook.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(state.forecasts, key = { it.beach.id }) { forecast ->
                        BeachCard(forecast, date, state)
                    }
                    item { ForecastFooter(state) }
                }
            }
        }
    }
}

@Composable
private fun DateStrip(state: OutingUiState, onDateSelected: (LocalDate) -> Unit) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(state.selectedDate) {
        val index = state.dates.indexOf(state.selectedDate)
        if (index >= 0) scrollState.animateScrollToItem(index)
    }
    Column {
        Text(
            "${state.dates.size} forecast days · swipe to explore",
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.dates, key = { it.toString() }) { date ->
                FilterChip(
                    selected = date == state.selectedDate,
                    onClick = { onDateSelected(date) },
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(dayLabel(date, state.now.toLocalDate()), fontWeight = FontWeight.SemiBold)
                            Text(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BeachCard(forecast: BeachForecast, date: LocalDate, state: OutingUiState) {
    var expanded by rememberSaveable(forecast.beach.id, date.toString()) { mutableStateOf(false) }
    val outlooks = remember(forecast, date, state.now) {
        DayPlanner.forDate(forecast.hours, date, state.now)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(forecast.beach.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (forecast.weatherError) {
                Text("Weather forecast unavailable. Tap Refresh to try again.")
            } else {
                if (forecast.marineError) {
                    Text("Marine service unavailable. Swimming cannot be rated.",
                        style = MaterialTheme.typography.bodySmall)
                }
                outlooks.forEachIndexed { index, outlook ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActivitySummary(outlook)
                }
                if (outlooks.any { it.recommendation != null }) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide reasons" else "Why these ratings?")
                    }
                }
                if (expanded) {
                    outlooks.mapNotNull { it.recommendation }.forEach { recommendation ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(recommendation.activity + " · " + formatWindow(recommendation),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        recommendation.factors.forEach { FactorRow(it) }
                        recommendation.warnings.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(
                        "For each window: average air/water temperature and cloud cover; highest wind, waves and rain chance; total rain. Ratings use adjustable rules, not AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySummary(outlook: ActivityOutlook) {
    val recommendation = outlook.recommendation
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(outlook.activity, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recommendation == null) {
            Text("Unavailable", style = MaterialTheme.typography.titleMedium)
            Text(outlook.unavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(recommendation.rating.label, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = ratingColor(recommendation.score))
            // Stacked deliberately: readable on narrow screens and at large font sizes.
            Text(
                if (recommendation.score < 40) "Best available: ${formatWindow(recommendation)} · not recommended"
                else "Best: ${formatWindow(recommendation)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FactorRow(factor: FactorResult) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(when (factor.outcome) {
            FactorOutcome.POSITIVE -> "✓"
            FactorOutcome.MIXED -> "~"
            FactorOutcome.NEGATIVE -> "!"
        }, color = outcomeColor(factor.outcome), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(factor.label, style = MaterialTheme.typography.bodyMedium)
            Text(factor.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(factor.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ForecastFooter(state: OutingUiState) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.fetchedAt?.let {
            Text("Fetched ${it.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH))} · Asturias time",
                style = MaterialTheme.typography.labelMedium)
        }
        Text("Weather and marine forecasts have different ranges. Missing data stays unavailable. Nearby beaches may share a forecast grid and receive the same rating.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Suitability is not a safety assessment. Check beach flags and lifeguard advice. Rip currents, local shelter and UV exposure are not assessed.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) {
            Text("Weather data: Open-Meteo (CC BY 4.0)")
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
}

private fun formatWindow(recommendation: ActivityRecommendation): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return "${recommendation.bestStart.format(formatter)}–${recommendation.bestEnd.format(formatter)}"
}

private fun ratingColor(score: Int) = when {
    score >= 80 -> Color(0xFF087A63)
    score >= 60 -> Color(0xFF39734B)
    score >= 40 -> Color(0xFF8A6500)
    else -> Color(0xFFA34235)
}

private fun outcomeColor(outcome: FactorOutcome) = when (outcome) {
    FactorOutcome.POSITIVE -> Color(0xFF087A63)
    FactorOutcome.MIXED -> Color(0xFF8A6500)
    FactorOutcome.NEGATIVE -> Color(0xFFA34235)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun BeachScreenPreview() {
    val hours = SampleForecast.tomorrow()
    val date = hours.first().time.toLocalDate()
    OutingTheme {
        BeachScreen(OutingUiState(
            forecasts = BeachCatalog.beaches.map { BeachForecast(it, hours) },
            dates = listOf(date),
            selectedDate = date,
            isLoading = false,
        ))
    }
}
