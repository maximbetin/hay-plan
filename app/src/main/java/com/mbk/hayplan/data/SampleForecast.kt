package com.mbk.hayplan.data

import com.mbk.hayplan.domain.HourlyConditions
import java.time.LocalDate
import java.time.LocalDateTime

object SampleForecast {
    fun forDate(date: LocalDate): List<HourlyConditions> {
        return listOf(
            hour(date, 7, false, 18.0, 5, 12, 7.0, 20.0, 0.2),
            hour(date, 8, true, 18.5, 5, 15, 7.0, 20.0, 0.2),
            hour(date, 9, true, 19.5, 5, 18, 8.0, 20.1, 0.2),
            hour(date, 10, true, 21.0, 5, 20, 9.0, 20.2, 0.3),
            hour(date, 11, true, 22.5, 5, 18, 10.0, 20.4, 0.3),
            hour(date, 12, true, 24.0, 5, 15, 11.0, 20.5, 0.3),
            hour(date, 13, true, 25.0, 5, 16, 12.0, 20.6, 0.4),
            hour(date, 14, true, 25.5, 10, 24, 14.0, 20.6, 0.4),
            hour(date, 15, true, 24.5, 15, 35, 17.0, 20.5, 0.5),
            hour(date, 16, true, 23.0, 20, 48, 20.0, 20.4, 0.6),
            hour(date, 17, true, 21.5, 25, 55, 22.0, 20.3, 0.7),
            hour(date, 18, true, 20.0, 30, 62, 24.0, 20.2, 0.8),
            // Night data may be available, but the scorer intentionally ignores it.
            hour(date, 22, false, 23.0, 0, 0, 4.0, 21.0, 0.1),
        )
    }

    private fun hour(
        date: LocalDate,
        hour: Int,
        isDaylight: Boolean,
        airTemperatureC: Double,
        precipitationProbabilityPercent: Int,
        cloudCoverPercent: Int,
        windSpeedKmh: Double,
        seaTemperatureC: Double,
        waveHeightM: Double,
    ) = HourlyConditions(
        time = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
        isDaylight = isDaylight,
        airTemperatureC = airTemperatureC,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        cloudCoverPercent = cloudCoverPercent,
        windSpeedKmh = windSpeedKmh,
        seaTemperatureC = seaTemperatureC,
        waveHeightM = waveHeightM,
        precipitationMm = 0.0,
        apparentTemperatureC = airTemperatureC - 0.5,
        relativeHumidityPercent = 65,
        visibilityM = 20_000.0,
        weatherCode = if (cloudCoverPercent > 80) 3 else if (cloudCoverPercent > 30) 2 else 1,
        windGustsKmh = windSpeedKmh + 8,
        uvIndex = if (isDaylight) 4.0 else 0.0,
    )
}
