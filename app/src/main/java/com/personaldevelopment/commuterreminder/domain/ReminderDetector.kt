package com.personaldevelopment.commuterreminder.domain

import com.personaldevelopment.commuterreminder.domain.model.Station

/** Satu-satunya sumber nilai radius reminder; semua UI & service membaca dari sini. */
object ReminderSettings {
    const val ACTIVATION_RADIUS_KM = 3.0

    fun formatRadiusKm(): String {
        val km = ACTIVATION_RADIUS_KM
        return if (km >= 1.0) {
            val whole = if (km == km.toLong().toDouble()) km.toLong().toString() else "$km"
            "$whole km"
        } else {
            "${(km * 1000).toInt()} m"
        }
    }
}

data class ReminderConfig(
    val destination: Station,
    val activationRadiusKm: Double = ReminderSettings.ACTIVATION_RADIUS_KM
)

enum class ReminderEvent {
    NONE,
    ENTERED_RADIUS,
    ARRIVED_AT_DESTINATION
}

data class ReminderState(
    val event: ReminderEvent = ReminderEvent.NONE,
    val distanceToDestinationKm: Double = Double.MAX_VALUE,
    val isInsideRadius: Boolean = false,
    val hasArrived: Boolean = false
)

class ReminderDetector(private val config: ReminderConfig) {

    private var insideRadius = false
    private var notifiedArrival = false

    fun onLocationUpdate(latitude: Double, longitude: Double): ReminderState {
        val distanceKm = GeoCalculator.distanceKm(
            latitude, longitude,
            config.destination.latitude, config.destination.longitude
        )

        val wasInside = insideRadius
        insideRadius = distanceKm <= config.activationRadiusKm

        val arrived = distanceKm <= ARRIVAL_RADIUS_KM
        var event = ReminderEvent.NONE

        if (insideRadius && !wasInside) {
            event = ReminderEvent.ENTERED_RADIUS
        }
        if (arrived && !notifiedArrival) {
            event = ReminderEvent.ARRIVED_AT_DESTINATION
            notifiedArrival = true
        }

        return ReminderState(
            event = event,
            distanceToDestinationKm = distanceKm,
            isInsideRadius = insideRadius,
            hasArrived = arrived
        )
    }

    fun acknowledgeReminder() {
        notifiedArrival = true
    }

    companion object {
        const val ARRIVAL_RADIUS_KM = 0.3
    }
}
