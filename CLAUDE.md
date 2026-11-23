# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

KWeather: an ad-free Android weather app (Kotlin, Jetpack Compose, single `app` module, package `app.kweather`). Data-heavy, especially wind; official alerts; animated per-condition scenes.

## Commands

Needs JDK 17+ (`jvmToolchain(17)`) and the Android SDK with compile SDK 37.2. If no system JDK is set up, point `JAVA_HOME` at one before running Gradle.

```bash
./gradlew assembleDebug                      # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                    # R8-minified, signed with the debug key (alpha releases)
./gradlew testDebugUnitTest                  # all JVM unit tests
./gradlew testDebugUnitTest --tests 'app.kweather.AlertsTest'                 # one class
./gradlew testDebugUnitTest --tests 'app.kweather.UnitsTest.temperatures'     # one test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

There is no lint/format task configured and no emulator setup; the app is checked on a real phone over adb. Unit tests are plain JVM (JUnit 4, Ktor `MockEngine`, `kotlinx-coroutines-test`); there are no instrumented tests. SMN CAP fixtures live in `app/src/test/resources/smn/`.

Versions are pinned in `gradle/libs.versions.toml`. AGP 9 provides built-in Kotlin, so there is no `org.jetbrains.kotlin.android` plugin; only the Compose and serialization compiler plugins are applied.

## Architecture

No DI framework: `KWeatherApp` (the `Application`) wires one Ktor/OkHttp client and the repositories; ViewModels get them through `viewModelFactory` + `APPLICATION_KEY`.

**Data flow.** `PlacesRepository` (DataStore) holds saved places, the "use current location" flag and the last GPS place. `HomeViewModel` combines that with per-place forecast data, location state and alerts into `HomeUiState.pages: List<PageState>` — one page for current location (if enabled, key `Place.CURRENT_ID`) followed by saved places. On place changes it loads each place's disk cache first, then refreshes; the current-location page refreshes only after a location fix. `WeatherRepository` caches the raw Open-Meteo JSON per place (keyed by rounded lat/lon) and `prune()`s caches for removed places.

**Navigation** lives entirely in `ui/AppRoot.kt`: a saved string back stack (`home/places/settings/demoList/demo`) plus an open-alert overlay (`"pageKey|alertId"`), no navigation library. `PageActions` keeps `WeatherPage` free of navigation/permission code; the demo gallery reuses `WeatherPage` with its own actions.

**Scenes and theming.** `SceneSpec.from(weather)` maps the WMO condition + time of day (dawn/day/dusk/night from sunrise/sunset; daylight 0 = polar night, ~24 h = midnight sun) + wind/precip/moon phase. `ScenePalette.forSpec` derives every screen color from the scene; `KWeatherTheme` exposes it as `LocalPalette`. `WeatherScene` draws everything in one Compose `Canvas` from shared layers on a 390-unit stage; particle slant/drift/cloud speed follow real wind. Time is read only inside the draw lambda (redraw, not recomposition). Motion is off if the app setting or Android's animator scale says so. Hero text color and the veil behind it (`heroInk`/`heroScrim`) are computed per palette; `ContrastTest` checks hero, card and wind-panel colors for every `SkyType` × `TimeOfDay`, so run it after any palette change.

**Units.** API data is always metric. All display formatting goes through `ui/settings/Fmt` (from `AppSettings`, provided as `LocalFmt` at the root): temperatures, temperature deltas, wind, pressure, rain, distance, and 12/24 h times. Don't format units or times directly in cards.

**Alerts.** `AlertsRepository` asks each `AlertProvider` whose `covers(place)` matches, then filters by point-in-polygon, drops expired ones, de-duplicates (same event/level/window across zones) and sorts by severity. `SmnAlertProvider` (Argentina) reads the RSS index at `ssl.smn.gob.ar/CAP/AR.php` — one CAP file per zone, often 100+ — downloads each file once into `files/smn_cap/` (names include timestamps and never change), retries failed downloads, and throttles the index to every 5 minutes. SMN reportedly allows ~200 XML requests per 10 minutes, so keep it cached. `CapParser` uses DOM (works on JVM tests and Android) and accepts both SMN's default-namespace and `cap:`-prefixed documents. CAP severity maps to SMN colors (Moderate→yellow, Severe→orange, Extreme→red), overridden by a color word in the headline. New national sources should implement `AlertProvider`.

**Location** uses the platform `LocationManager` (no Play Services) with coarse permission only, rounds to 0.01° (cache stability and privacy), and names the place with `Geocoder`.

## Design rules

- One fixed home layout for every condition and place, in this order: alerts, scene/hero, 24 h chart, wind panel, six detail tiles, the single conditional comfort card (wind chill or heat index), sun and moon, 10-day forecast. Only the scene and palette change with the weather; don't add or reorder cards per condition.
- Scene art is original and drawn in code; no bitmaps.
- Anything that moves in the scene follows the weather (wind, gusts, precipitation); see `DESIGN.md` for direction and dials.
- Figures (temperatures, wind, tile values, chart labels) use `Figures`/`NumberFamily` from `ui/theme/Type.kt`; words stay in the system font.
- The demo gallery (`ui/demo/DemoData.kt`) is the way to preview designs; keep it covering every `SkyType`, `TimeOfDay` and alert level (`UnitsTest` checks the first two).
- UI text is English; SMN alert text is shown as issued (Spanish) with CC BY 4.0 attribution.

## Repository conventions

- Commit messages, PR descriptions and release notes must not contain AI co-author trailers or "Generated with Claude Code"-style lines.
- Releases are GitHub pre-releases tagged `vX.Y.Z-alpha`, with the release APK attached as `KWeather-X.Y.Z-alpha.apk`; bump `versionCode`/`versionName` in `app/build.gradle.kts` first.
