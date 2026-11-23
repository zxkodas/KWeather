package app.kweather.data.places

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.kweather.data.openmeteo.GeocodingResponse
import app.kweather.data.openmeteo.OpenMeteoApi
import app.kweather.data.openmeteo.toPlaces
import app.kweather.domain.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.placesStore by preferencesDataStore(name = "places")

data class PlacesState(
    /** Whether the first page follows the phone's location. */
    val useCurrentLocation: Boolean,
    /** Last resolved GPS place, kept so the current page works offline. */
    val currentPlace: Place?,
    val saved: List<Place>,
    /** False until the user has picked GPS or a city at least once. */
    val onboarded: Boolean,
)

class PlacesRepository(
    private val context: Context,
    private val api: OpenMeteoApi,
    private val json: Json,
) {
    private val savedKey = stringPreferencesKey("saved")
    private val currentKey = stringPreferencesKey("current")
    private val useCurrentKey = booleanPreferencesKey("use_current")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val listSerializer = ListSerializer(Place.serializer())

    val state: Flow<PlacesState> = context.placesStore.data.map { prefs ->
        PlacesState(
            useCurrentLocation = prefs[useCurrentKey] ?: false,
            currentPlace = prefs[currentKey]?.let { runCatching { json.decodeFromString(Place.serializer(), it) }.getOrNull() },
            saved = prefs[savedKey]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList(),
            onboarded = prefs[onboardedKey] ?: false,
        )
    }

    suspend fun setUseCurrentLocation(enabled: Boolean) {
        context.placesStore.edit {
            it[useCurrentKey] = enabled
            it[onboardedKey] = true
        }
    }

    suspend fun setCurrentPlace(place: Place) {
        context.placesStore.edit { it[currentKey] = json.encodeToString(Place.serializer(), place) }
    }

    suspend fun add(place: Place) {
        context.placesStore.edit { prefs ->
            val list = decode(prefs[savedKey])
            if (list.none { it.id == place.id }) {
                prefs[savedKey] = json.encodeToString(listSerializer, list + place)
            }
            prefs[onboardedKey] = true
        }
    }

    suspend fun remove(id: String) {
        context.placesStore.edit { prefs ->
            prefs[savedKey] = json.encodeToString(listSerializer, decode(prefs[savedKey]).filterNot { it.id == id })
        }
    }

    suspend fun move(id: String, delta: Int) {
        context.placesStore.edit { prefs ->
            val list = decode(prefs[savedKey]).toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from >= 0 && to in list.indices) {
                list.add(to, list.removeAt(from))
                prefs[savedKey] = json.encodeToString(listSerializer, list)
            }
        }
    }

    suspend fun search(query: String): List<Place> {
        if (query.isBlank()) return emptyList()
        val response = json.decodeFromString<GeocodingResponse>(api.searchJson(query.trim()))
        return response.results.toPlaces()
    }

    private fun decode(raw: String?): List<Place> =
        raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
}
