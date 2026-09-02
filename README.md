# Outing

A small Android app that turns weather conditions into clear outdoor activity
recommendations for San Lorenzo, Poniente, and Estaño in Gijón.

Select a date, compare swimming and sunbathing, and expand “Why these ratings?”
to see the factors. Open-Meteo is the only runtime data provider; there are no
accounts, backend, database, location permissions, or API keys.

## Run it

1. Open this folder in Android Studio.
2. Let Android Studio sync the Gradle project and install any requested Android
   SDK components.
3. Select an emulator and run the `app` configuration.

Night hours are intentionally excluded from recommendations and the interface.

## Forecast behavior

- Requests 16 days from the [weather](https://open-meteo.com/en/docs) and
  [marine](https://open-meteo.com/en/docs/marine-weather-api) endpoints. Dates
  come from the response, not a fixed Today/Tomorrow list. Marine variables
  usually end sooner: never fill missing sea data with weather data or zeroes.
- A live API check on 2 September 2026 returned 384 hourly timestamps for each
  endpoint, but only 218 non-null wave values and 224 sea-temperature values.
  The marine docs have inconsistent range descriptions; the live endpoint
  accepts `forecast_days=16`. Actual returned values determine availability.
- Best windows are three contiguous, complete hours on the selected date,
  entirely between sunrise and sunset. For today, exclude already-started hours.
  Dates and times use Europe/Madrid even if the device is in another timezone.
- Air/water/cloud values are averages; wave/wind/rain-chance values are maxima;
  precipitation is a total. Preceding-hour precipitation is aligned to the
  correct outing interval. Both endpoints are joined by timestamp.
- Scores and explanations are deterministic. The best capped score wins;
  ties use the earliest window. Rough waves, cold water, rain, high heat and
  strong wind impose explicit rating limits. This is a comfort/suitability
  heuristic, not a safety assessment. Thresholds are initial, unvalidated rules.
- Later dates include an outlook caveat. Nearby beaches may share forecast
  grids; local shelter, currents, tides, beach flags and UV are not assessed.
- Data is held in memory. Refresh reloads it; changing dates makes no requests.
  Failed refreshes retain previous data with an explicit message and fetch time.

## Where things live

- `data/OpenMeteoClient.kt`: HTTPS requests off the UI thread.
- `data/OpenMeteoParser.kt`: JSON, null handling, sunrise/sunset and alignment.
- `data/ForecastRepository.kt`: parallel requests and partial failures.
- `domain/BeachScorer.kt`: scoring and rating limits, independent of Android.
- `domain/DayPlanner.kt`: date and remaining-daylight selection.
- `ui/OutingViewModel.kt`: in-memory state and refresh.
- `ui/BeachScreen.kt`: date strip, beach summaries and expandable factors.

`SampleForecast` is used only for the Android Studio preview, never as a live-data fallback.
Static beach coordinates are from the [Gijón municipal beach catalog](https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML).
Weather data attribution: Open-Meteo, CC BY 4.0.

## Verify

With Android Studio's JDK configured, run `gradlew.bat testDebugUnitTest assembleDebug`.
Tests cover null marine data, timestamp joins, sunrise/sunset boundaries,
rain-interval alignment, missing hours, date selection, rating limits and ties.
