package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class CraMileageAllowanceTest {
    @Test fun provincial2026Threshold() {
        val result = CraMileageAllowance.annual(2026, false, 10000.0)
        assertEquals(5000.0, result.firstTierKm, 0.0)
        assertEquals(5000.0, result.additionalTierKm, 0.0)
        assertEquals(BigDecimal("7000.00"), result.amountCad)
    }

    @Test fun territorial2026Threshold() {
        assertEquals(BigDecimal("7400.00"), CraMileageAllowance.annual(2026, true, 10000.0).amountCad)
    }

    @Test fun crossingThresholdWithPriorDistance() {
        val result = CraMileageAllowance.calculate(2026, false, 4900.0, 200.0)
        assertEquals(100.0, result.firstTierKm, 0.0)
        assertEquals(100.0, result.additionalTierKm, 0.0)
        assertEquals(BigDecimal("140.00"), result.amountCad)
    }

    @Test fun zeroDistance() {
        assertEquals(BigDecimal("0.00"), CraMileageAllowance.annual(2026, false, 0.0).amountCad)
    }

    @Test fun historicalRates() {
        assertEquals(BigDecimal("6900.00"), CraMileageAllowance.annual(2025, false, 10000.0).amountCad)
    }

    @Test fun unknownFutureYearRejected() {
        assertThrows(IllegalArgumentException::class.java) { CraMileageAllowance.annual(2027, false, 1000.0) }
    }

    @Test fun invalidDistancesRejected() {
        assertThrows(IllegalArgumentException::class.java) { CraMileageAllowance.annual(2026, false, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { CraMileageAllowance.annual(2026, false, Double.NaN) }
    }
}
