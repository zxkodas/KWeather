package app.kweather

import android.app.Application
import app.kweather.data.WeatherRepository
import app.kweather.data.alerts.AlertsRepository
import app.kweather.data.alerts.SmnAlertProvider
import app.kweather.data.location.LocationProvider
import app.kweather.data.places.PlacesRepository
import app.kweather.data.settings.SettingsRepository
import app.kweather.data.openmeteo.OpenMeteoApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.serialization.json.Json

/** Manual dependency wiring; the app is small enough not to need a DI framework. */
class KWeatherApp : Application() {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    val http by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
            defaultRequest {
                header("User-Agent", "KWeather/${BuildConfig.VERSION_NAME} (Android weather app)")
            }
        }
    }

    private val openMeteo by lazy { OpenMeteoApi(http) }

    val weatherRepository by lazy { WeatherRepository(openMeteo, filesDir, json) }

    val placesRepository by lazy { PlacesRepository(this, openMeteo, json) }

    val locationProvider by lazy { LocationProvider(this) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val alertsRepository by lazy {
        AlertsRepository(listOf(SmnAlertProvider(http, filesDir)))
    }
}
