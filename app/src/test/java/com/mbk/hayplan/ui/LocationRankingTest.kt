package com.mbk.hayplan.ui

import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocationRankingTest {
    private val date = LocalDate.of(2026, 9, 4)
    private fun forecast(id: String, temperature: Double?) = LocationForecast(
        HayPlanLocation(id, id, "Asturias", Coordinates(43.5, -5.5)),
        ActivityForecastData((10..13).map {
            HourlyConditions(date.atTime(it, 0), true, temperature, 0, 0, 10.0, null, null, 0.0,
                apparentTemperatureC = temperature, relativeHumidityPercent = 60, visibilityM = 20_000.0,
                weatherCode = 1, windGustsKmh = 18.0, uvIndex = 4.0)
        }),
    )
    private fun outlooks(forecasts: List<LocationForecast>, activity: ActivityType) =
        forecasts.associate { it.location.id to DayPlanner.forDate(
            it.forActivity(activity).hours, date, date.atStartOfDay(), activity) }

    @Test fun `ranking follows selected activity without dropping inland or unavailable towns`() {
        val warm = forecast("warm", 27.0)
        val mild = forecast("mild", 20.0)
        val missing = forecast("missing", null)
        val forecasts = listOf(missing, mild, warm)
        assertEquals(listOf(warm, mild, missing), rankLocations(forecasts, outlooks(forecasts, ActivityType.BEACH)))
        assertEquals(listOf(mild, warm, missing), rankLocations(forecasts, outlooks(forecasts, ActivityType.HIKING)))
        assertSame(warm, rankLocations(forecasts, outlooks(forecasts, ActivityType.BEACH)).first())
    }

    @Test fun `ties have deterministic order regardless of input order`() {
        val forecasts = listOf(forecast("b", 20.0), forecast("a", 20.0))
        val outlooks = outlooks(forecasts, ActivityType.HIKING)
        assertEquals(listOf("a", "b"), rankLocations(forecasts, outlooks).map { it.location.id })
        assertEquals(rankLocations(forecasts, outlooks), rankLocations(forecasts.reversed(), outlooks))
    }

    @Test fun `incomplete day with a window stays below even a poor complete day`() {
        val good = forecast("incomplete", 20.0)
        val incomplete = good.copy(weather = good.weather.copy(hours = good.weather.hours.mapIndexed { i, hour ->
            if (i == 3) hour.copy(airTemperatureC = null) else hour
        })).let { it.copy(beach = it.weather) }
        val poor = forecast("poor", -2.0)
        val forecasts = listOf(incomplete, poor)
        val outlooks = outlooks(forecasts, ActivityType.HIKING)
        assertNull(outlooks.getValue("incomplete").day)
        assertNotNull(outlooks.getValue("incomplete").bestWindow)
        assertEquals(listOf(poor, incomplete), rankLocations(forecasts, outlooks))
    }
}
