# Hay Plan

A small native Android app for planning a beach visit or a walk around town.

## Download

**[Download the latest APK](https://github.com/maximbetin/hay-plan/releases/latest/download/hay-plan.apk)**
In GitHub Mobile, open this repository → Releases → Latest → **hay-plan.apk**.
Open the downloaded file and allow installation from that app when Android asks.

Requires Android 8.0 or newer. The first Hay Plan install is separate from earlier app versions; subsequent Hay Plan releases update it normally.
For new-build notifications in GitHub Mobile, watch this repository with **Releases** enabled.
Older builds signed with another key require uninstalling first, which removes local app data.

## Use

1. Choose a date and **Beach** or **Hiking** to see the top five towns ranked by daylight score. Tap **Show all** for the full ordered list.
2. Each town shows its daylight rating and a separate best three-hour window. Unavailable days appear last.
3. Tap a town for hourly results, then an hour for its weather values and score explanation.
4. Use **Refresh** to bypass the one-hour forecast cache.

Towns: Gijón, Oviedo, Avilés, Luanco, Llanes, Ribadesella, Candás, Luarca, and Cangas de Onís.

Beach uses town weather plus available sea data from one named reference:
San Lorenzo (Gijón), Salinas in Castrillón (Avilés), Luanco, El Sablón (Llanes),
Santa Marina (Ribadesella), La Palmera (Candás), and Primera y Segunda de Luarca.
Oviedo and Cangas de Onís use labelled weather-only estimates for outdoor leisure,
not pool or river forecasts. Hiking uses town weather, not route or elevation conditions.
See [Scoring rules](docs/SCORING.md) for how the ratings work.

Built with Kotlin and Jetpack Compose. No accounts or backend.
Weather: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.
Ratings estimate comfort, not safety; check local warnings and conditions.
