package com.example

import com.example.service.Odometer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerTest {

    @Test
    fun `parses an ELM327 odometer frame`() {
        // 0x000F4240 = 1_000_000 counts = 100_000.0 km
        assertEquals(100_000.0, Odometer.parseElmKm("41 A6 00 0F 42 40\r>")!!, 0.001)
        assertEquals(100_000.0, Odometer.parseElmKm("7E80641A6000F4240")!!, 0.001)
    }

    @Test
    fun `rejects a missing or zero odometer`() {
        assertNull(Odometer.parseElmKm("NO DATA"))
        assertNull(Odometer.parseElmKm("SEARCHING...\r7F0112\r>"))
        assertNull(Odometer.parseElmKm("41 A6 00 00 00 00"))
    }

    @Test
    fun `logs only a real increase`() {
        assertNull(Odometer.tripKm(null, 1000.0))
        assertNull(Odometer.tripKm(1000.0, 1000.0))
        assertNull(Odometer.tripKm(1000.0, 900.0))
        assertEquals(42.5, Odometer.tripKm(1000.0, 1042.5)!!, 0.001)
    }
}
