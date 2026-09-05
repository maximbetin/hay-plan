package com.mbk.hayplan.data

import org.junit.Assert.*
import org.junit.Test

class OpenMeteoParserTest {
    private val weather = """
        {
          "daily": {"time":["2026-09-02"],"sunrise":["2026-09-02T08:15"],"sunset":["2026-09-02T20:35"]},
          "hourly": {
            "time":["2026-09-02T08:00","2026-09-02T09:00","2026-09-02T10:00","2026-09-02T19:00","2026-09-02T20:00"],
            "temperature_2m":[20,21,null,22,23],
            "apparent_temperature":[19,20,21,22,23],
            "relative_humidity_2m":[70,65,60,55,50],
            "precipitation_probability":[0,10,30,40,50],
            "precipitation":[0,0.1,0.7,0.2,0.3],
            "cloud_cover":[10,20,30,40,50],
            "visibility":[20000,15000,10000,5000,1000],
            "weather_code":[0,1,2,3,45],
            "wind_speed_10m":[5,6,7,8,9],
            "wind_gusts_10m":[10,12,14,16,18],
            "uv_index":[1,2,3,4,5]
          }
        }
    """.trimIndent()

    @Test
    fun `window must fit fully between sunrise and sunset`() {
        val hours = OpenMeteoParser.weather(weather)
        assertFalse(hours[0].isDaylight)
        assertTrue(hours[1].isDaylight)
        assertTrue(hours[3].isDaylight)
        assertFalse(hours[4].isDaylight)
    }

    @Test
    fun `preceding hour rain is aligned to the hay-plan interval`() {
        val hours = OpenMeteoParser.weather(weather)
        assertEquals(30, hours[1].precipitationProbabilityPercent)
        assertEquals(0.7, hours[1].precipitationMm!!, 0.001)
        assertEquals(14.0, hours[1].windGustsKmh!!, 0.001)
        assertNull(hours[2].precipitationMm) // No next contiguous endpoint.
        assertNull(hours.last().precipitationProbabilityPercent)
    }

    @Test
    fun `null weather values stay unknown not zero`() {
        val hours = OpenMeteoParser.weather(weather)
        assertNull(hours[2].airTemperatureC)
        assertNull(hours[0].waveHeightM)
        assertEquals(20.0, hours[1].apparentTemperatureC!!, 0.001)
        assertEquals(65, hours[1].relativeHumidityPercent)
        assertEquals(15_000.0, hours[1].visibilityM!!, 0.001)
        assertEquals(1, hours[1].weatherCode)
        assertEquals(2.0, hours[1].uvIndex!!, 0.001)
    }

    @Test
    fun `marine join uses timestamps and preserves weather-only dates`() {
        val marine = """{"hourly": {
          "time":["2026-09-02T09:00","2026-09-02T08:00"],
          "wave_height":[0.8,null],"sea_surface_temperature":[21.5,20.0]
        }}"""
        val hours = OpenMeteoParser.withMarine(OpenMeteoParser.weather(weather), marine)
        assertEquals(5, hours.size)
        assertEquals(0.8, hours[1].waveHeightM!!, 0.001)
        assertEquals(21.5, hours[1].seaTemperatureC!!, 0.001)
        assertNull(hours[0].waveHeightM)
        assertNull(hours.last().seaTemperatureC)
    }

    @Test
    fun `absent solar data never invents daylight`() {
        val hours = OpenMeteoParser.weather("""{"hourly":{"time":["2026-09-02T12:00"]}}""")
        assertFalse(hours.single().isDaylight)
    }
}
