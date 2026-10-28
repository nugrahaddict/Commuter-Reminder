package com.personaldevelopment.commuterreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaldevelopment.commuterreminder.data.StationRepository
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.tracking.AppLocation
import com.personaldevelopment.commuterreminder.tracking.LocationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RouteUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val stations: List<Station> = emptyList(),
    val origin: Station? = null,
    val destination: Station? = null,
    val isTracking: Boolean = false,
    val error: String? = null,
    val currentLocation: AppLocation? = null
)

class RouteViewModel(
    private val repository: StationRepository,
    private val locationClient: LocationClientSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    init {
        load()
        observeLocation()
    }

    private fun observeLocation() {
        viewModelScope.launch {
            try {
                locationClient.updates().collect { location ->
                    _uiState.value = _uiState.value.copy(currentLocation = location)
                }
            } catch (_: SecurityException) {
                // permission belum diberikan; peta tetap tampil tanpa posisi
            } catch (_: Exception) {
                // sumber lokasi gagal (misal play services tidak tersedia)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                repository.ensureSeeded()
                val stations = repository.getAll()
                _uiState.value = RouteUiState(
                    isLoading = false,
                    lines = stations.map { it.line }.distinct().sorted(),
                    stations = stations
                )
            } catch (e: Exception) {
                _uiState.value = RouteUiState(
                    isLoading = false,
                    error = "Gagal memuat data stasiun: ${e.message}"
                )
            }
        }
    }

    fun selectOrigin(station: Station) {
        _uiState.value = _uiState.value.copy(origin = station)
        validateSelection()
    }

    fun selectDestination(station: Station) {
        _uiState.value = _uiState.value.copy(destination = station)
        validateSelection()
    }

    fun setTracking(isTracking: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = isTracking)
    }

    private fun validateSelection() {
        val state = _uiState.value
        val origin = state.origin
        val destination = state.destination
        if (origin != null && destination != null &&
            origin.id == destination.id
        ) {
            _uiState.value = state.copy(error = "Stasiun keberangkatan dan tujuan tidak boleh sama")
        } else {
            _uiState.value = state.copy(error = null)
        }
    }

    fun stationsForRoute(): List<Station> {
        val state = _uiState.value
        val origin = state.origin ?: return emptyList()
        val destination = state.destination ?: return emptyList()
        return state.stations.filter {
            it.line == origin.line &&
                it.orderIndex >= minOf(origin.orderIndex, destination.orderIndex) &&
                it.orderIndex <= maxOf(origin.orderIndex, destination.orderIndex)
        }
    }
}

fun interface LocationClientSource {
    fun updates(): kotlinx.coroutines.flow.Flow<AppLocation>
}
