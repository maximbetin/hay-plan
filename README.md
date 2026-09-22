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

1. Choose **Beach** or **Walk** (in Spanish, *Playita* and *Paseíto*) and a date. Locations rank by their whole-day comfort; Gijón, Oviedo, and Avilés appear first in that fixed order, followed by the ten highest-ranked remaining locations without duplicates. After sunset the app opens on tomorrow; today stays available in the date strip.
2. Choose **Week**, the first chip in the date strip, to see every location across the next seven days as coloured cells, with the best place and day called out on top. If nothing looks Good, it offers the other activity. Tap a cell to open that place on that day; Back returns to the week.
3. Each card shows its comfort rating, essential conditions rounded to whole degrees, its best three-hour window and the most important timed warning. Coastal Beach cards show air and water temperatures plus waves; inland estimates are clearly labelled.
4. Tap a location for its 7-day chart, hourly results with the best three hours highlighted, and score explanations. Tap the place name to switch to another location without going back. Date and activity changes keep that location open.
5. Use **Refresh** to bypass the one-hour forecast cache. If updating fails, saved data is used for at most 12 hours and clearly labelled.
6. The app follows English or Spanish system language by default. Open **Settings** to override it; the choice is saved on the phone. Light and dark system themes are supported.
7. Optionally enable the **Daily plan notification** in Settings and choose a time. Around that time in Asturias, a compact notification recommends Beach or walking for Gijón and walking for Oviedo; tap it to open today’s overview.

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
Redes uses **Campo de Caso**. These references are shown in the location detail; cards only
flag inland estimates and missing sea data.
Walk uses town and nearby-area weather for walks and ordinary local routes, not an exact
trail, mountain or elevation forecast. Scores include apparent temperature, rain amount and
chance, sustained wind and gusts, cloud cover, humidity, visibility, UV and severe weather.
See [Scoring rules](docs/SCORING.md) for how the ratings work.

Built with Kotlin and Jetpack Compose. No accounts or backend.
Weather: [Open-Meteo](https://open-meteo.com/) · CC BY 4.0.
Ratings estimate comfort, not safety; severe conditions remain visible even when the overall average is high. Check local warnings and conditions.
