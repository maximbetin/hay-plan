package com.mbk.hayplan.ui

import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LocationSelectionTest {
    @Test fun `regional location context identifies the actual weather town for both activities`() {
        val location = HayPlanLocation("region", "Region", "Asturias", Coordinates(43.2, -5.3),
            weatherReference = "Reference town")
        ActivityType.entries.forEach { activity ->
            assertTrue(forecastContextLabel(location, activity, LocalDate.of(2026, 9, 4))
                .contains("Region · Reference town · ${activity.label}"))
        }
    }

    private val gijon = LocationCatalog.locations.first { it.id == "gijon" }
    private val oviedo = LocationCatalog.locations.first { it.id == "oviedo" }
    private val date = LocalDate.of(2026, 9, 3)
    private fun forecast(location: HayPlanLocation, day: LocalDate = date) = LocationForecast(location,
        ActivityForecastData(hours = listOf(HourlyConditions(day.atTime(12, 0), true,
            20.0, 0, 0, 10.0, null, null, 0.0,
            sunrise = day.atTime(8, 0), sunset = day.atTime(13, 0),
            apparentTemperatureC = 20.0, relativeHumidityPercent = 60, visibilityM = 20_000.0,
            weatherCode = 1, windGustsKmh = 18.0, uvIndex = 4.0))))

    @Test fun `initial state is all towns in Beach mode`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(gijon), forecast(oviedo)))
        assertNull(state.opened)
        assertEquals(ActivityType.BEACH, state.activity)
        assertEquals("San Lorenzo", gijon.coast!!.name)
        assertNull(oviedo.coast)
    }

    @Test fun `opening inland town does not change the chosen activity or date`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(oviedo)), selectedDate = date).openLocation("oviedo")
        assertEquals(oviedo, state.opened!!.location)
        assertEquals(ActivityType.BEACH, state.activity)
        assertEquals(date, state.selectedDate)
    }

    @Test fun `activity change keeps an opened location and preserves selected date`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(oviedo)), selectedDate = date)
            .openLocation("oviedo").selectActivity(ActivityType.HIKING)
        assertEquals(oviedo, state.opened!!.location)
        assertEquals(ActivityType.HIKING, state.activity)
        assertEquals(date, state.selectedDate)
    }

    @Test fun `after sunset prompt applies only to today with completed forecast data`() {
        val data = forecast(oviedo)
        val finished = DayPlanner.forDate(data.weather.hours, date, date.atTime(13, 0), ActivityType.BEACH)
        assertTrue(daylightHasEnded(date, date.atTime(13, 0), listOf(finished)))
        assertFalse(daylightHasEnded(date.plusDays(1), date.atTime(13, 0), listOf(finished)))
        assertFalse(daylightHasEnded(date, date.atTime(13, 0), emptyList()))
        val missing = DayPlanner.forDate(emptyList(), date, date.atTime(13, 0), ActivityType.BEACH)
        assertFalse(daylightHasEnded(date, date.atTime(13, 0), listOf(missing)))
    }

    @Test fun `back retains filters and already loaded data`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(gijon), forecast(oviedo)),
            selectedDate = date, activity = ActivityType.HIKING).openLocation("gijon")
        val closed = state.copy(openedLocationId = null)
        assertSame(state.forecasts, closed.forecasts)
        assertEquals(state.activity, closed.activity)
        assertEquals(state.selectedDate, closed.selectedDate)
    }

    @Test fun `unknown town leaves state unchanged`() {
        val state = HayPlanUiState()
        assertSame(state, state.openLocation("missing"))
    }

    @Test fun `navigation preserves source timestamps and refresh failure flags`() {
        val source = ForecastSourceStatus("Weather", Instant.parse("2026-09-03T08:00:00Z"), true, false)
        val data = forecast(oviedo).let { it.copy(weather = it.weather.copy(sources = listOf(source))) }
        val state = HayPlanUiState(forecasts = listOf(data)).openLocation("oviedo")
        assertSame(data, state.opened)
        assertSame(source, state.opened!!.weather.sources.single())
    }

    @Test fun `dates cover all towns and opening one cannot silently change the date`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(gijon), forecast(oviedo, date.plusDays(1))),
            selectedDate = date).openLocation("oviedo").atTime(Instant.parse("2026-09-03T08:00:00Z"))
        assertEquals(listOf(date, date.plusDays(1)), state.dates)
        assertEquals(date, state.selectedDate)
        assertEquals(date.atTime(10, 0), state.now)
    }

    @Test fun `context uses consistent numeric dates and only a configured coastal reference`() {
        assertEquals("Gijón · Beach · San Lorenzo\nThu 03/09", forecastContextLabel(gijon, ActivityType.BEACH, date))
        assertEquals("Oviedo · Beach\nThu 03/09", forecastContextLabel(oviedo, ActivityType.BEACH, date))
        assertEquals("Gijón · Walk\nThu 03/09", forecastContextLabel(gijon, ActivityType.HIKING, date))
        assertEquals("Fri 04/09", formatDate(date.plusDays(1)))
    }

    @Test fun `catalog identifiers and coordinates are valid without freezing its membership`() {
        val locations = LocationCatalog.locations
        assertEquals(locations.size, locations.map { it.id }.distinct().size)
        assertEquals(locations.size, locations.map { it.name }.distinct().size)
        locations.forEach { location ->
            assertTrue(location.coordinates.latitude in -90.0..90.0)
            assertTrue(location.coordinates.longitude in -180.0..180.0)
            location.coast?.let {
                assertTrue(it.name.isNotBlank())
                assertTrue(it.coordinates.latitude in -90.0..90.0)
                assertTrue(it.coordinates.longitude in -180.0..180.0)
                assertNotEquals(location.coordinates, it.coordinates)
            }
        }
    }

    @Test fun `first selection skips today once its daylight has passed`() {
        val forecasts = listOf(forecast(oviedo), forecast(oviedo, date.plusDays(1)))
        val evening = date.atTime(14, 0).atZone(LocationCatalog.zone).toInstant()
        val afterSunset = HayPlanUiState(forecasts = forecasts).atTime(evening)
        assertEquals(date.plusDays(1), afterSunset.selectedDate)
        assertEquals(listOf(date, date.plusDays(1)), afterSunset.dates)
        val morning = date.atTime(9, 0).atZone(LocationCatalog.zone).toInstant()
        assertEquals(date, HayPlanUiState(forecasts = forecasts).atTime(morning).selectedDate)
        // An explicit choice of today is kept even after sunset.
        assertEquals(date, HayPlanUiState(forecasts = forecasts, selectedDate = date).atTime(evening).selectedDate)
    }

    @Test fun `planning scores every location once per selection and reuses matching plans`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(gijon), forecast(oviedo)),
            dates = listOf(date), selectedDate = date, now = date.atStartOfDay())
        assertTrue(state.needsPlanning)
        val planned = state.planned()
        assertFalse(planned.needsPlanning)
        assertEquals(setOf("gijon", "oviedo"), planned.plan!!.outlooks.keys)
        assertEquals(setOf("gijon", "oviedo"), planned.week!!.outlooks.keys)
        // The day's cards and the week grid share the same scored objects.
        assertSame(planned.week.outlooks.getValue("gijon").single().outlook, planned.plan.outlooks["gijon"])
        assertSame(planned, planned.planned())
        // Opening or switching places needs no new scoring.
        val opened = planned.openLocation("gijon")
        assertFalse(opened.needsPlanning)
        val switched = opened.selectActivity(ActivityType.HIKING).planned()
        assertEquals(ActivityType.HIKING, switched.plan!!.activity)
        assertEquals(ActivityType.HIKING, switched.week!!.activity)
    }

    @Test fun `week view returns to the grid from a cell and a date chip leaves it`() {
        val tomorrow = date.plusDays(1)
        val state = HayPlanUiState(forecasts = listOf(forecast(gijon), forecast(oviedo, tomorrow)),
            dates = listOf(date, tomorrow), selectedDate = date).openLocation("oviedo").showWeek()
        assertTrue(state.weekView)
        assertNull(state.openedLocationId)
        val cell = state.openDay("oviedo", tomorrow)
        assertEquals(tomorrow, cell.selectedDate)
        assertEquals("oviedo", cell.openedLocationId)
        // Changing the day inside a detail keeps the way back to the grid.
        assertTrue(cell.selectDate(date).weekView)
        assertTrue(cell.copy(openedLocationId = null).weekView)
        assertFalse(state.selectDate(tomorrow).weekView)
        assertEquals(state, state.selectDate(date.plusDays(9)))
    }
}
