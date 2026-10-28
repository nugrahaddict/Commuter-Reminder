package com.personaldevelopment.commuterreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.personaldevelopment.commuterreminder.domain.model.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    uiState: RouteUiState,
    onOriginSelected: (Station) -> Unit,
    onDestinationSelected: (Station) -> Unit,
    onStartJourney: () -> Unit,
    onStopJourney: () -> Unit
) {
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

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
                    Button(onClick = { /* reload handled via VM */ }, enabled = false) {
                        Text(text = "Coba lagi")
                    }
                }
                else -> {
                    StationSelectorCard(
                        origin = uiState.origin,
                        destination = uiState.destination,
                        onPickOrigin = { pickerTarget = PickerTarget.ORIGIN },
                        onPickDestination = { pickerTarget = PickerTarget.DESTINATION }
                    )

                    val routeStations = remember(uiState.origin, uiState.destination, uiState.stations) {
                        buildList {
                            val origin = uiState.origin
                            val destination = uiState.destination
                            if (origin != null && destination != null && origin.line == destination.line) {
                                val lo = minOf(origin.orderIndex, destination.orderIndex)
                                val hi = maxOf(origin.orderIndex, destination.orderIndex)
                                addAll(
                                    uiState.stations.filter {
                                        it.line == origin.line && it.orderIndex in lo..hi
                                    }
                                )
                            }
                        }
                    }
                    if (routeStations.isNotEmpty()) {
                        RoutePreview(stations = routeStations, origin = uiState.origin, destination = uiState.destination)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (uiState.isTracking) {
                        OutlinedButton(
                            onClick = onStopJourney,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Stop Perjalanan")
                        }
                    } else {
                        Button(
                            onClick = onStartJourney,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.origin != null &&
                                uiState.destination != null &&
                                uiState.error == null
                        ) {
                            Text(text = "Mulai Perjalanan")
                        }
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        StationPickerDialog(
            title = if (target == PickerTarget.ORIGIN) "Pilih Stasiun Keberangkatan" else "Pilih Stasiun Tujuan",
            stations = uiState.stations,
            excluded = if (target == PickerTarget.ORIGIN) uiState.destination else uiState.origin,
            onDismiss = { pickerTarget = null },
            onSelected = { station ->
                if (target == PickerTarget.ORIGIN) onOriginSelected(station) else onDestinationSelected(station)
                pickerTarget = null
            }
        )
    }
}

private enum class PickerTarget { ORIGIN, DESTINATION }

@Composable
private fun StationSelectorCard(
    origin: Station?,
    destination: Station?,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StationButton(
                label = "Dari",
                station = origin,
                onClick = onPickOrigin
            )
            HorizontalDivider()
            StationButton(
                label = "Ke",
                station = destination,
                onClick = onPickDestination
            )
        }
    }
}

@Composable
private fun StationButton(
    label: String,
    station: Station?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp)
        )
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(
                text = station?.let { "${it.name} (${it.line})" } ?: "Pilih stasiun",
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RoutePreview(
    stations: List<Station>,
    origin: Station?,
    destination: Station?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Rute", style = MaterialTheme.typography.titleSmall)
            Text(
                text = stations.joinToString(" → ") { station ->
                    when (station.id) {
                        origin?.id -> "${station.name} (naik)"
                        destination?.id -> "${station.name} (turun)"
                        else -> station.name
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StationPickerDialog(
    title: String,
    stations: List<Station>,
    excluded: Station?,
    onDismiss: () -> Unit,
    onSelected: (Station) -> Unit
) {
    var selectedLine by remember { mutableStateOf<String?>(null) }
    val lines = stations.map { it.line }.distinct().sorted()
    val effectiveLine = selectedLine ?: lines.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    lines.take(4).forEach { line ->
                        FilterChip(
                            selected = effectiveLine == line,
                            onClick = { selectedLine = line },
                            label = { Text(text = line) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(
                        stations.filter { it.line == effectiveLine }.sortedBy { it.orderIndex }
                    ) { station ->
                        val enabled = station.id != excluded?.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = { if (enabled) onSelected(station) },
                                enabled = enabled
                            )
                            Text(
                                text = if (enabled) station.name else "${station.name} (dipakai)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = "Batal") }
        }
    )
}
