package app.kweather.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kweather.KWeatherApp
import app.kweather.data.places.PlacesRepository
import app.kweather.domain.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val places: List<Place>) : SearchState
    data class Failed(val message: String) : SearchState
}

class PlacesViewModel(private val repo: PlacesRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val search: StateFlow<SearchState> = _query
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.trim().length < 2) {
                    emit(SearchState.Idle)
                    return@flow
                }
                emit(SearchState.Searching)
                try {
                    emit(SearchState.Results(repo.search(q)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(SearchState.Failed("Search failed. Check your connection."))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clear() {
        _query.value = ""
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KWeatherApp
                PlacesViewModel(app.placesRepository)
            }
        }
    }
}
