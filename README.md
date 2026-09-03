# Outing

A small native Android app for choosing a good day for the **beach** or a **hike**.

## Download

**[Download the Android APK — v0.4.1](https://github.com/maximbetin/outing/releases/download/v0.4.1/outing-debug.apk)**

[All releases](https://github.com/maximbetin/outing/releases)

Requires Android 8.0 or newer. This is an early test build, not a Play Store release.
Updating from v0.3.0 requires a one-time uninstall because CI signing was corrected.
Uninstalling removes the app's local data.

## Features

- Beach and Hiking outlooks, starting with **Gijón, Asturias**.
- A full-day rating plus a separately rated best three-hour window.
- Weather summaries, numeric scores, and an hour-by-hour outlook for both activities.
- Daylight-only recommendations across the available forecast.
- Open Score details for weather conditions and an expandable score explanation.
- Tap Best 3 hours or an individual hour to inspect its forecast.
- Choose among Gijón's beaches, with San Lorenzo selected initially.
- One-hour local caching with manual refresh.

Beach uses the selected beach's weather and sea conditions. Hiking uses town-level weather;
it does not assess specific routes, terrain, or elevation.

Built with Kotlin and Jetpack Compose. No accounts or backend.

Weather data: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.

Ratings estimate suitability, not safety. Check beach flags and local conditions.
