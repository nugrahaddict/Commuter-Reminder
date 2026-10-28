package com.personaldevelopment.commuterreminder

import com.personaldevelopment.commuterreminder.domain.GeoCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCalculatorTest {

    @Test
    fun `distance between same point is zero`() {
        val dist = GeoCalculator.distanceKm(-6.2000, 106.8500, -6.2000, 106.8500)
        assertEquals(0.0, dist, 0.0001)
    }

    @Test
    fun `distance Cikini to Menteng is roughly 1 km`() {
        val dist = GeoCalculator.distanceKm(-6.1980, 106.8453, -6.2072, 106.8493)
        assertTrue("actual=$dist", dist in 0.8..1.4)
    }

    @Test
    fun `distance Jakarta Kota to Bogor is roughly 50 km`() {
        val dist = GeoCalculator.distanceKm(-6.1416, 106.8136, -6.5951, 106.8166)
        assertTrue("actual=$dist", dist in 45.0..55.0)
    }

    @Test
    fun `km to meters conversion rounds correctly`() {
        assertEquals(1234, GeoCalculator.kmToMeters(1.2344))
        assertEquals(1000, GeoCalculator.kmToMeters(1.0))
    }

    @Test
    fun `meters to km conversion`() {
        assertEquals(0.5, GeoCalculator.metersToKm(500), 0.0001)
    }
}
