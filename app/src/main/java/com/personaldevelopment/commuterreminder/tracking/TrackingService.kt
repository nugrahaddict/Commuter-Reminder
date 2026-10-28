package com.personaldevelopment.commuterreminder.tracking

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.personaldevelopment.commuterreminder.R
import com.personaldevelopment.commuterreminder.alarm.Notifier
import com.personaldevelopment.commuterreminder.domain.ReminderConfig
import com.personaldevelopment.commuterreminder.domain.ReminderDetector
import com.personaldevelopment.commuterreminder.domain.ReminderEvent
import com.personaldevelopment.commuterreminder.domain.ReminderSettings
import com.personaldevelopment.commuterreminder.domain.model.Station

class TrackingService : Service() {

    private var detector: ReminderDetector? = null
    private var destination: Station? = null
    private var alarmActive = false

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val state = detector?.onLocationUpdate(location.latitude, location.longitude)
                ?: return
            handleState(state)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val dest = intent.getStationExtra(EXTRA_DESTINATION) ?: run {
                    stopTracking()
                    return START_NOT_STICKY
                }
                val radius = intent.getDoubleExtra(EXTRA_RADIUS_KM, ReminderSettings.ACTIVATION_RADIUS_KM)
                ReminderSettings.ACTIVATION_RADIUS_KM = radius
                destination = dest
                detector = ReminderDetector(
                    ReminderConfig(destination = dest, activationRadiusKm = radius)
                )
                startAsForeground(dest.name)
                requestLocationUpdates()
            }
            ACTION_SNOOZE -> {
                // alarm dimatikan user, lanjutkan pantau sampai benar-benar sampai
                Notifier.cancelAlarm(this)
                alarmActive = false
                detector?.acknowledgeReminder()
            }
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startAsForeground(destinationName: String) {
        Notifier.createChannels(this)
        val text = getString(R.string.notif_tracking_text, destinationName)
        val notification = Notifier.buildTrackingNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.NOTIFICATION_ID_TRACKING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Notifier.NOTIFICATION_ID_TRACKING, notification)
        }
    }

    private fun requestLocationUpdates() {
        if (!hasLocationPermission()) {
            stopTracking()
            return
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun handleState(state: com.personaldevelopment.commuterreminder.domain.ReminderState) {
        val dest = destination ?: return

        updateTrackingNotification(dest, state)

        if (alarmActive && state.event == ReminderEvent.NONE) return
        when (state.event) {
            ReminderEvent.ENTERED_RADIUS -> {
                Notifier.showAlarmNotification(
                    this,
                    getString(R.string.notif_alarm_near_title),
                getString(
                    R.string.notif_alarm_near_text,
                    ReminderSettings.formatRadiusKm(),
                    dest.name
                )
                )
                alarmActive = true
            }
            ReminderEvent.ARRIVED_AT_DESTINATION -> {
                Notifier.showAlarmNotification(
                    this,
                    getString(R.string.notif_alarm_arriving),
                    getString(R.string.notif_alarm_arriving_text, dest.name)
                )
                alarmActive = true
            }
            ReminderEvent.NONE -> Unit
        }
    }

    private fun updateTrackingNotification(
        dest: Station,
        state: com.personaldevelopment.commuterreminder.domain.ReminderState
    ) {
        val distance = state.distanceToDestinationKm
        val text = if (distance != Double.MAX_VALUE && distance > 0) {
            getString(R.string.notif_tracking_distance, dest.name, formatDistance(distance))
        } else {
            getString(R.string.notif_tracking_text, dest.name)
        }
        Notifier.showTrackingNotification(this, text)
    }

    private fun formatDistance(km: Double): String =
        if (km >= 1.0) "${kotlin.math.ceil(km).toInt()} km" else "${(km * 1000).toInt()} m"

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        Notifier.cancelAlarm(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.personaldevelopment.commuterreminder.action.START"
        const val ACTION_STOP = "com.personaldevelopment.commuterreminder.action.STOP"
        const val ACTION_SNOOZE = "com.personaldevelopment.commuterreminder.action.SNOOZE"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_RADIUS_KM = "radius_km"

        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MIN_UPDATE_DISTANCE_M = 50f

        fun start(context: Context, destination: Station, radiusKm: Double = ReminderSettings.ACTIVATION_RADIUS_KM) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_RADIUS_KM, radiusKm)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun snooze(context: Context) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_SNOOZE
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private fun Intent.getStationExtra(key: String): Station? {
    val value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        @Suppress("UNCHECKED_CAST")
        extras?.getSerializable(key) as? Station
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(key) as? Station
    }
    return value
}
