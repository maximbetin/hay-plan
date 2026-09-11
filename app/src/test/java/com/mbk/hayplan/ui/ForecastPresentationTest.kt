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

    @Test fun `summary rounds percentage averages instead of truncating them`() {
        val result = summary(listOf(
            hour(8).copy(cloudCoverPercent = 45, relativeHumidityPercent = 60),
            hour(9).copy(cloudCoverPercent = 46, relativeHumidityPercent = 61),
            hour(10).copy(cloudCoverPercent = 46, relativeHumidityPercent = 61),
        ))!!
        assertEquals("46% avg", result.values.first { it.label == "Cloud cover" }.value)
        assertEquals("61% avg", result.values.first { it.label == "Humidity" }.value)
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
        assertEquals("Viento muy fuerte.", strings("Very strong wind."))
        assertEquals("28,2 km/h máx.", strings("28.2 km/h max"))
        val summary = summary((8..10).map(::hour))!!
        assertEquals("Sensación 23,0°C · Prob. lluvia 10% máx.\nRachas 18,0 km/h máx. · Nubes 20% media",
            dayWeatherHeadline(summary, AppLanguage.SPANISH))
        assertEquals("Sensación 23,0°C · Agua 20,0°C · Olas 0,5 m\n" +
            "Lluvia 10% · Rachas 18,0 km/h · Nubes 20%",
            cardConditions(summary, ActivityType.BEACH, coastal = true, language = AppLanguage.SPANISH))
        assertEquals("Media: Excelente", strings.averageRating(Rating.EXCELLENT))
        assertEquals("Todo el día", strings.rankingMode(RankingMode.WHOLE_DAY))
        assertEquals("Mejor franja de 3 horas", strings.rankingMode(RankingMode.BEST_WINDOW))
        assertEquals("Tormenta · 18:00–20:00", strings.warningPeriod(ForecastWarningPeriod(
            ForecastWarning.THUNDERSTORM, date.atTime(18, 0), date.atTime(20, 0))))
    }

    @Test fun `typed Spanish references and source timestamps preserve supplied names`() {
        val strings = UiStrings(AppLanguage.SPANISH)
        assertEquals("Tiempo: Pola de Somiedo", strings.weatherSource("Pola de Somiedo"))
        assertEquals("Referencia del mar: San Lorenzo", strings.seaReference("San Lorenzo"))
        assertEquals("Actualizado: 03/09 10:00", strings.updated("03/09 10:00"))
    }

    @Test fun `typed unavailable reasons format counts without sentence matching`() {
        val strings = UiStrings(AppLanguage.SPANISH)
        assertEquals("Previsión incompleta · 2/4 horas valoradas",
            strings.dayUnavailable(DayUnavailableReason.Incomplete(2, 4)))
        assertEquals("No hay previsión para esta fecha.",
            strings.dayUnavailable(DayUnavailableReason.NoForecast))
    }

    @Test fun `forecast horizon labels separate later and long range dates`() {
        assertNull(forecastConfidenceLabel(date.plusDays(2), date))
        assertEquals("Later outlook · forecast may change", forecastConfidenceLabel(date.plusDays(3), date))
        assertFalse(isLongRangeOutlook(date.plusDays(6), date))
        assertTrue(isLongRangeOutlook(date.plusDays(7), date))
        assertEquals("Long-range outlook · lower confidence", forecastConfidenceLabel(date.plusDays(7), date))
    }

    @Test fun `weekly outlook uses the seven exact-score dates and keeps unavailable days`() {
        fun hoursFor(day: LocalDate) = (8..10).map { time ->
            hour(time).copy(time = day.atTime(time, 0), sunrise = day.atTime(8, 0), sunset = day.atTime(11, 0))
        }
        val dates = (0L..8L).map(date::plusDays)
        val hours = dates.flatMap(::hoursFor).filterNot { it.time == date.plusDays(2).atTime(9, 0) }

        val result = sevenDayOutlook(hours, dates, date.atStartOfDay(), ActivityType.BEACH)

        assertEquals(dates.take(7), result.map { it.date })
        assertTrue(result.first().outlook.day != null)
        assertNull(result[2].outlook.day)
        assertTrue(result[2].outlook.dayUnavailableReason is DayUnavailableReason.Incomplete)
    }

    @Test fun `semantic colors distinguish every rating band and factor outcome`() {
        val ratingColors = listOf(0, 20, 40, 60, 90).map(::ratingColor)
        val containers = listOf(0, 20, 40, 60, 90).map(::ratingContainerColor)
        assertEquals(5, ratingColors.distinct().size)
        assertEquals(5, containers.distinct().size)
        assertEquals(3, FactorOutcome.entries.map(::factorColor).distinct().size)
        assertEquals(5, listOf(0, 20, 40, 60, 90).map { ratingColor(it, darkTheme = true) }.distinct().size)
    }

}
