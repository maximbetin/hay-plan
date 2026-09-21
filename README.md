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

1. Choose a date and **Beach** or **Hiking**, then rank the other locations by the whole day or their best three-hour opportunity. Gijón, Oviedo, and Avilés appear first in that fixed order, followed by the ten highest-ranked remaining locations without duplicates. After sunset the app opens on tomorrow; today stays available in the date strip.
2. Each card shows its comfort rating, essential conditions, timed important warnings, and separate best three-hour window. Coastal Beach cards show air and water temperatures plus waves; inland estimates are clearly labelled.
3. Tap a location for hourly results and score explanations. Date and activity changes keep that location open.
4. Use **Refresh** to bypass the one-hour forecast cache. If updating fails, saved data is used for at most 12 hours and clearly labelled.
5. The app follows English or Spanish system language by default. Open **Settings** to override it; the choice is saved on the phone. Light and dark system themes are supported.
6. Optionally enable the **Daily plan notification** in Settings and choose a time. Around that time in Asturias, a compact notification recommends Beach or walking for Gijón and walking for Oviedo; tap it to open today’s overview.

Dates three through six use rating bands on cards and charts, with exact heuristic scores available only inside calculation details. Dates seven days away and later are lower-confidence outlooks that also hide exact scores and best-window timing.

Locations: Gijón, Oviedo, Avilés, Luanco, Llanes, Ribadesella, Candás, Luarca,
Cangas de Onís, Salinas, Cudillero, Tapia de Casariego, Villaviciosa, Somiedo, and Redes.

Beach uses weather and available sea data from one named coastal reference:
San Lorenzo (Gijón), Salinas in Castrillón (Avilés and Salinas), Luanco, El Sablón (Llanes),
Santa Marina (Ribadesella), La Palmera (Candás), Primera y Segunda de Luarca,
[Concha de Artedo](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-la-concha-de-artedo) (Cudillero),
[Anguileiro](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-anguileiro) (Tapia de Casariego),
and [Rodiles](https://www.turismoasturias.es/es/descubre/costa/playas/playa-de-rodiles) (Villaviciosa).
Oviedo, Cangas de Onís, Somiedo, and Redes use labelled weather-only estimates for
outdoor leisure, not pool or river forecasts. Somiedo uses **Pola de Somiedo** weather;
Redes uses **Campo de Caso**. These references are shown on cards and details.
Hiking uses town and nearby-area weather for walks and ordinary local routes, not an exact
trail, mountain or elevation forecast. Scores include apparent temperature, rain amount and
chance, sustained wind and gusts, cloud cover, humidity, visibility, UV and severe weather.
See [Scoring rules](docs/SCORING.md) for how the ratings work.

Built with Kotlin and Jetpack Compose. No accounts or backend.
Weather: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.
Ratings estimate comfort, not safety; severe conditions remain visible even when the overall average is high. Check local warnings and conditions.
