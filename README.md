# KWeather

An ad-free Android weather app with a lot of data, especially wind.

- Animated scene for the current conditions (clear, clouds, fog, drizzle, rain, storm, snow; dawn, day, dusk, night). Rain slant, snow drift and cloud speed follow the real wind.
- One fixed layout: 24-hour chart (temperature, rain chance, wind), wind panel (compass, gusts, wind at 120 m, 12-hour gust chart), six detail tiles, wind chill or heat index when relevant, sun and moon, 10-day forecast.
- Card colors come from the current scene.
- Current location (approximate, via Android's own location service; no Play Services needed) plus saved cities, swiped between as pages.
- City search, reordering and removal from the Places screen.
- Offline cache of the last forecast for every place.
- Official weather alerts from Argentina's SMN, matched to each place by the alert's exact polygon, with a detail screen.
- Settings: temperature, wind, pressure, rain and distance units, 12/24-hour clock, animations on or off.
- Demo gallery (Settings → Developer) with example cities for every scene, alert level and card.

Planned: alert notifications, more alert sources (Environment Canada, NWS and other national CAP feeds), home screen widget.

## Data

- Forecast, air quality and city search: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)
- Weather alerts for Argentina: [Servicio Meteorológico Nacional](https://www.smn.gob.ar/alertas) (CC BY 4.0)

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
