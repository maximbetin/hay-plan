# Hay Plan maintenance guide

## Scope and architecture

- Native Android app using Kotlin and Jetpack Compose; no accounts or backend.
- Keep the current small three-layer structure: `data` for Open-Meteo/cache/catalog,
  `domain` for pure scoring/planning models, and `ui` for state/presentation.
- Prefer focused changes over a framework-heavy architecture rewrite.

## Behavioral contracts

- `docs/SCORING.md` is the scoring contract. Update it with any calculation change.
- Day scores average every expected complete daylight-hour score. Missing required
  weather data invalidates the full-day rating; do not average only known hours.
- The best three-hour window is independent of the day rating and chooses the earliest tie.
- Open-Meteo precipitation probability, precipitation, and gusts describe the preceding
  hour and are deliberately shifted in `OpenMeteoParser`.
- Join weather and marine data by timestamp, never array position or extrapolation.
- Marine inputs are optional for the visible normalized Beach score. Beach ranking uses
  `evidenceScore` so absent optional inputs cannot improve a location's ordering.
- Always surface `primaryWarning`; a high day average must not hide severe conditions.
- Coastal Beach mode uses the named coast reference for weather and marine data. Hiking
  uses the town/reference weather point. Town weather is an explicit Beach fallback only.
- Cached data is fresh for one hour and may be used after a failed refresh for no more
  than 12 hours. Failed sources retry after 15 minutes.
- Open-Meteo requests batch at most five coordinates while preserving individual cache
  entries, validation, stale fallback, and single-coordinate recovery.
- Dates 3-6 are labelled as later outlooks; day 7 onward is labelled long-range.
- Scoring for the selected date runs off the main thread in `HayPlanViewModel` (`HayPlanUiState.planned()`);
  the screen renders `state.plan` and never calls `DayPlanner` itself. Tests that build a state
  directly call `.planned()`.
- When no daylight remains today, the initial date selection is tomorrow; an explicit choice of today is kept.
- Every activity overview shows Gijón, Oviedo and Avilés first in that fixed order,
  followed by the ten highest-ranked remaining locations with no duplicates.
- Daily notifications are opt-in, default to approximately 09:00 Europe/Madrid, and request
  notification permission only when enabled. Oviedo considers Hiking; Gijón prefers a
  complete, fully marine-informed Very Good Beach window and otherwise considers Hiking.
- Preserve the explicit limitations: the score estimates comfort, not safety, and does
  not assess beach flags, currents, tides, water quality, exact trails, or elevation.

## UI and localization

- Preserve the compact phone-first flow: date, activity, ranked cards, then details.
- Maintain English and Spanish behavior, system-language default, decimal localization,
  light/dark contrast, 48dp touch targets, and non-color status labels.
- Warnings, source issues, and unavailable reasons are typed. Do not introduce behavior
  that compares translated or English display sentences.
- The remaining `UiStrings` mapping is presentation-only; migrate it incrementally rather
  than mixing new business logic into strings.

## Verification

On Windows, keep Gradle state inside the workspace to avoid the invalid `C:\.gradle` path:

```powershell
$env:GRADLE_USER_HOME='C:\Users\MBK\hay-plan\.gradle'
.\gradlew.bat --no-daemon --console=plain testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease
```

- Current baseline: 121 JVM tests and 4 instrumented Compose tests, zero failures; Android lint reports no issues.
- `compileDebugAndroidTestKotlin` only compiles the Compose tests. Run `connectedDebugAndroidTest`
  when an emulator or phone is available (the `Pixel_10` AVD on Android 16 works; Espresso is pinned
  to 3.7 because the transitive 3.5 crashes its idle check there).
- Use `git diff --check` and keep the working tree free of generated artifacts.
- A push to `main` triggers `.github/workflows/android.yml` and publishes a latest APK.
  Never push when the user asks not to trigger a release.

## Previous released state

- Released commit: `6d44d51` (`Hay Plan 0.7.0.22`, tag `auto-22`).
- The release workflow completed successfully on 2026-09-08.
- Manual phone checks still useful: narrow width, large font, TalkBack, Spanish, and dark mode.
