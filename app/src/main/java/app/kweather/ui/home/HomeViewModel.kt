package app.kweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kweather.KWeatherApp
import app.kweather.data.WeatherRepository
import app.kweather.data.alerts.AlertsRepository
import app.kweather.data.alerts.PlaceAlerts
import app.kweather.data.location.LocationProvider
import app.kweather.data.location.LocationResult
import app.kweather.data.places.PlacesRepository
import app.kweather.data.places.PlacesState
import app.kweather.domain.Alert
import app.kweather.domain.Place
import app.kweather.domain.Weather
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class LocationIssue(val message: String) {
    NoPermission("KWeather doesn't have permission to use your location."),
    LocationOff("Location is turned off on this phone."),
    Unavailable("Couldn't find your location right now."),
}

data class PageState(
    val key: String,
    /** Null only for the current-location page before the first fix. */
    val place: Place?,
    val isCurrent: Boolean,
    val weather: Weather?,
    val refreshing: Boolean,
    val error: String?,
    val locationIssue: LocationIssue?,
    /** Official alerts covering this place, most severe first. */
    val alerts: List<Alert> = emptyList(),
    /** True when an alert source failed, so alerts may be missing. */
    val alertsUnavailable: Boolean = false,
)

data class HomeUiState(
    val ready: Boolean = false,
    val onboarded: Boolean = false,
    val useCurrentLocation: Boolean = false,
    val saved: List<Place> = emptyList(),
    val pages: List<PageState> = emptyList(),
    val locationIssue: LocationIssue? = null,
)

private data class PageData(
    val weather: Weather? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val weatherRepo: WeatherRepository,
    private val placesRepo: PlacesRepository,
    private val location: LocationProvider,
    private val alertsRepo: AlertsRepository,
) : ViewModel() {

    private val data = MutableStateFlow<Map<String, PageData>>(emptyMap())
    private val locating = MutableStateFlow(false)
    private val issue = MutableStateFlow<LocationIssue?>(null)
    private val alerts = MutableStateFlow(PlaceAlerts(emptyMap(), emptySet()))
    private val requested = mutableSetOf<String>()
    private var alertsJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(placesRepo.state, data, locating, issue, alerts) { places, data, locating, issue, alerts ->
        buildState(places, data, locating, issue, alerts)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            if (placesRepo.state.first().useCurrentLocation) locate()
        }
        viewModelScope.launch {
            placesRepo.state.collect { places -> onPlacesChanged(places) }
        }
    }

    private suspend fun onPlacesChanged(places: PlacesState) {
        val active = buildList {
            if (places.useCurrentLocation) places.currentPlace?.let(::add)
            addAll(places.saved)
        }
        for (place in active) {
            if (!requested.add(place.id)) continue
            weatherRepo.cached(place)?.let { cached -> setData(place.id) { it.copy(weather = cached) } }
            // The current page refreshes after each location fix instead.
            if (!place.isCurrent) refresh(place)
        }
        val activeIds = active.map { it.id }.toSet()
        requested.retainAll(activeIds)
        data.update { map -> map.filterKeys { it in activeIds } }
        weatherRepo.prune(active)
        refreshAlerts(active)
    }

    /** The provider throttles itself, so calling this often is cheap. */
    private fun refreshAlerts(places: List<Place>) {
        if (places.isEmpty()) return
        alertsJob?.cancel()
        alertsJob = viewModelScope.launch {
            alerts.value = alertsRepo.alertsFor(places)
        }
    }

    private suspend fun activePlaces(): List<Place> {
        val places = placesRepo.state.first()
        return buildList {
            if (places.useCurrentLocation) places.currentPlace?.let(::add)
            addAll(places.saved)
        }
    }

    /** Pull-to-refresh or retry on a page. */
    fun refresh(page: PageState) {
        if (page.isCurrent) {
            viewModelScope.launch { locate() }
        } else {
            page.place?.let(::refresh)
            viewModelScope.launch { refreshAlerts(activePlaces()) }
        }
    }

    fun setUseCurrentLocation(enabled: Boolean) {
        viewModelScope.launch {
            placesRepo.setUseCurrentLocation(enabled)
            if (enabled) locate() else issue.value = null
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) setUseCurrentLocation(true) else issue.value = LocationIssue.NoPermission
    }

    fun addPlace(place: Place) {
        viewModelScope.launch { placesRepo.add(place) }
    }

    fun removePlace(id: String) {
        viewModelScope.launch { placesRepo.remove(id) }
    }

    fun movePlace(id: String, delta: Int) {
        viewModelScope.launch { placesRepo.move(id, delta) }
    }

    private suspend fun locate() {
        if (locating.value) return
        locating.value = true
        try {
            when (val result = location.locate()) {
                is LocationResult.Found -> {
                    issue.value = null
                    placesRepo.setCurrentPlace(result.place)
                    requested.add(Place.CURRENT_ID)
                    refresh(result.place)
                    refreshAlerts(activePlaces())
                }
                LocationResult.NoPermission -> fallBack(LocationIssue.NoPermission)
                LocationResult.LocationOff -> fallBack(LocationIssue.LocationOff)
                LocationResult.Unavailable -> fallBack(LocationIssue.Unavailable)
            }
        } finally {
            locating.value = false
        }
    }

    /** No fresh fix: keep showing the last known place, refreshed. */
    private suspend fun fallBack(problem: LocationIssue) {
        issue.value = problem
        placesRepo.state.first().currentPlace?.let(::refresh)
    }

    private fun refresh(place: Place) {
        if (data.value[place.id]?.refreshing == true) return
        viewModelScope.launch {
            setData(place.id) { it.copy(refreshing = true, error = null) }
            try {
                val weather = weatherRepo.refresh(place)
                setData(place.id) { it.copy(weather = weather, refreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                setData(place.id) { it.copy(refreshing = false, error = "No connection. Showing the last forecast.") }
            } catch (e: ResponseException) {
                setData(place.id) { it.copy(refreshing = false, error = "The forecast service returned an error (${e.response.status.value}).") }
            } catch (e: Exception) {
                setData(place.id) { it.copy(refreshing = false, error = "Couldn't read the forecast: ${e.message}") }
            }
        }
    }

    private fun setData(key: String, change: (PageData) -> PageData) {
        data.update { map -> map + (key to change(map[key] ?: PageData())) }
    }

    private fun buildState(
        places: PlacesState,
        data: Map<String, PageData>,
        locating: Boolean,
        issue: LocationIssue?,
        alerts: PlaceAlerts,
    ): HomeUiState {
        val unavailable = alerts.failedSources.isNotEmpty()
        val pages = buildList {
            if (places.useCurrentLocation) {
                val d = data[Place.CURRENT_ID] ?: PageData()
                add(
                    PageState(
                        key = Place.CURRENT_ID,
                        place = places.currentPlace,
                        isCurrent = true,
                        weather = d.weather,
                        refreshing = d.refreshing || locating,
                        error = d.error,
                        locationIssue = issue,
                        alerts = alerts.byPlace[Place.CURRENT_ID].orEmpty(),
                        alertsUnavailable = unavailable,
                    ),
                )
            }
            places.saved.forEach { place ->
                val d = data[place.id] ?: PageData()
                add(
                    PageState(
                        place.id, place, false, d.weather, d.refreshing, d.error, null,
                        alerts = alerts.byPlace[place.id].orEmpty(),
                        alertsUnavailable = unavailable,
                    ),
                )
            }
        }
        return HomeUiState(
            ready = true,
            onboarded = places.onboarded,
            useCurrentLocation = places.useCurrentLocation,
            saved = places.saved,
            pages = pages,
            locationIssue = issue,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KWeatherApp
                HomeViewModel(app.weatherRepository, app.placesRepository, app.locationProvider, app.alertsRepository)
            }
        }
    }
}
