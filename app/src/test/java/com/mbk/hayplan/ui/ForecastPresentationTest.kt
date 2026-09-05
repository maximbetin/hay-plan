package com.mbk.hayplan.ui

import com.mbk.hayplan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ForecastPresentationTest {
    private val date = LocalDate.of(2026, 9, 3)
    private fun hour(time: Int) = HourlyConditions(
        date.atTime(time, 0), true, 24.0, 10, 20, 12.0, 20.0, 0.5, 0.2,
        date.atTime(8, 0), date.atTime(11, 0),
        apparentTemperatureC = 23.0, relativeHumidityPercent = 60, visibilityM = 20_000.0,
        weatherCode = 1, windGustsKmh = 18.0, uvIndex = 4.0,
    )
    private fun summary(hours: List<HourlyConditions>, activity: ActivityType = ActivityType.BEACH,
                        nowHour: Int = 0): DayWeatherSummary? =
        dayWeatherSummary(DayPlanner.forDate(hours, date, date.atTime(nowHour, 0), activity), hours)

    @Test fun `summary uses ranges maxima and total rain from assessed hours`() {
        val result = summary(listOf(hour(8), hour(9).copy(airTemperatureC = 26.0,
            windSpeedKmh = 21.0, precipitationProbabilityPercent = 30), hour(10)))!!
        assertEquals("Feels 23.0°C · Rain chance 30% max\nGusts 18.0 km/h max · Clouds 20% avg", result.headline)
        assertEquals("0.6 mm total", result.values.first { it.label == "Rainfall" }.value)
        assertEquals(12, result.values.size)
    }

    @Test fun `missing slots never become a summary of only known hours`() {
        val result = summary(listOf(hour(8), hour(10)))!!
        assertTrue(result.values.all { it.value == "Unknown" })
    }

    @Test fun `unknown sea leaves available weather visible without inventing sea values`() {
        val result = summary((8..10).map { hour(it).copy(seaTemperatureC = null) })!!
        assertEquals("Unknown", result.values.first { it.label == "Water temperature" }.value)
        assertTrue(result.headline.contains("Feels 23.0°C"))
    }

    @Test fun `elapsed hours and other dates cannot influence the summary`() {
        val hours = listOf(hour(8).copy(airTemperatureC = 40.0), hour(9), hour(10),
            hour(10).copy(time = date.plusDays(1).atTime(10, 0), airTemperatureC = 50.0))
        assertTrue(summary(hours, nowHour = 9)!!.headline.startsWith("Feels 23.0°C"))
        assertEquals("0.4 mm total", summary(hours, nowHour = 9)!!.values.first { it.label == "Rainfall" }.value)
    }

    @Test fun `hiking summaries do not show sea conditions`() {
        val result = summary((8..10).map(::hour), ActivityType.HIKING)!!
        assertEquals(10, result.values.size)
        assertFalse(result.values.any { it.label == "Waves" || it.label == "Water temperature" })
    }

    @Test fun `inland Beach summary omits marine fields rather than showing fake values`() {
        val hours = (8..10).map { hour(it).copy(seaTemperatureC = null, waveHeightM = null) }
        val outlook = DayPlanner.forDate(hours, date, date.atStartOfDay(), ActivityType.BEACH)
        val result = dayWeatherSummary(outlook, hours, coastal = false)!!
        assertEquals(10, result.values.size)
        assertFalse(result.values.any { it.label == "Waves" || it.label == "Water temperature" })
        assertNotNull(outlook.day)
    }

    @Test fun `invalid values remain unknown`() {
        val result = summary((8..10).map { hour(it).copy(airTemperatureC = Double.NaN,
            windSpeedKmh = -1.0, precipitationProbabilityPercent = 101) })!!
        assertEquals("Unknown", result.values.first { it.label == "Air temperature" }.value)
        assertEquals("Unknown", result.values.first { it.label == "Wind" }.value)
        assertTrue(result.headline.contains("Rain chance Unknown"))
    }

    @Test fun `no daylight hours means no weather summary`() {
        assertNull(summary((8..10).map(::hour), nowHour = 12))
        assertNull(summary(emptyList()))
    }

    @Test fun `average label explains daylight and remaining hours`() {
        assertEquals("Average of 5 remaining daylight hours", daylightAverageLabel(5, true))
        assertEquals("Average of 12 daylight hours", daylightAverageLabel(12, false))
        assertEquals("Average of 1 remaining daylight hour", daylightAverageLabel(1, true))
    }

    @Test fun `card conditions are compact and inland Beach is explicit`() {
        val result = summary((8..10).map(::hour))!!
        assertEquals("Feels 23.0°C · Rain 10% · Gusts 18.0 km/h\nClouds 20% · UV 4.0",
            cardConditions(result, ActivityType.BEACH, coastal = false))
        assertEquals("Feels 23.0°C · Water 20.0°C · Waves 0.5 m\nRain 10% · Gusts 18.0 km/h · Clouds 20%",
            cardConditions(result, ActivityType.BEACH, coastal = true))
        assertEquals("Inland estimate · no beach", beachCoverageLabel(false, MarineCoverage.NONE))
        assertEquals(MarineCoverage.FULL.label, beachCoverageLabel(true, MarineCoverage.FULL))
    }

    @Test fun `Spanish presentation covers dates activities ratings coverage and warnings`() {
        val strings = UiStrings(AppLanguage.SPANISH)
        assertEquals("Hoy · 03/09", formatDate(date, date, AppLanguage.SPANISH))
        assertEquals("Mañana · 04/09", formatDate(date.plusDays(1), date, AppLanguage.SPANISH))
        assertEquals("Senderismo", strings.activity(ActivityType.HIKING))
        assertEquals("Excelente", strings.rating(Rating.EXCELLENT))
        assertEquals("Tiempo y mar", strings.coverage(MarineCoverage.FULL))
        assertEquals("Viento fuerte: valoración limitada a Mala.",
            strings("Strong wind: rating limited to Poor."))
        assertEquals("28.2 km/h máx.", strings("28.2 km/h max"))
        assertEquals("Sensación 20°C · Prob. lluvia 10% máx.\nRachas 18 km/h máx. · Nubes 90% media",
            strings("Feels 20°C · Rain chance 10% max\nGusts 18 km/h max · Clouds 90% avg"))
    }
}
