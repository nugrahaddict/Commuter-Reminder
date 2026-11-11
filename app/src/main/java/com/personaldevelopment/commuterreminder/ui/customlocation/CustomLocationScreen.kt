package com.personaldevelopment.commuterreminder.ui.customlocation

import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personaldevelopment.commuterreminder.R
import com.personaldevelopment.commuterreminder.data.CustomLocationStore
import com.personaldevelopment.commuterreminder.domain.ReminderSettings
import com.personaldevelopment.commuterreminder.ui.map.CoordinatePickerMap
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fitur Custom Location: pin titik via tekan lama peta atau pencarian alamat,
 * pilih radius reminder, simpan ke riwayat, dan langsung mulai reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLocationScreen(
    onBack: () -> Unit,
    onStartReminder: () -> Unit,
    isTracking: Boolean,
    onStopReminder: () -> Unit,
    onViewModelCreated: (CustomLocationViewModel) -> Unit = {},
    viewModel: CustomLocationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    onViewModelCreated(viewModel)

    // posisi realtime user untuk peta saat reminder aktif
    val locationFlow = remember {
        kotlinx.coroutines.flow.flow {
            if (com.personaldevelopment.commuterreminder.tracking.LocationClient.hasPermission(context)) {
                com.personaldevelopment.commuterreminder.tracking.LocationClient
                    .locationUpdates(context).collect { emit(it) }
            }
        }
    }
    val currentLocation: com.personaldevelopment.commuterreminder.tracking.AppLocation? by
        locationFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.custom_location_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchField(
                query = uiState.searchQuery,
                isSearching = uiState.isSearching,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = viewModel::search
            )
            uiState.searchResults.forEach { result ->
                OutlinedButton(
                    onClick = { viewModel.selectSearchResult(result) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }

            CoordinatePickerMap(
                station = stationPlaceholder(),
                centerLat = uiState.pickedLat,
                centerLon = uiState.pickedLon,
                pickedLat = uiState.pickedLat,
                pickedLon = uiState.pickedLon,
                focusTarget = uiState.focusPoint?.let { GeoPoint(it.first, it.second) },
                onPick = viewModel::onPick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )

            Text(
                text = stringResource(
                    R.string.custom_location_picked,
                    uiState.pickedLat ?: 0.0,
                    uiState.pickedLon ?: 0.0
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.custom_location_radius_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5, 1.0, 2.0, 3.0, 5.0).forEach { preset ->
                    FilterChip(
                        selected = uiState.radiusKm == preset,
                        onClick = { viewModel.setRadius(preset) },
                        label = { Text(text = formatPreset(preset)) }
                    )
                }
            }

            OutlinedTextField(
                value = uiState.radiusText,
                onValueChange = viewModel::onRadiusTextChange,
                label = { Text(text = stringResource(R.string.custom_location_radius_hint)) },
                isError = ReminderSettings.validateRadiusText(uiState.radiusText) != null,
                supportingText = {
                    val error = ReminderSettings.validateRadiusText(uiState.radiusText)
                    if (error != null) Text(text = error)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = uiState.pickedLat != null && uiState.pickedLon != null &&
                        ReminderSettings.validateRadiusText(uiState.radiusText) == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.custom_location_save))
                }
                OutlinedButton(
                    onClick = viewModel::clear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.custom_location_clear))
                }
            }

            // Mulai/stop reminder memakai titik + radius yang sedang dipilih
            val canStart = uiState.pickedLat != null && uiState.pickedLon != null &&
                ReminderSettings.validateRadiusText(uiState.radiusText) == null
            if (isTracking) {
                Button(
                    onClick = onStopReminder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.custom_location_stop_reminder))
                }
            } else {
                Button(
                    onClick = onStartReminder,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.custom_location_start_reminder))
                }
            }

            // Saat reminder aktif: peta posisi kamu + tujuan + garis lurus penghubung
            val targetLat = uiState.pickedLat
            val targetLon = uiState.pickedLon
            if (isTracking && targetLat != null && targetLon != null) {
                val targetStation = com.personaldevelopment.commuterreminder.domain.model.Station(
                    id = "custom_target",
                    name = uiState.pickedLabel
                        ?: stringResource(R.string.custom_location_default_name),
                    line = "",
                    orderIndex = 0,
                    latitude = targetLat,
                    longitude = targetLon
                )
                com.personaldevelopment.commuterreminder.ui.map.StationMap(
                    currentLocation = currentLocation,
                    destination = targetStation,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.saved) {
                Text(
                    text = stringResource(R.string.custom_location_saved_msg),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            HistorySection(
                history = uiState.history,
                activeId = uiState.activeLocationId,
                onSelect = viewModel::selectHistoryEntry,
                onRemove = viewModel::removeFromHistory,
                onRename = viewModel::renameAlias
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<CustomLocationStore.CustomLocation>,
    activeId: String?,
    onSelect: (CustomLocationStore.CustomLocation) -> Unit,
    onRemove: (CustomLocationStore.CustomLocation) -> Unit,
    onRename: (CustomLocationStore.CustomLocation, String) -> Unit
) {
    var renamingEntry by remember {
        mutableStateOf<CustomLocationStore.CustomLocation?>(null)
    }
    var renamingText by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.custom_location_default_name)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.custom_location_history_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.custom_location_history_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        history.forEach { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = entry.label
                                ?: stringResource(R.string.custom_location_default_name),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = formatHistorySubtitle(entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    TextButtonCompact(
                        selected = entry.id == activeId,
                        onClick = { onSelect(entry) }
                    )
                    IconButton(
                        onClick = {
                            renamingEntry = entry
                            renamingText = entry.label ?: defaultName
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.custom_location_rename)
                        )
                    }
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.custom_location_history_delete)
                        )
                    }
                }
            }
        }
    }

    renamingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { renamingEntry = null },
            title = { Text(text = stringResource(R.string.custom_location_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renamingText,
                    onValueChange = { renamingText = it },
                    label = { Text(text = stringResource(R.string.custom_location_alias_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(entry, renamingText)
                        renamingEntry = null
                    }
                ) {
                    Text(text = stringResource(R.string.custom_location_rename_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { renamingEntry = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TextButtonCompact(selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !selected) {
        Text(
            text = if (selected) {
                stringResource(R.string.custom_location_history_selected)
            } else {
                stringResource(R.string.custom_location_history_use)
            },
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(text = stringResource(R.string.custom_location_search_hint)) },
        singleLine = true,
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.custom_location_search)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatHistorySubtitle(entry: CustomLocationStore.CustomLocation): String {
    val coords = "%.5f, %.5f".format(Locale.US, entry.latitude, entry.longitude)
    val radius = if (entry.radiusKm >= 1.0) {
        "${entry.radiusKm.toLong()} km"
    } else {
        "${(entry.radiusKm * 1000).toInt()} m"
    }
    return if (entry.savedAtMillis > 0L) {
        val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            .format(Date(entry.savedAtMillis))
        "$radius • $coords • $date"
    } else {
        "$radius • $coords"
    }
}

/** Dummy station agar komponen peta lama tetap kompatibel; center memakai picked. */
private fun stationPlaceholder(): com.personaldevelopment.commuterreminder.domain.model.Station =
    com.personaldevelopment.commuterreminder.domain.model.Station(
        id = "custom",
        name = "",
        line = "",
        latitude = DEFAULT_MAP_LAT,
        longitude = DEFAULT_MAP_LON,
        orderIndex = 0
    )

private fun formatPreset(km: Double): String =
    if (km >= 1.0) "${km.toLong()} km" else "${(km * 1000).toInt()} m"

private const val DEFAULT_MAP_LAT = -6.2088
private const val DEFAULT_MAP_LON = 106.8456
