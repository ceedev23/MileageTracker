package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerTextParserTest {
    @Test fun readsPlainOdometer() {
        assertEquals("123456", OdometerTextParser.candidate("123456"))
    }

    @Test fun readsLabeledOdometerAmongOtherNumbers() {
        assertEquals("123456", OdometerTextParser.candidate("Speed 80\nODO 123456\nTrip 437"))
    }

    @Test fun readsGroupedThousands() {
        assertEquals("123456.7", OdometerTextParser.candidate("ODOMETER: 123,456.7 km"))
    }

    @Test fun refusesAmbiguousUnlabeledNumbers() {
        assertNull(OdometerTextParser.candidate("123456\n654321"))
    }

    @Test fun refusesEmptyText() {
        assertNull(OdometerTextParser.candidate(""))
    }

    @Test fun refusesUnrealisticOdometer() {
        assertNull(OdometerTextParser.candidate("ODO 12"))
    }
}
