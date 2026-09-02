# Outing

A small native Android app for deciding whether a day suits **Beach** or **Hiking**.
Kotlin + Jetpack Compose. Open-Meteo is the only runtime data provider.

## Use it

1. Open this folder in Android Studio, let Gradle sync, and run `app`.
2. Select a date and Beach or Hiking.
3. Tap the location card for that activity's details.
4. Tap **Refresh** whenever you want new forecasts, even within the cache hour.

The first location is **Gijón**. Beach uses its assigned main beach, **San Lorenzo**;
Hiking uses town-level weather, without requiring a route. Other towns and ranked
top-five/top-ten lists are intentionally not implemented yet.

## What the rating means

- **Day overall** averages suitability across all full daylight hours of the
  selected day. **Rest of today** excludes hours that have already started.
- **Best window** is a separate three-hour recommendation. An otherwise Fair day
  can have an Excellent window; that window does not determine the headline.
- Day ratings require complete data for the daylight period. With partial data,
  the day may be Unavailable while a known three-hour window can still be shown.
- Beach combines weather and swimming conditions into one profile. Hiking covers
  general hiking/walking weather; sea conditions and cloudiness are not inputs.
- These are adjustable comfort/suitability rules, not safety guarantees. Beach
  flags, rip currents, UV, trail surfaces, elevation and route hazards are not assessed.

See [scoring rules](docs/SCORING.md) for thresholds and aggregation.

## Forecasts and caching

- Request the full available range (up to 16 days) from Open-Meteo. Marine fields
  can end sooner; missing data stays unknown, never zero-filled.
- Dates and times are in **Europe/Madrid**, regardless of the device timezone.
  A recommended window must fit entirely between sunrise and sunset.
- Each unique request URL is cached in memory and in the app's private
  `noBackupFilesDir/forecasts` directory for **one hour**. This survives app
  restarts and is excluded from Android backups.
- Fresh Gijón loading uses at most **three requests**: town weather, main-beach
  weather, and main-beach marine data. Identical coordinates are deduplicated.
- Changing date, activity, or detail view makes **no requests**. While visible,
  expired data is refreshed automatically. There is no background service.
- **Refresh bypasses the cache**. Repeated taps during a load cannot start another.
  A failed refresh retains saved data and its original timestamp with a warning.
  Automatic failures are retried at most hourly; manual refresh remains available.
- Cache files are versioned and atomically replaced where supported. Invalid files
  are ignored, invalid API responses cannot replace good entries, and persistence
  failures leave the current forecast usable in memory.

No backend, accounts, database, geolocation permission, routes, or API keys.

## Project layout

- `data/LocationCatalog.kt`: town coordinates and optional main beach.
- `data/OpenMeteoClient.kt`: HTTPS requests and validation.
- `data/ForecastCache.kt`: disk/memory cache, expiry, refresh and fallback.
- `data/OpenMeteoParser.kt`: JSON, null handling, solar bounds and timestamp joins.
- `data/ForecastRepository.kt`: source deduplication and partial failures.
- `domain/ActivityScorer.kt`: pure Kotlin rules for Beach and Hiking.
- `domain/DayPlanner.kt`: whole-day assessment and independent best window.
- `ui/OutingViewModel.kt`: state, selection and lifecycle-aware refresh timing.
- `ui/OutingScreen.kt`: date/activity controls, tappable locations and details.

Dependencies are passed manually using a ViewModel factory; there is no DI framework.
The cache accepts an injected clock/downloader so tests need neither Android nor
network access. `SampleForecast` is preview-only, never a live-data fallback.

## Verify

With Android Studio's JDK configured:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Tests cover whole-day versus peak-window ratings, daylight/current-time filtering,
missing data, rating limits, cache persistence/expiry/manual refresh/cancellation,
corrupt responses, request deduplication and endpoint failure isolation.

## Data sources

- [Open-Meteo weather API](https://open-meteo.com/en/docs)
- [Open-Meteo marine API](https://open-meteo.com/en/docs/marine-weather-api)
- [Gijón municipal location catalog](https://opendata.gijon.es/descargar.php?id=749&tipo=XHTML)

Weather data: Open-Meteo, CC BY 4.0. Static coordinates are bundled, not fetched from
the municipal catalog at runtime. Nearby locations can share forecast grids.

The marine documentation has inconsistent horizon descriptions. A live check on
2 September 2026 accepted `forecast_days=16` and returned 384 timestamps but only
218 non-null wave values and 224 sea-temperature values. The app uses actual field
availability, not the requested duration, to decide whether it can rate a day.
