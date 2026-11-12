package com.example

import com.example.data.TripEntity
import com.example.data.VehicleExpenseEntity
import com.example.service.CanadianTaxAdjustments
import com.example.service.CanadianTaxWorksheet
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CanadianTaxWorksheetTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun time(year: Int) = Calendar.getInstance(zone).apply {
        clear(); set(year, Calendar.JUNE, 1)
    }.timeInMillis
    private val expense = VehicleExpenseEntity("Car", 2026, 10000.0, 30000.0,
        3000.0, 1000.0, 1000.0, 100.0, 0.0, 0.0, 200.0, 0.0)
    private val trips = listOf(TripEntity(1, time(2026), time(2026) + 1000,
        10000.0, "Business", vehicle = "Car"))

    @Test fun `ITCs reduce expenses and CCA remains separate`() {
        val result = CanadianTaxWorksheet.calculate(expense, trips,
            CanadianTaxAdjustments(operatingItcCad = 100.0, directExpenseItcCad = 20.0,
                verifiedCcaClaimCad = 400.0, ccaClassAndBasis = "Class 10 reviewed UCC"),
            zone)
        assertEquals(0.5, result.base.businessShare, 0.00001)
        assertEquals(2680.0, result.t2125VehicleExpenses, 0.01)
        assertEquals(400.0, result.t2125CcaSeparate, 0.01)
        assertEquals(3080.0, result.combinedEstimate, 0.01)
    }

    @Test fun `excess ITC and undocumented CCA are rejected`() {
        assertTrue(runCatching { CanadianTaxWorksheet.calculate(expense, trips,
            CanadianTaxAdjustments(operatingItcCad = 99999.0), zone) }.isFailure)
        assertTrue(runCatching { CanadianTaxWorksheet.calculate(expense, trips,
            CanadianTaxAdjustments(verifiedCcaClaimCad = 200.0), zone) }.isFailure)
    }

    @Test fun `2026 passenger loan interest limit uses eligible days`() {
        assertEquals(349.8, CanadianTaxWorksheet.passengerInterestLimit2026(999.0, 30), 0.001)
    }
}
