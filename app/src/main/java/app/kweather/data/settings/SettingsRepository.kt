package app.kweather.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.kweather.ui.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val temp = stringPreferencesKey("temperature")
    private val wind = stringPreferencesKey("wind")
    private val pressure = stringPreferencesKey("pressure")
    private val precip = stringPreferencesKey("precipitation")
    private val distance = stringPreferencesKey("distance")
    private val clock24h = booleanPreferencesKey("clock_24h")
    private val animations = booleanPreferencesKey("animations")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            temperature = p.enum(temp, d.temperature),
            wind = p.enum(wind, d.wind),
            pressure = p.enum(pressure, d.pressure),
            precipitation = p.enum(precip, d.precipitation),
            distance = p.enum(distance, d.distance),
            clock24h = p[clock24h] ?: d.clock24h,
            animations = p[animations] ?: d.animations,
        )
    }

    suspend fun update(s: AppSettings) {
        context.settingsStore.edit {
            it[temp] = s.temperature.name
            it[wind] = s.wind.name
            it[pressure] = s.pressure.name
            it[precip] = s.precipitation.name
            it[distance] = s.distance.name
            it[clock24h] = s.clock24h
            it[animations] = s.animations
        }
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
