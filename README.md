# Outing

A small native Android app for planning a beach visit or a walk around town.

## Download

**[Download the latest APK](https://github.com/maximbetin/outing/releases/latest/download/outing-debug.apk)**
· [All releases](https://github.com/maximbetin/outing/releases)

Requires Android 8.0 or newer. This is a personal test build, not a Play Store release.
For new-build notifications in GitHub Mobile, watch this repository with **Releases** enabled.
Older builds signed with another key require uninstalling first, which removes local app data.

## Use

1. Choose a date and **Beach** or **Hiking** to compare Gijón, Oviedo, and Avilés.
2. Each town shows its daylight rating and a separate best three-hour window.
3. Tap a town for hourly results, then an hour for its weather values and score explanation.
4. Use **Refresh** to bypass the one-hour forecast cache.

Beach uses town weather plus available sea data: San Lorenzo for Gijón and nearby
Salinas (Castrillón) for Avilés. Oviedo is a weather-only estimate for outdoor leisure,
not a pool or river forecast. Hiking uses town weather, not route or elevation conditions.
See [Scoring rules](docs/SCORING.md) for how the ratings work.

Built with Kotlin and Jetpack Compose. No accounts or backend.
Weather: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.
Ratings estimate comfort, not safety; check local warnings and conditions.
