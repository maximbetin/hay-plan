package com.mbk.hayplan.ui

import com.mbk.hayplan.data.*
import com.mbk.hayplan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LocationSelectionTest {
    private val gijon = LocationCatalog.locations.first { it.id == "gijon" }
    private val oviedo = LocationCatalog.locations.first { it.id == "oviedo" }
    private val date = LocalDate.of(2026, 9, 3)
    private fun forecast(location: HayPlanLocation, day: LocalDate = date) = LocationForecast(location,
        ActivityForecastData(hours = listOf(HourlyConditions(day.atTime(12, 0), true,
            20.0, 0, 0, 10.0, null, null, 0.0))))

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

    @Test fun `activity change returns to all towns and preserves selected date`() {
        val state = HayPlanUiState(forecasts = listOf(forecast(oviedo)), selectedDate = date)
            .openLocation("oviedo").selectActivity(ActivityType.HIKING)
        assertNull(state.opened)
        assertEquals(ActivityType.HIKING, state.activity)
        assertEquals(date, state.selectedDate)
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
        assertEquals("Gijón · Hiking\nThu 03/09", forecastContextLabel(gijon, ActivityType.HIKING, date))
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
}
