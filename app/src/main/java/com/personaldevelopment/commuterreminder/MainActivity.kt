package com.personaldevelopment.commuterreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personaldevelopment.commuterreminder.data.StationRepository
import com.personaldevelopment.commuterreminder.tracking.LocationClient
import com.personaldevelopment.commuterreminder.tracking.TrackingService
import com.personaldevelopment.commuterreminder.ui.RouteScreen
import com.personaldevelopment.commuterreminder.ui.RouteViewModel

class MainActivity : ComponentActivity() {

    private var viewModelInstance: RouteViewModel? = null

    private var showPermissionRationale by mutableStateOf(false)
    private var showPermanentlyDenied by mutableStateOf(false)
    private var pendingStart by mutableStateOf(false)

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val anyGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (anyGranted) {
                viewModelInstance?.retryLocation()
                requestNotificationPermissionThenStart()
            } else {
                pendingStart = false
                showPermanentlyDenied = locationPermissions.all {
                    !shouldShowRequestPermissionRationale(it)
                }
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startTrackingNow()
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasLocationPermission()) {
                viewModelInstance?.retryLocation()
                requestNotificationPermissionThenStart()
            } else {
                showPermanentlyDenied = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: RouteViewModel = viewModel {
                        RouteViewModel(
                            repository = StationRepository.from(applicationContext),
                            locationClient = { LocationClient.locationUpdates(applicationContext) }
                        )
                    }
                    viewModelInstance = viewModel

                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    RouteScreen(
                        uiState = uiState,
                        onDestinationSelected = viewModel::selectDestination,
                        onUpdateStation = viewModel::updateStationCoordinates,
                        onStartReminder = { handleStartReminder() },
                        onStopReminder = { handleStopReminder() }
                    )
                }
            }

            if (showPermissionRationale) {
                PermissionRationaleDialog(
                    onConfirm = {
                        showPermissionRationale = false
                        locationPermissionLauncher.launch(locationPermissions)
                    },
                    onDismiss = {
                        showPermissionRationale = false
                        pendingStart = false
                    }
                )
            }

            if (showPermanentlyDenied) {
                PermanentlyDeniedDialog(
                    onOpenSettings = {
                        showPermanentlyDenied = false
                        settingsLauncher.launch(appSettingsIntent())
                    },
                    onDismiss = {
                        showPermanentlyDenied = false
                        pendingStart = false
                    }
                )
            }
        }
    }

    private fun handleStartReminder() {
        val viewModel = viewModelInstance ?: return
        val state = viewModel.uiState.value
        if (state.destination == null) return

        pendingStart = true
        if (hasLocationPermission()) {
            requestNotificationPermissionThenStart()
        } else {
            showPermissionRationale = true
        }
    }

    private fun handleStopReminder() {
        TrackingService.stop(this)
        viewModelInstance?.setTracking(false)
    }

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startTrackingNow()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startTrackingNow() {
        val viewModel = viewModelInstance ?: return
        val state = viewModel.uiState.value
        val destination = state.destination
        if (destination == null) {
            pendingStart = false
            return
        }
        TrackingService.start(this, destination)
        viewModel.setTracking(true)
        pendingStart = false
        showPermissionRationale = false
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun appSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
}

@androidx.compose.runtime.Composable
private fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Izin Lokasi Diperlukan") },
        text = {
            Text(
                text = "Commuter Reminder butuh akses lokasi untuk memantau posisi kamu " +
                    "dan membangunkan kamu saat mendekati stasiun tujuan. " +
                    "Lokasi hanya diproses di perangkat, tidak dikirim ke server manapun."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(text = "Berikan Izin") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = "Nanti") }
        }
    )
}

@androidx.compose.runtime.Composable
private fun PermanentlyDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Izin Lokasi Ditolak Permanen") },
        text = {
            Text(
                text = "Tanpa izin lokasi, aplikasi tidak bisa memantau posisi. " +
                    "Aktifkan izin lokasi di pengaturan aplikasi."
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text(text = "Buka Pengaturan") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = "Batal") }
        }
    )
}
