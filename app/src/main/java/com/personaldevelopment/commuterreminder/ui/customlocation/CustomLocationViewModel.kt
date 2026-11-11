package com.personaldevelopment.commuterreminder.ui.customlocation

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaldevelopment.commuterreminder.data.CustomLocationStore
import com.personaldevelopment.commuterreminder.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class SearchResult(val displayName: String, val latitude: Double, val longitude: Double)

data class CustomLocationUiState(
    val isLoading: Boolean = true,
    val pickedLat: Double? = null,
    val pickedLon: Double? = null,
    val pickedLabel: String? = null,
    val radiusText: String = ReminderSettings.DEFAULT_ACTIVATION_RADIUS_KM.toLong().toString(),
    val radiusKm: Double = ReminderSettings.DEFAULT_ACTIVATION_RADIUS_KM,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val focusPoint: Pair<Double, Double>? = null,
    val saved: Boolean = false,
    val activeLocationId: String? = null,
    val history: List<CustomLocationStore.CustomLocation> = emptyList()
)

class CustomLocationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CustomLocationUiState())
    val uiState: StateFlow<CustomLocationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val saved = CustomLocationStore.load(app)
            val history = CustomLocationStore.history(app)
            _uiState.value = if (saved != null) {
                CustomLocationUiState(
                    isLoading = false,
                    pickedLat = saved.latitude,
                    pickedLon = saved.longitude,
                    pickedLabel = saved.label,
                    radiusKm = saved.radiusKm,
                    radiusText = ReminderSettings.formatRadiusInput(saved.radiusKm),
                    focusPoint = saved.latitude to saved.longitude,
                    activeLocationId = saved.id,
                    history = history
                )
            } else {
                CustomLocationUiState(isLoading = false, history = history)
            }
        }
    }

    fun onPick(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            pickedLat = latitude,
            pickedLon = longitude,
            pickedLabel = null,
            activeLocationId = null,
            saved = false
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResults = emptyList())
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        _uiState.value = _uiState.value.copy(isSearching = true, searchResults = emptyList())
        viewModelScope.launch {
            val results = geocode(query)
            _uiState.value = _uiState.value.copy(isSearching = false, searchResults = results)
        }
    }

    fun selectSearchResult(result: SearchResult) {
        _uiState.value = _uiState.value.copy(
            pickedLat = result.latitude,
            pickedLon = result.longitude,
            pickedLabel = result.displayName,
            searchResults = emptyList(),
            focusPoint = result.latitude to result.longitude,
            activeLocationId = null,
            saved = false
        )
    }

    /** Terapkan entri riwayat ke pin + radius (siap dipakai/di-reminder). */
    fun selectHistoryEntry(entry: CustomLocationStore.CustomLocation) {
        _uiState.value = _uiState.value.copy(
            pickedLat = entry.latitude,
            pickedLon = entry.longitude,
            pickedLabel = entry.label,
            radiusKm = entry.radiusKm,
            radiusText = ReminderSettings.formatRadiusInput(entry.radiusKm),
            focusPoint = entry.latitude to entry.longitude,
            activeLocationId = entry.id,
            saved = false
        )
    }

    /** Ganti nama alias entri riwayat (juga label lokasi aktif bila id sama). */
    fun renameAlias(entry: CustomLocationStore.CustomLocation, alias: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            CustomLocationStore.renameAlias(app, entry.id, alias)
            val refreshed = CustomLocationStore.load(app)
            _uiState.value = _uiState.value.copy(
                history = CustomLocationStore.history(app),
                pickedLabel = if (_uiState.value.activeLocationId == entry.id) {
                    refreshed?.label
                } else {
                    _uiState.value.pickedLabel
                }
            )
        }
    }

    fun removeFromHistory(entry: CustomLocationStore.CustomLocation) {
        viewModelScope.launch {
            CustomLocationStore.removeFromHistory(getApplication(), entry.id)
            val app = getApplication<Application>()
            _uiState.value = _uiState.value.copy(
                history = CustomLocationStore.history(app),
                activeLocationId = if (_uiState.value.activeLocationId == entry.id) {
                    null
                } else {
                    _uiState.value.activeLocationId
                }
            )
        }
    }

    fun setRadius(km: Double) {
        _uiState.value = _uiState.value.copy(
            radiusText = ReminderSettings.formatRadiusInput(km),
            radiusKm = km,
            activeLocationId = null,
            saved = false
        )
    }

    fun onRadiusTextChange(text: String) {
        val valid = ReminderSettings.validateRadiusText(text) == null
        _uiState.value = _uiState.value.copy(
            radiusText = text,
            radiusKm = if (valid) text.trim().replace(',', '.').toDouble() else _uiState.value.radiusKm,
            saved = false
        )
    }

    fun save() {
        val state = _uiState.value
        val lat = state.pickedLat ?: return
        val lon = state.pickedLon ?: return
        if (ReminderSettings.validateRadiusText(state.radiusText) != null) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            CustomLocationStore.save(
                context = app,
                latitude = lat,
                longitude = lon,
                radiusKm = state.radiusKm,
                label = state.pickedLabel
            )
            val active = CustomLocationStore.load(app)
            _uiState.value = _uiState.value.copy(
                saved = true,
                history = CustomLocationStore.history(app),
                activeLocationId = active?.id ?: state.activeLocationId
            )
        }
    }

    /** Bersihkan pin di layar (riwayat tetap ada). */
    fun clear() {
        viewModelScope.launch {
            CustomLocationStore.clearActive(getApplication())
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pickedLat = null,
                pickedLon = null,
                pickedLabel = null,
                activeLocationId = null,
                saved = false
            )
        }
    }

    /** Lokasi aktif siap dipakai untuk reminder; null jika belum ada pilihan valid. */
    fun currentSelection(): CustomLocationStore.CustomLocation? {
        val state = _uiState.value
        val lat = state.pickedLat ?: return null
        val lon = state.pickedLon ?: return null
        return CustomLocationStore.CustomLocation(
            id = state.activeLocationId ?: "pending",
            latitude = lat,
            longitude = lon,
            radiusKm = state.radiusKm,
            label = state.pickedLabel,
            savedAtMillis = 0L
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun geocode(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(getApplication(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 5) ?: emptyList()
                addresses
                    .filter { it.hasLatitude() && it.hasLongitude() }
                    .map { address ->
                        SearchResult(
                            displayName = address.getAddressLine(0) ?: address.toString(),
                            latitude = address.latitude,
                            longitude = address.longitude
                        )
                    }
            }.getOrDefault(emptyList())
        }
}
