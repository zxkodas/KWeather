package app.kweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kweather.KWeatherApp
import app.kweather.data.WeatherRepository
import app.kweather.domain.Place
import app.kweather.domain.Weather
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class HomeUiState(
    val weather: Weather? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(private val repository: WeatherRepository) : ViewModel() {

    // Saved places and GPS come in a later step; Buenos Aires is the starting place.
    private val place = Place("Buenos Aires", "Argentina", -34.6037, -58.3816)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cached(place)?.let { cached -> _state.update { it.copy(weather = cached) } }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val weather = repository.refresh(place)
                _state.update { it.copy(weather = weather, refreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _state.update { it.copy(refreshing = false, error = "No connection. Showing the last forecast.") }
            } catch (e: ResponseException) {
                _state.update { it.copy(refreshing = false, error = "The forecast service returned an error (${e.response.status.value}).") }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, error = "Couldn't read the forecast: ${e.message}") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KWeatherApp
                HomeViewModel(app.weatherRepository)
            }
        }
    }
}
