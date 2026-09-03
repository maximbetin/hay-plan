# Outing

A small native Android app for choosing a good day for the **beach** or a **hike**.

## Download

**[Download the latest Android APK](https://github.com/maximbetin/outing/releases/latest/download/outing-debug.apk)**

[All releases](https://github.com/maximbetin/outing/releases)

To receive new-build alerts in GitHub Mobile, watch this repository with the
**Releases** option enabled.

Requires Android 8.0 or newer. This is an early test build, not a Play Store release.
Updating from v0.3.0 requires a one-time uninstall because CI signing was corrected.
Uninstalling removes the app's local data.

## Features

- Beach and Hiking outlooks for **Gijón**, **Oviedo**, and **Avilés**.
- Choose a date and activity to compare all towns, then tap a card for hourly details.
- A full-day rating plus a separately rated best three-hour window.
- Weather summaries, numeric scores, and an hour-by-hour outlook for both activities.
- Daylight-only recommendations across the available forecast.
- Open Score details for weather conditions and an expandable score explanation.
- Tap Best 3 hours or an individual hour to inspect its forecast.
- One named coastal reference where relevant: San Lorenzo for Gijón and nearby
  Salinas (Castrillón) for Avilés. No beach selector.
- One-hour local caching with manual refresh.

Beach uses town weather plus available sea data for coastal locations. Inland it
is a weather-only estimate for outdoor leisure, not a pool or river conditions forecast.
Ratings clearly indicate the data used. Hiking uses town-level weather; it does
not assess specific routes, terrain, or elevation.

Built with Kotlin and Jetpack Compose. No accounts or backend.

Weather data: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.

Ratings estimate suitability, not safety. Check beach flags and local conditions.
