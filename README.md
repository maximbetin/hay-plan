# Outing

A small native Android app for choosing a good day for the **beach** or a **hike**.

## Download

**[Download the Android APK](https://github.com/maximbetin/outing/releases/download/v0.2.0/outing-debug.apk)**

[All releases](https://github.com/maximbetin/outing/releases)

Requires Android 8.0 or newer. This is an early test build, not a Play Store release.
Updates signed with a different key may require uninstalling the previous version,
which removes its local data.

## Features

- Beach and Hiking outlooks, starting with **Gijón, Asturias**.
- A full-day rating plus a separate best time window.
- Numeric scores and an hour-by-hour outlook for both activities.
- Daylight-only recommendations across the available forecast.
- Tap a day, time window, or hour to inspect its score and weather factors.
- Choose among Gijón's beaches, with San Lorenzo selected initially.
- One-hour local caching with manual refresh.

Beach uses the selected beach's weather and sea conditions. Hiking uses town-level weather;
it does not assess specific routes, terrain, or elevation.

Built with Kotlin and Jetpack Compose. No accounts or backend.

Weather data: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.

Ratings estimate suitability, not safety. Check beach flags and local conditions.
