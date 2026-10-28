package com.personaldevelopment.commuterreminder

import com.personaldevelopment.commuterreminder.domain.JourneyConfig
import com.personaldevelopment.commuterreminder.domain.JourneyDetector
import com.personaldevelopment.commuterreminder.domain.JourneyEvent
import com.personaldevelopment.commuterreminder.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyDetectorTest {

    private fun station(id: String, name: String, orderIndex: Int, lat: Double, lon: Double) =
        Station(id = id, name = name, line = "Test", orderIndex = orderIndex, latitude = lat, longitude = lon)

    private val cikini = station("cikini", "Cikini", 7, -6.1980, 106.8453)
    private val menteng = station("menteng", "Menteng", 8, -6.2072, 106.8493)
    private val sudirman = station("sudirman", "Sudirman", 9, -6.2221, 106.8495)
    private val manggarai = station("manggarai", "Manggarai", 10, -6.2095, 106.8520)

    private fun detector(
        origin: Station,
        destination: Station,
        approachingRadius: Int = 1000,
        arrivalRadius: Int = 500
    ): JourneyDetector {
        val others = listOf(cikini, menteng, sudirman, manggarai).filter {
            it.id !in listOf(origin.id, destination.id)
        }
        return JourneyDetector(
            JourneyConfig(
                origin = origin,
                destination = destination,
                stationsBetween = others,
                approachingRadiusMeters = approachingRadius,
                arrivalRadiusMeters = arrivalRadius
            )
        )
    }

    @Test
    fun `far from route gives no event`() {
        val d = detector(origin = cikini, destination = sudirman)
        val state = d.onLocationUpdate(-6.1980, 106.8453)
        assertEquals(JourneyEvent.NONE, state.event)
    }

    @Test
    fun `one station before destination triggers approaching event`() {
        val d = detector(origin = cikini, destination = sudirman)
        val state = d.onLocationUpdate(-6.2072, 106.8493)
        assertEquals(JourneyEvent.APPROACHING_BEFORE_DESTINATION, state.event)
        assertEquals(1, state.stationsUntilDestination)
        assertNotNull(state.nextStation)
    }

    @Test
    fun `at destination triggers arriving event`() {
        val d = detector(origin = cikini, destination = sudirman)
        val state = d.onLocationUpdate(-6.2221, 106.8495)
        assertEquals(JourneyEvent.ARRIVING_AT_DESTINATION, state.event)
        assertEquals(0, state.stationsUntilDestination)
    }

    @Test
    fun `beyond destination triggers passed event`() {
        val d = detector(origin = cikini, destination = sudirman)
        val state = d.onLocationUpdate(-6.2095, 106.8520)
        assertEquals(JourneyEvent.PASSED_DESTINATION, state.event)
    }

    @Test
    fun `approaching radius configurable`() {
        val d = detector(origin = cikini, destination = sudirman, approachingRadius = 2500)
        val state = d.onLocationUpdate(-6.2072, 106.8493)
        assertEquals(JourneyEvent.APPROACHING_BEFORE_DESTINATION, state.event)
    }

    @Test
    fun `nearest station is detected when no event`() {
        val d = detector(origin = cikini, destination = sudirman)
        val state = d.onLocationUpdate(-6.2072, 106.8493)
        assertEquals("menteng", state.nearestStation?.id)
        assertTrue(state.distanceToNextStationMeters >= 0)
    }
}
