package com.personaldevelopment.commuterreminder.domain

import com.personaldevelopment.commuterreminder.domain.model.Station

enum class JourneyEvent {
    NONE,
    APPROACHING_BEFORE_DESTINATION,
    ARRIVING_AT_DESTINATION,
    PASSED_DESTINATION
}

data class JourneyConfig(
    val origin: Station,
    val destination: Station,
    val stationsBetween: List<Station>,
    val approachingRadiusMeters: Int = 1000,
    val arrivalRadiusMeters: Int = 500,
    val passedDistanceMeters: Int = 1000
)

data class JourneyState(
    val event: JourneyEvent = JourneyEvent.NONE,
    val nearestStation: Station? = null,
    val nextStation: Station? = null,
    val stationsUntilDestination: Int = -1,
    val distanceToNextStationMeters: Int = -1
)

class JourneyDetector(private val config: JourneyConfig) {

    private val direction: Int =
        if (config.destination.orderIndex >= config.origin.orderIndex) 1 else -1

    private val routeStations: List<Station> = buildRouteStations()

    fun onLocationUpdate(latitude: Double, longitude: Double): JourneyState {
        val nearest = findNearestStation(latitude, longitude)
            ?: return JourneyState()

        val distanceToNearest = GeoCalculator.kmToMeters(
            GeoCalculator.distanceKm(latitude, longitude, nearest.latitude, nearest.longitude)
        )

        val stationsUntil = (config.destination.orderIndex - nearest.orderIndex) * direction

        val next = routeStations.firstOrNull { it.orderIndex == nearest.orderIndex + direction }
        val distanceToNext = next?.let {
            GeoCalculator.kmToMeters(
                GeoCalculator.distanceKm(latitude, longitude, it.latitude, it.longitude)
            )
        } ?: -1

        val event = when {
            isPassedDestination(latitude, longitude, nearest) ->
                JourneyEvent.PASSED_DESTINATION
            nearest.id == config.destination.id &&
                distanceToNearest <= config.arrivalRadiusMeters ->
                JourneyEvent.ARRIVING_AT_DESTINATION
            stationsUntil == 1 &&
                distanceToNearest <= config.approachingRadiusMeters &&
                distanceToDestination(latitude, longitude) > distanceToNearest ->
                JourneyEvent.APPROACHING_BEFORE_DESTINATION
            else -> JourneyEvent.NONE
        }

        return JourneyState(
            event = event,
            nearestStation = nearest,
            nextStation = next,
            stationsUntilDestination = stationsUntil,
            distanceToNextStationMeters = distanceToNext
        )
    }

    private fun distanceToDestination(latitude: Double, longitude: Double) =
        GeoCalculator.kmToMeters(
            GeoCalculator.distanceKm(
                latitude, longitude,
                config.destination.latitude, config.destination.longitude
            )
        )

    private fun isPassedDestination(
        latitude: Double,
        longitude: Double,
        nearest: Station
    ): Boolean {
        val distanceToDestination = distanceToDestination(latitude, longitude)
        if (distanceToDestination <= config.passedDistanceMeters) return false
        return (config.destination.orderIndex - nearest.orderIndex) * direction < 0
    }

    private fun buildRouteStations(): List<Station> {
        val startIndex = minOf(config.origin.orderIndex, config.destination.orderIndex)
        val endIndex = maxOf(config.origin.orderIndex, config.destination.orderIndex)
        val inRoute = (config.stationsBetween + config.origin + config.destination)
            .distinctBy { it.id }
            .filter { it.orderIndex in startIndex..endIndex }
            .sortedBy { it.orderIndex }
        val pastSentinel = config.stationsBetween
            .filter {
                (it.orderIndex - config.destination.orderIndex) * direction == 1
            }
            .minByOrNull { (it.orderIndex - config.destination.orderIndex) * direction }
        return if (pastSentinel != null) inRoute + pastSentinel else inRoute
    }

    private fun findNearestStation(latitude: Double, longitude: Double): Station? {
        var nearest: Station? = null
        var nearestDistance = Double.MAX_VALUE
        for (station in routeStations) {
            val distance = GeoCalculator.distanceKm(
                latitude, longitude, station.latitude, station.longitude
            )
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = station
            }
        }
        return nearest
    }
}
