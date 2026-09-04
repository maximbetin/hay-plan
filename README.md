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

1. Choose a date and **Beach** or **Hiking** to see the top five locations ranked by daylight score. Beach ranks coastal locations first and keeps inland alternatives under **Show all**; after sunset, use **View tomorrow**.
2. Each card shows its rating, essential conditions, and separate best three-hour window. Coastal Beach cards show air and water temperatures plus waves; inland estimates are clearly labelled.
3. Tap a location for hourly results and score explanations. Date and activity changes keep that location open.
4. Use **Refresh** to bypass the one-hour forecast cache.
5. Open **Settings** to switch between English and Spanish. The choice is saved on the phone.

Locations: Gijón, Oviedo, Avilés, Luanco, Llanes, Ribadesella, Candás, Luarca,
Cangas de Onís, Salinas, Cudillero, Tapia de Casariego, Villaviciosa, Somiedo, and Redes.

Beach uses town weather plus available sea data from one named reference:
San Lorenzo (Gijón), Salinas in Castrillón (Avilés and Salinas), Luanco, El Sablón (Llanes),
Santa Marina (Ribadesella), La Palmera (Candás), Primera y Segunda de Luarca,
[Concha de Artedo](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-la-concha-de-artedo) (Cudillero),
[Anguileiro](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-anguileiro) (Tapia de Casariego),
and [Rodiles](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-rodiles) (Villaviciosa).
Oviedo, Cangas de Onís, Somiedo, and Redes use labelled weather-only estimates for
outdoor leisure, not pool or river forecasts. Somiedo uses **Pola de Somiedo** weather;
Redes uses **Campo de Caso**. These references are shown on cards and details.
Hiking uses town weather, not conditions across an entire park, route, or elevation range.
See [Scoring rules](docs/SCORING.md) for how the ratings work.

Built with Kotlin and Jetpack Compose. No accounts or backend.
Weather: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.
Ratings estimate comfort, not safety; check local warnings and conditions.
