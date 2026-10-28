package com.personaldevelopment.commuterreminder

import com.personaldevelopment.commuterreminder.domain.ReminderConfig
import com.personaldevelopment.commuterreminder.domain.ReminderDetector
import com.personaldevelopment.commuterreminder.domain.ReminderEvent
import com.personaldevelopment.commuterreminder.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDetectorTest {

    // 1 derajat latitude ~ 111.1 km
    private fun destination() = Station(
        id = "depok", name = "Depok", line = "BOGOR",
        orderIndex = 19, latitude = -6.4000, longitude = 106.8000
    )

    private fun detector() = ReminderDetector(
        ReminderConfig(destination = destination())
    )

    @Test
    fun `far away no event`() {
        val d = detector()
        // ~55 km dari tujuan
        val state = d.onLocationUpdate(-5.9000, 106.8000)
        assertEquals(ReminderEvent.NONE, state.event)
        assertFalse(state.isInsideRadius)
    }

    @Test
    fun `inside 3 km radius triggers entered event once`() {
        val d = detector()
        // ~1.7 km dari tujuan
        val state = d.onLocationUpdate(-6.3850, 106.8000)
        assertEquals(ReminderEvent.ENTERED_RADIUS, state.event)
        assertTrue(state.isInsideRadius)

        // update kedua masih di dalam radius: tidak ada event baru
        val second = d.onLocationUpdate(-6.3851, 106.8000)
        assertEquals(ReminderEvent.NONE, second.event)
        assertTrue(second.isInsideRadius)
    }

    @Test
    fun `exiting and re-entering radius re-triggers event`() {
        val d = detector()
        d.onLocationUpdate(-6.3850, 106.8000)
        d.onLocationUpdate(-5.9000, 106.8000)
        val reentered = d.onLocationUpdate(-6.3850, 106.8000)
        assertEquals(ReminderEvent.ENTERED_RADIUS, reentered.event)
    }

    @Test
    fun `exactly at destination triggers arrival event once`() {
        val d = detector()
        val state = d.onLocationUpdate(-6.4000, 106.8000)
        assertEquals(ReminderEvent.ARRIVED_AT_DESTINATION, state.event)
        assertTrue(state.hasArrived)

        val second = d.onLocationUpdate(-6.4000, 106.8000)
        assertEquals(ReminderEvent.NONE, second.event)
    }

    @Test
    fun `acknowledge reminder suppresses arrival event but not radius entry`() {
        val d = detector()
        d.acknowledgeReminder()
        val entered = d.onLocationUpdate(-6.3850, 106.8000)
        assertEquals(ReminderEvent.ENTERED_RADIUS, entered.event)

        val arrived = d.onLocationUpdate(-6.4000, 106.8000)
        assertEquals(ReminderEvent.NONE, arrived.event)
        assertTrue(arrived.hasArrived)
    }

    @Test
    fun `distance to destination reported`() {
        val d = detector()
        val state = d.onLocationUpdate(-6.3850, 106.8000)
        assertTrue("actual=${state.distanceToDestinationKm}",
            state.distanceToDestinationKm in 1.0..2.0)
    }

    @Test
    fun `just inside 3 km radius triggers entered event`() {
        val d = detector()
        // ~2,9 km di utara tujuan
        val offset = 2.9 / 111.1
        val state = d.onLocationUpdate(-6.4000 + offset, 106.8000)
        assertEquals(ReminderEvent.ENTERED_RADIUS, state.event)
        assertTrue(state.isInsideRadius)
    }

    @Test
    fun `just outside 3 km radius no event`() {
        val d = detector()
        // ~3,1 km di utara tujuan
        val offset = 3.1 / 111.1
        val state = d.onLocationUpdate(-6.4000 + offset, 106.8000)
        assertEquals(ReminderEvent.NONE, state.event)
        assertFalse(state.isInsideRadius)
    }
}
