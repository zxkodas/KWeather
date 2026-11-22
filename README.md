# KWeather

An ad-free Android weather app with a lot of data, especially wind.

- Animated scene for the current conditions (clear, clouds, fog, drizzle, rain, storm, snow; dawn, day, dusk, night). Rain slant, snow drift and cloud speed follow the real wind.
- One fixed layout: 24-hour chart (temperature, rain chance, wind), wind panel (compass, gusts, wind at 120 m, 12-hour gust chart), six detail tiles, wind chill or heat index when relevant, sun and moon, 10-day forecast.
- Card colors come from the current scene.
- Offline cache of the last forecast.

Planned: GPS and saved places, official alerts (SMN Argentina first, then Environment Canada, NWS and other national CAP feeds), home screen widget, settings.

## Data

- Forecast and air quality: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)

## Build

Requires JDK 17+ and the Android SDK (compile SDK 37).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests:

```bash
./gradlew testDebugUnitTest
```
