package com.personaldevelopment.commuterreminder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.ui.LineColorDot
import com.personaldevelopment.commuterreminder.ui.map.CoordinatePickerMap

/**
 * Editor koordinat stasiun (pindahan dari picker "Ke" di beranda):
 * pilih line -> pilih stasiun -> edit koordinat via peta / input manual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditorScreen(
    stations: List<Station>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onUpdateStation: (Station, Double, Double) -> Unit
) {
    var selectedLine by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var editingStation by remember { mutableStateOf<Station?>(null) }

    val lines = remember(stations) { stations.map { it.line }.distinct().sorted() }
    val lineStations = remember(selectedLine, stations) {
        stations.filter { it.line == selectedLine }.sortedBy { it.orderIndex }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedLine == null -> "Pilih Line"
                            else -> lineStations.firstOrNull()?.lineName ?: "Pilih Stasiun"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedLine != null) {
                                selectedLine = null
                                query = ""
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = if (selectedLine == null) "Cari line atau stasiun…"
                            else "Cari stasiun…"
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Hapus pencarian"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    selectedLine == null -> LineList(
                        stations = stations,
                        lines = lines,
                        query = query,
                        onSelectLine = { line ->
                            selectedLine = line
                            query = ""
                        },
                        onSelectStation = { station -> editingStation = station }
                    )
                    else -> StationList(
                        lineStations = lineStations,
                        query = query,
                        onSelect = { station -> editingStation = station },
                        onEdit = { station -> editingStation = station }
                    )
                }
            }
        }
    }

    editingStation?.let { station ->
        EditStationDialog(
            station = station,
            onDismiss = { editingStation = null },
            onSave = { lat, lon ->
                onUpdateStation(station, lat, lon)
                editingStation = null
            }
        )
    }
}

@Composable
private fun LineList(
    stations: List<Station>,
    lines: List<String>,
    query: String,
    onSelectLine: (String) -> Unit,
    onSelectStation: (Station) -> Unit
) {
    val q = query.trim().lowercase()
    val stationMatches = remember(stations, q) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            stations.filter { it.name.lowercase().contains(q) }
                .sortedWith(
                    compareBy({ !it.name.lowercase().startsWith(q) }, { it.name.lowercase() })
                )
        }
    }
    val filteredLines = remember(lines, stations, q) {
        if (q.isEmpty()) {
            lines
        } else {
            lines.filter { line ->
                val sample = stations.first { it.line == line }
                sample.lineShortName.lowercase().contains(q) ||
                    sample.lineName.lowercase().contains(q) ||
                    line.lowercase().contains(q)
            }
        }
    }

    when {
        filteredLines.isEmpty() && stationMatches.isEmpty() ->
            EmptyResult(text = "Line atau stasiun tidak ditemukan")
        else -> LazyColumn {
            if (stationMatches.isNotEmpty()) {
                item { SectionHeader(text = "Stasiun") }
                items(stationMatches) { station ->
                    StationResultItem(station = station, onSelect = onSelectStation)
                }
            }
            if (filteredLines.isNotEmpty()) {
                item { SectionHeader(text = "Line") }
                items(filteredLines) { line ->
                    LineItem(stations = stations, line = line, onSelectLine = onSelectLine)
                }
            }
        }
    }
}

@Composable
private fun StationList(
    lineStations: List<Station>,
    query: String,
    onSelect: (Station) -> Unit,
    onEdit: (Station) -> Unit
) {
    val q = query.trim().lowercase()
    val filtered = remember(lineStations, q) {
        if (q.isEmpty()) {
            lineStations
        } else {
            lineStations.filter { it.name.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyResult(text = "Stasiun tidak ditemukan")
    } else {
        LazyColumn {
            items(filtered) { station ->
                StationItem(station = station, onSelect = onSelect, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun LineItem(
    stations: List<Station>,
    line: String,
    onSelectLine: (String) -> Unit
) {
    val sample = stations.first { it.line == line }
    Card(
        onClick = { onSelectLine(line) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LineColorDot(line = line)
            Column {
                Text(text = sample.lineShortName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = sample.lineName.substringAfter(" (").removeSuffix(")"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StationResultItem(
    station: Station,
    onSelect: (Station) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(station) }
            .padding(vertical = 6.dp)
    ) {
        LineColorDot(line = station.line)
        Column {
            Text(text = station.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = station.lineShortName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StationItem(
    station: Station,
    onSelect: (Station) -> Unit,
    onEdit: (Station) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(station) }
            .padding(vertical = 6.dp)
    ) {
        RadioButton(selected = false, onClick = { onSelect(station) })
        Column(modifier = Modifier.weight(1f)) {
            Text(text = station.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.6f, %.6f".format(station.latitude, station.longitude),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onEdit(station) }) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit koordinat ${station.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditStationDialog(
    station: Station,
    onDismiss: () -> Unit,
    onSave: (latitude: Double, longitude: Double) -> Unit
) {
    var latText by remember { mutableStateOf("%.7f".format(station.latitude)) }
    var lonText by remember { mutableStateOf("%.7f".format(station.longitude)) }
    var latError by remember { mutableStateOf<String?>(null) }
    var lonError by remember { mutableStateOf<String?>(null) }

    // koordinat hasil long-press di peta; null = belum dipilih
    var pickedLat by remember { mutableStateOf<Double?>(null) }
    var pickedLon by remember { mutableStateOf<Double?>(null) }

    fun applyPicked(lat: Double, lon: Double) {
        pickedLat = lat
        pickedLon = lon
        latText = "%.7f".format(lat)
        lonText = "%.7f".format(lon)
        latError = validateCoord(latText, min = -7.5, max = -5.5)
        lonError = validateCoord(lonText, min = 105.5, max = 107.5)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit Koordinat") },
        text = {
            Column(
                modifier = Modifier.height(460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = station.lineShortName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CoordinatePickerMap(
                    station = station,
                    pickedLat = pickedLat,
                    pickedLon = pickedLon,
                    onPick = { lat, lon -> applyPicked(lat, lon) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        latError = validateCoord(it, min = -7.5, max = -5.5)
                    },
                    label = { Text(text = "Latitude") },
                    isError = latError != null,
                    supportingText = { latError?.let { msg -> Text(text = msg) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = {
                        lonText = it
                        lonError = validateCoord(it, min = 105.5, max = 107.5)
                    },
                    label = { Text(text = "Longitude") },
                    isError = lonError != null,
                    supportingText = { lonError?.let { msg -> Text(text = msg) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.replace(',', '.').toDoubleOrNull()
                    val lon = lonText.replace(',', '.').toDoubleOrNull()
                    val latOk = lat != null && validateCoord(latText, -7.5, -5.5) == null
                    val lonOk = lon != null && validateCoord(lonText, 105.5, 107.5) == null
                    if (latOk && lonOk && lat != null && lon != null) {
                        onSave(lat, lon)
                    }
                },
                enabled = latError == null && lonError == null
            ) {
                Text(text = "Simpan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = "Batal") }
        }
    )
}

/** Null = valid. Cek angka dan rentang koordinat Jabodetabek. */
private fun validateCoord(text: String, min: Double, max: Double): String? {
    val value = text.replace(',', '.').toDoubleOrNull()
        ?: return "Harus berupa angka, contoh: -6.2099000"
    return when {
        value < min || value > max -> "Di luar rentang wajar ($min s/d $max)"
        else -> null
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun EmptyResult(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
