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

    @Test fun officialCraRatesForEverySupportedYearAndRegion() {
        val expected = listOf(
            Triple(2022, 0.61 to 0.55, 0.65 to 0.59),
            Triple(2023, 0.68 to 0.62, 0.72 to 0.66),
            Triple(2024, 0.70 to 0.64, 0.74 to 0.68),
            Triple(2025, 0.72 to 0.66, 0.76 to 0.70),
            Triple(2026, 0.73 to 0.67, 0.77 to 0.71)
        )
        expected.forEach { (year, province, territory) ->
            val p = CraMileageAllowance.rates(year, false)
            val t = CraMileageAllowance.rates(year, true)
            assertEquals("Province first tier $year", province.first, p.first, 0.0000001)
            assertEquals("Province additional tier $year", province.second, p.additional, 0.0000001)
            assertEquals("Territory first tier $year", territory.first, t.first, 0.0000001)
            assertEquals("Territory additional tier $year", territory.second, t.additional, 0.0000001)
        }
    }

    @Test fun exactThresholdAndAfterThreshold() {
        assertEquals(BigDecimal("3650.00"), CraMileageAllowance.annual(2026, false, 5000.0).amountCad)
        assertEquals(BigDecimal("67.00"), CraMileageAllowance.calculate(2026, false, 5000.0, 100.0).amountCad)
    }
}
