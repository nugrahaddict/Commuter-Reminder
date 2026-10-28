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
import com.personaldevelopment.commuterreminder.domain.JourneyConfig
import com.personaldevelopment.commuterreminder.domain.JourneyDetector
import com.personaldevelopment.commuterreminder.domain.JourneyEvent
import com.personaldevelopment.commuterreminder.domain.model.Station

class TrackingService : Service() {

    private var detector: JourneyDetector? = null
    private var destination: Station? = null
    private var lastEvent: JourneyEvent = JourneyEvent.NONE
    private var lastFiredAt: Long = 0

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
                val origin = intent.getStationExtra(EXTRA_ORIGIN)
                val dest = intent.getStationExtra(EXTRA_DESTINATION)
                if (origin == null || dest == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val between = intent.getStationListExtra(EXTRA_STATIONS_BETWEEN)
                destination = dest
                detector = JourneyDetector(
                    JourneyConfig(origin = origin, destination = dest, stationsBetween = between)
                )
                lastEvent = JourneyEvent.NONE
                startAsForeground(dest.name)
                requestLocationUpdates()
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

    private fun handleState(state: com.personaldevelopment.commuterreminder.domain.JourneyState) {
        val dest = destination ?: return
        val now = System.currentTimeMillis()
        val canFireAgain = now - lastFiredAt > EVENT_COOLDOWN_MS

        if (state.event == lastEvent && state.event != JourneyEvent.NONE) return
        if (state.event == JourneyEvent.NONE) {
            lastEvent = JourneyEvent.NONE
            return
        }
        if (!canFireAgain) return

        when (state.event) {
            JourneyEvent.APPROACHING_BEFORE_DESTINATION -> {
                Notifier.showAlarmNotification(
                    this,
                    getString(R.string.notif_alarm_one_station),
                    getString(
                        R.string.notif_alarm_one_station_text,
                        state.nextStation?.name ?: "",
                        dest.name
                    )
                )
                lastFiredAt = now
            }
            JourneyEvent.ARRIVING_AT_DESTINATION -> {
                Notifier.showAlarmNotification(
                    this,
                    getString(R.string.notif_alarm_arriving),
                    getString(R.string.notif_alarm_arriving_text, dest.name)
                )
                lastFiredAt = now
            }
            JourneyEvent.PASSED_DESTINATION -> {
                Notifier.showAlarmNotification(
                    this,
                    getString(R.string.notif_alarm_passed),
                    getString(R.string.notif_alarm_passed_text, dest.name)
                )
                lastFiredAt = now
            }
            JourneyEvent.NONE -> Unit
        }
        lastEvent = state.event
        updateTrackingDistance(dest, state)
    }

    private fun updateTrackingDistance(dest: Station, state: com.personaldevelopment.commuterreminder.domain.JourneyState) {
        val distance = state.distanceToNextStationMeters
        val text = if (distance > 0) {
            getString(R.string.notif_tracking_distance, dest.name, distance)
        } else {
            getString(R.string.notif_tracking_text, dest.name)
        }
        Notifier.showTrackingNotification(this, text)
    }

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
        const val EXTRA_ORIGIN = "origin"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_STATIONS_BETWEEN = "stations_between"

        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MIN_UPDATE_DISTANCE_M = 50f
        private const val EVENT_COOLDOWN_MS = 60_000L

        fun start(context: Context, origin: Station, destination: Station, between: List<Station>) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ORIGIN, origin)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_STATIONS_BETWEEN, ArrayList(between))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_STOP
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

private fun Intent.getStationListExtra(key: String): List<Station> {
    val value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        @Suppress("UNCHECKED_CAST")
        extras?.getSerializable(key) as? ArrayList<Station>
    } else {
        @Suppress("DEPRECATION")
        extras?.getSerializable(key) as? ArrayList<Station>
    }
    return value ?: emptyList()
}
