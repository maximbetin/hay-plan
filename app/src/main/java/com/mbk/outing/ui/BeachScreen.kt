package com.mbk.outing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mbk.outing.data.SampleForecast
import com.mbk.outing.domain.ActivityRecommendation
import com.mbk.outing.domain.BeachScorer
import com.mbk.outing.domain.FactorOutcome
import com.mbk.outing.domain.FactorResult
import com.mbk.outing.ui.theme.OutingTheme
import java.time.format.DateTimeFormatter

@Composable
fun OutingApp(viewModel: OutingViewModel = viewModel()) {
    BeachScreen(viewModel.uiState)
}

@Composable
fun BeachScreen(state: OutingUiState) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state)
            state.recommendations.forEach { RecommendationCard(it) }
            if (state.isSampleData) {
                Text(
                    text = "Prototype using sample conditions · daylight hours only",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(state: OutingUiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = state.dayLabel.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.locationName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = state.areaName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: ActivityRecommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.activity,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = recommendation.rating.label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ratingColor(recommendation.score),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "BEST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatWindow(recommendation),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            recommendation.factors.forEach { FactorRow(it) }
        }
    }
}

@Composable
private fun FactorRow(factor: FactorResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(outcomeColor(factor.outcome), CircleShape),
        )
        Text(
            text = factor.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = factor.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = factor.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    FactorOutcome.POSITIVE -> Color(0xFF0B8A70)
    FactorOutcome.MIXED -> Color(0xFFE0A223)
    FactorOutcome.NEGATIVE -> Color(0xFFC45345)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun BeachScreenPreview() {
    val forecast = SampleForecast.tomorrow()
    OutingTheme {
        BeachScreen(
            OutingUiState(
                locationName = "San Lorenzo",
                areaName = "Gijón, Asturias",
                dayLabel = "Tomorrow",
                recommendations = listOf(
                    BeachScorer.swimming(forecast),
                    BeachScorer.sunbathing(forecast),
                ),
                isSampleData = true,
            ),
        )
    }
}
