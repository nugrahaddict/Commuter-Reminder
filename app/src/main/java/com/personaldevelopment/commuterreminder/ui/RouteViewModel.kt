package com.personaldevelopment.commuterreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaldevelopment.commuterreminder.data.StationRepository
import com.personaldevelopment.commuterreminder.domain.ReminderSettings
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.tracking.AppLocation
import com.personaldevelopment.commuterreminder.tracking.LocationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RouteUiState(
    val isLoading: Boolean = true,
    val stations: List<Station> = emptyList(),
    val destination: Station? = null,
    val isTracking: Boolean = false,
    val error: String? = null,
    val currentLocation: AppLocation? = null,
    val radiusKm: Double = ReminderSettings.DEFAULT_ACTIVATION_RADIUS_KM
)

class RouteViewModel(
    private val repository: StationRepository,
    private val locationClient: LocationClientSource,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private var locationJob: kotlinx.coroutines.Job? = null

    init {
        // radius tersimpan (persistensi) jadi nilai awal UI
        com.personaldevelopment.commuterreminder.data.RadiusSettingsStore.applySaved(appContext)
        _uiState.value = _uiState.value.copy(radiusKm = ReminderSettings.ACTIVATION_RADIUS_KM)
        load()
        observeLocation()
    }

    private fun observeLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
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

    /** Dipanggil ulang setelah izin lokasi disetujui agar pin posisi muncul. */
    fun retryLocation() {
        observeLocation()
    }

    fun load() {
        viewModelScope.launch {
            try {
                repository.ensureSeeded()
                val stations = repository.getAll()
                _uiState.value = RouteUiState(
                    isLoading = false,
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

    fun selectDestination(station: Station) {
        _uiState.value = _uiState.value.copy(destination = station, error = null)
    }

    fun setTracking(isTracking: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = isTracking)
    }

    /** Terapkan radius aktivasi (km) hasil input user; service membaca nilainya saat start. */
    /** Terapkan radius aktivasi (km) hasil input user, simpan ke SharedPreferences. */
    fun setRadiusKm(km: Double) {
        ReminderSettings.ACTIVATION_RADIUS_KM = km
        viewModelScope.launch {
            com.personaldevelopment.commuterreminder.data.RadiusSettingsStore.save(appContext, km)
        }
        _uiState.value = _uiState.value.copy(radiusKm = km)
    }

    /** Simpan koreksi koordinat satu stasiun lalu muat ulang daftar. */
    fun updateStationCoordinates(station: Station, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                repository.updateCoordinates(station.id, latitude, longitude)
                val stations = repository.getAll()
                _uiState.value = _uiState.value.copy(
                    stations = stations,
                    // refresh stasiun tujuan jika yang diedit sedang jadi tujuan
                    destination = _uiState.value.destination?.let { dest ->
                        if (dest.id == station.id) {
                            stations.firstOrNull { it.id == station.id } ?: dest
                        } else {
                            dest
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Gagal menyimpan koordinat: ${e.message}"
                )
            }
        }
    }
}

fun interface LocationClientSource {
    fun updates(): kotlinx.coroutines.flow.Flow<AppLocation>
}
