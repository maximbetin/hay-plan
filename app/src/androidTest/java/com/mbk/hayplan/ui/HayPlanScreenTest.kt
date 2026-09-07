package com.mbk.hayplan.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mbk.hayplan.data.ActivityForecastData
import com.mbk.hayplan.data.Coordinates
import com.mbk.hayplan.data.HayPlanLocation
import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.ui.theme.HayPlanTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HayPlanScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun severeWarningRemainsVisibleBesideAnExcellentDay() {
        val date = LocalDate.of(2026, 9, 3)
        val hours = (8..19).map { hour ->
            HourlyConditions(
                time = date.atTime(hour, 0), isDaylight = true,
                airTemperatureC = 20.0, apparentTemperatureC = 20.0,
                precipitationProbabilityPercent = 0, precipitationMm = 0.0,
                cloudCoverPercent = 0, windSpeedKmh = 5.0, windGustsKmh = 10.0,
                relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = if (hour == 19) 95 else 0, uvIndex = 3.0,
                seaTemperatureC = null, waveHeightM = null,
            )
        }
        val forecast = LocationForecast(
            HayPlanLocation("test", "Test place", "Asturias", Coordinates(43.5, -5.5)),
            ActivityForecastData(hours),
        )
        compose.setContent {
            HayPlanTheme {
                HayPlanScreen(HayPlanUiState(
                    forecasts = listOf(forecast), dates = listOf(date), selectedDate = date,
                    activity = ActivityType.HIKING, now = date.atStartOfDay(), isLoading = false,
                ))
            }
        }

        compose.onNodeWithText("Excellent").assertIsDisplayed()
        compose.onNodeWithText("Thunderstorm.").assertIsDisplayed()
    }
}
