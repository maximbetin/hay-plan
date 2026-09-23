package com.mbk.hayplan.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mbk.hayplan.data.ActivityForecastData
import com.mbk.hayplan.data.Coordinates
import com.mbk.hayplan.data.CoastalReference
import com.mbk.hayplan.data.HayPlanLocation
import com.mbk.hayplan.data.LocationForecast
import com.mbk.hayplan.domain.ActivityType
import com.mbk.hayplan.domain.HourlyConditions
import com.mbk.hayplan.ui.theme.HayPlanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.time.LocalDate

class HayPlanScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Hosts the screen with the one callback these tests use, so tapping a card opens its detail. */
    private fun show(initial: HayPlanUiState, language: AppLanguage = AppLanguage.ENGLISH) {
        compose.setContent {
            var state by remember { mutableStateOf(initial.planned()) }
            CompositionLocalProvider(LocalUiStrings provides UiStrings(language)) {
                HayPlanTheme {
                    HayPlanScreen(state, language = language,
                        onLocationSelected = { state = state.openLocation(it).planned() },
                        onWeekSelected = { state = state.showWeek().planned() },
                        onDayOpened = { id, date -> state = state.openDay(id, date).planned() },
                        onBack = { state = state.copy(openedLocationId = null) })
                }
            }
        }
    }

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
            HayPlanLocation("test", "Test place", "Asturias", Coordinates(43.5, -5.5),
                coast = CoastalReference("Test beach", Coordinates(43.5, -5.5))),
            ActivityForecastData(hours),
        )
        show(HayPlanUiState(
            forecasts = listOf(forecast),
            dates = listOf(date),
            selectedDate = date,
            activity = ActivityType.HIKING,
            now = date.atStartOfDay(),
            isLoading = false,
        ).openLocation("test"))

        compose.onNodeWithText("Excellent", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Thunderstorm · 19:00–20:00").assertIsDisplayed()
        compose.onNode(hasText("Best 3 hours") and hasText("Excellent", substring = true)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Rank by").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Settings").assertHasClickAction()
        compose.onNodeWithContentDescription("Warning: Thunderstorm · 19:00–20:00").assertIsDisplayed()
    }

    @Test fun weekGridOpensADayAndBackReturnsToTheGrid() {
        val today = LocalDate.of(2026, 9, 3)
        val dates = (0L..2L).map(today::plusDays)
        val hours = dates.flatMap { date ->
            (8..10).map { hour ->
                HourlyConditions(
                    time = date.atTime(hour, 0), isDaylight = true,
                    airTemperatureC = 20.0, apparentTemperatureC = 20.0,
                    precipitationProbabilityPercent = 0, precipitationMm = 0.0,
                    cloudCoverPercent = 0, windSpeedKmh = 5.0, windGustsKmh = 10.0,
                    relativeHumidityPercent = 60, visibilityM = 20_000.0,
                    weatherCode = 0, uvIndex = 3.0, seaTemperatureC = null, waveHeightM = null,
                )
            }
        }
        val forecast = LocationForecast(
            HayPlanLocation("week", "Week place", "Asturias", Coordinates(43.5, -5.5),
                coast = CoastalReference("Week beach", Coordinates(43.5, -5.5))),
            ActivityForecastData(hours),
        )
        show(HayPlanUiState(
            forecasts = listOf(forecast),
            dates = dates,
            selectedDate = today,
            activity = ActivityType.HIKING,
            now = today.atStartOfDay(),
            isLoading = false,
        ))

        compose.onNodeWithText("Week place").assertHasClickAction()
        compose.onNodeWithContentDescription("Week place, Sat 05/09: Excellent, 100/100").performClick()
        compose.onNodeWithText("Hour by hour").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Week place").assertHasClickAction()
    }

    @Test fun longRangeOutlookHidesExactScoresAndBestWindowTiming() {
        val today = LocalDate.of(2026, 9, 3)
        val date = today.plusDays(7)
        val hours = (8..10).map { hour ->
            HourlyConditions(
                time = date.atTime(hour, 0), isDaylight = true,
                airTemperatureC = 20.0, apparentTemperatureC = 20.0,
                precipitationProbabilityPercent = 0, precipitationMm = 0.0,
                cloudCoverPercent = 0, windSpeedKmh = 5.0, windGustsKmh = 10.0,
                relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = 0, uvIndex = 3.0, seaTemperatureC = null, waveHeightM = null,
            )
        }
        val forecast = LocationForecast(
            HayPlanLocation("future", "Future place", "Asturias", Coordinates(43.5, -5.5),
                coast = CoastalReference("Future beach", Coordinates(43.5, -5.5))),
            ActivityForecastData(hours),
        )
        show(HayPlanUiState(
            forecasts = listOf(forecast),
            dates = listOf(date),
            selectedDate = date,
            now = today.atStartOfDay(),
            isLoading = false,
        ).openLocation("future"))

        compose.onNodeWithText("Long-range outlook · lower confidence").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("96/100").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("08:00–11:00").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Timing not shown for long-range outlooks").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("96/100").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("08:00–11:00").fetchSemanticsNodes().isEmpty())
    }

    @Test fun laterOutlookUsesBandsUntilCalculationDetailsAreOpened() {
        val today = LocalDate.of(2026, 9, 3)
        val date = today.plusDays(3)
        val hours = (8..14).map { hour ->
            HourlyConditions(
                time = date.atTime(hour, 0), isDaylight = true,
                airTemperatureC = 20.0, apparentTemperatureC = 20.0,
                precipitationProbabilityPercent = 0, precipitationMm = 0.0,
                cloudCoverPercent = 0, windSpeedKmh = 5.0, windGustsKmh = 10.0,
                relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = 0, uvIndex = 3.0, seaTemperatureC = null, waveHeightM = null,
            )
        }
        val forecast = LocationForecast(
            HayPlanLocation("later", "Later place", "Asturias", Coordinates(43.5, -5.5),
                coast = CoastalReference("Later beach", Coordinates(43.5, -5.5))),
            ActivityForecastData(hours),
        )
        show(HayPlanUiState(
            forecasts = listOf(forecast),
            dates = listOf(date),
            selectedDate = date,
            activity = ActivityType.HIKING,
            now = today.atStartOfDay(),
            isLoading = false,
        ).openLocation("later"))

        compose.onNodeWithText("Later outlook · forecast may change").assertIsDisplayed()
        compose.onNodeWithText("08:00–11:00").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("100/100").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Details ›").performClick()
        compose.onNodeWithText("100/100").assertIsDisplayed()
    }

    @Test fun SpanishOverrideControlsAndroidResourcesAndPluralPresentation() {
        val date = LocalDate.of(2026, 9, 3)
        val hours = (8..10).map { hour ->
            HourlyConditions(
                time = date.atTime(hour, 0), isDaylight = true,
                airTemperatureC = 20.0, apparentTemperatureC = 20.0,
                precipitationProbabilityPercent = 0, precipitationMm = 0.0,
                cloudCoverPercent = 0, windSpeedKmh = 5.0, windGustsKmh = 10.0,
                relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = 0, uvIndex = 3.0, seaTemperatureC = null, waveHeightM = null,
            )
        }
        val forecast = LocationForecast(
            HayPlanLocation("spanish", "Lugar", "Asturias", Coordinates(43.5, -5.5),
                coast = CoastalReference("Playa", Coordinates(43.5, -5.5))),
            ActivityForecastData(hours),
        )
        show(HayPlanUiState(
            forecasts = listOf(forecast),
            dates = listOf(date),
            selectedDate = date,
            now = date.atStartOfDay(),
            isLoading = false,
        ), language = AppLanguage.SPANISH)

        compose.onNodeWithContentDescription("Ajustes").assertHasClickAction()
        compose.onNodeWithText("Playita").assertIsSelected()
        compose.onNodeWithText("Paseíto").assertHasClickAction()
        compose.onNodeWithText("Lugar").assertHasClickAction()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Notificación diaria de planes").assertHasClickAction().assertIsDisplayed()
        compose.onNodeWithText("Hora · 09:00").assertHasClickAction().assertIsDisplayed()
    }
}
