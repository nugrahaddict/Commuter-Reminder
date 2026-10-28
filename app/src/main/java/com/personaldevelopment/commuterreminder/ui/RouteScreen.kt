package com.personaldevelopment.commuterreminder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.ui.map.CoordinatePickerMap
import com.personaldevelopment.commuterreminder.ui.map.StationMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    uiState: RouteUiState,
    onDestinationSelected: (Station) -> Unit,
    onUpdateStation: (Station, Double, Double) -> Unit,
    onStartReminder: () -> Unit,
    onStopReminder: () -> Unit
) {
    var pickerVisible by remember { mutableStateOf(false) }
    var editingStation by remember { mutableStateOf<Station?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Commuter Reminder") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { pickerVisible = true }) {
                        Text(text = "Pilih Stasiun Tujuan")
                    }
                }
                else -> {
                    DestinationCard(
                        destination = uiState.destination,
                        onPickDestination = { pickerVisible = true }
                    )

                    MapSection(
                        currentLocation = uiState.currentLocation,
                        destination = uiState.destination,
                        isTracking = uiState.isTracking,
                        onStartReminder = onStartReminder,
                        onStopReminder = onStopReminder,
                        canStart = uiState.destination != null
                    )
                }
            }
        }
    }

    if (pickerVisible) {
        StationPickerDialog(
            title = "Pilih Stasiun Tujuan",
            stations = uiState.stations,
            onDismiss = { pickerVisible = false },
            onSelected = { station ->
                onDestinationSelected(station)
                pickerVisible = false
            },
            onEditStation = { station -> editingStation = station }
        )
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
private fun MapSection(
    currentLocation: com.personaldevelopment.commuterreminder.tracking.AppLocation?,
    destination: Station?,
    isTracking: Boolean,
    onStartReminder: () -> Unit,
    onStopReminder: () -> Unit,
    canStart: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peta",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isTracking) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (isTracking) "Reminder aktif" else "Belum aktif",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTracking) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            StationMap(
                currentLocation = currentLocation,
                destination = destination,
                modifier = Modifier.fillMaxWidth()
            )

            if (isTracking) {
                Text(
                    text = "Alarm berbunyi otomatis saat kamu berada dalam radius 3 km.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onStopReminder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Stop Reminder")
                }
            } else {
                Button(
                    onClick = onStartReminder,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canStart
                ) {
                    Text(text = "Mulai Reminder")
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: Station?,
    onPickDestination: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ke",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp)
                )
                OutlinedButton(onClick = onPickDestination, modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination?.let { "${it.name} (${it.lineShortName})" }
                            ?: "Pilih stasiun tujuan",
                        maxLines = 1
                    )
                }
            }
            if (destination != null) {
                HorizontalDivider()
                Text(
                    text = "Reminder aktif otomatis saat radius 3 km.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StationPickerDialog(
    title: String,
    stations: List<Station>,
    onDismiss: () -> Unit,
    onSelected: (Station) -> Unit,
    onEditStation: (Station) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var selectedLine by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val lines = remember(stations) { stations.map { it.line }.distinct().sorted() }
    val lineStations = remember(selectedLine, stations) {
        stations.filter { it.line == selectedLine }.sortedBy { it.orderIndex }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    step == 1 -> "Pilih Line"
                    else -> "Pilih Stasiun — ${lineStations.firstOrNull()?.lineName ?: ""}"
                }
            )
        },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = if (step == 1) "Cari line atau stasiun…" else "Cari stasiun…"
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
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    step == 1 -> LineList(
                        stations = stations,
                        lines = lines,
                        query = query,
                        onSelectLine = { line ->
                            selectedLine = line
                            query = ""
                            step = 2
                        }
                    )
                    else -> StationList(
                        lineStations = lineStations,
                        query = query,
                        onSelect = onSelected,
                        onEdit = onEditStation
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = "Batal") }
        }
    )
}

private val RECOMMENDED_LINES = setOf("BOGOR", "CIKARANG", "RANGKAS")

private val HUB_STATIONS = setOf(
    "manggarai", "tanah abang", "jakarta kota", "sudirman", "bekasi",
    "depok", "bogor", "serpong", "tangerang", "duri", "jatinegara",
    "kampung bandan", "citayam", "rangkasbitung"
)

@Composable
private fun ColumnScope.LineList(
    stations: List<Station>,
    lines: List<String>,
    query: String,
    onSelectLine: (String) -> Unit
) {
    val q = query.trim().lowercase()
    val filteredLines = remember(lines, stations, q) {
        if (q.isEmpty()) {
            lines
        } else {
            lines.filter { line ->
                val sample = stations.first { it.line == line }
                sample.lineShortName.lowercase().contains(q) ||
                    sample.lineName.lowercase().contains(q) ||
                    line.lowercase().contains(q) ||
                    stations.any { it.line == line && it.name.lowercase().contains(q) }
            }
        }
    }
    val recommended = filteredLines.filter { it in RECOMMENDED_LINES }
    val others = filteredLines - RECOMMENDED_LINES.toSet()

    when {
        filteredLines.isEmpty() -> EmptyResult(text = "Line tidak ditemukan")
        q.isEmpty() && recommended.isNotEmpty() -> {
            SectionHeader(text = "Rekomendasi")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recommended) { line ->
                    LineItem(stations = stations, line = line, onSelectLine = onSelectLine)
                }
                item { SectionHeader(text = "Semua Line") }
                items(others) { line ->
                    LineItem(stations = stations, line = line, onSelectLine = onSelectLine)
                }
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredLines) { line ->
                    LineItem(stations = stations, line = line, onSelectLine = onSelectLine)
                }
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
    val lineName = sample.lineName
    val lineShort = sample.lineShortName
    Card(
        onClick = { onSelectLine(line) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LineColorDot(line = line)
            Column {
                Text(text = lineShort, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = lineName.substringAfter(" (").removeSuffix(")"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.StationList(
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
    val recommended = remember(lineStations, q) {
        if (q.isEmpty()) {
            lineStations.filter { it.name.trim().lowercase() in HUB_STATIONS }
        } else {
            emptyList()
        }
    }

    when {
        filtered.isEmpty() -> EmptyResult(text = "Stasiun tidak ditemukan")
        recommended.isNotEmpty() -> {
            SectionHeader(text = "Rekomendasi")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recommended) { station ->
                    StationItem(station = station, onSelect = onSelect, onEdit = onEdit)
                }
                item { SectionHeader(text = "Semua Stasiun") }
                items(filtered - recommended.toSet()) { station ->
                    StationItem(station = station, onSelect = onSelect, onEdit = onEdit)
                }
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered) { station ->
                    StationItem(station = station, onSelect = onSelect, onEdit = onEdit)
                }
            }
        }
    }
}

@Composable
private fun StationItem(
    station: Station,
    onSelect: (Station) -> Unit,
    onEdit: (Station) -> Unit
) {
    Row(
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

@Composable
private fun LineColorDot(line: String) {
    val color = when (line) {
        "BOGOR" -> Color(0xFFC62828)
        "NAMBO" -> Color(0xFFE57373)
        "CIKARANG" -> Color(0xFF1565C0)
        "CIKARANG_UTARA" -> Color(0xFF42A5F5)
        "RANGKAS" -> Color(0xFF2E7D32)
        "TANGERANG" -> Color(0xFF6D4C41)
        "PRIOK" -> Color(0xFFD81B60)
        else -> MaterialTheme.colorScheme.primary
    }
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(color = color)
    }
}
