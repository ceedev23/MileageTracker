package com.example

import com.example.data.OdometerReadingEntity
import com.example.data.TripEntity
import com.example.service.CraAnnualLogbook
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CraAnnualLogbookTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day) }.timeInMillis

    @Test fun `odometer difference is not automatically business mileage`() {
        val readings = listOf(
            OdometerReadingEntity(1, "Car", date(2026, 1, 1), 1000.0, "manual"),
            OdometerReadingEntity(2, "Car", date(2026, 12, 31), 1030.0, "manual")
        )
        val trips = listOf(TripEntity(1, date(2026, 6, 1), date(2026, 6, 1) + 60000, 20.0,
            "Business", vehicle = "Car"))
        val summary = CraAnnualLogbook.summarize(2026, readings, trips, zone).single()
        assertEquals(30.0, summary.totalKm!!, 0.001)
        assertEquals(20.0, summary.businessKm, 0.001)
        assertTrue(summary.completeYearBoundaries)
    }

    @Test fun `incomplete year boundary is clearly flagged`() {
        val readings = listOf(
            OdometerReadingEntity(1, "Car", date(2026, 3, 1), 1000.0, "manual"),
            OdometerReadingEntity(2, "Car", date(2026, 9, 1), 1030.0, "manual"))
        val summary = CraAnnualLogbook.summarize(2026, readings, emptyList(), zone).single()
        assertFalse(summary.completeYearBoundaries)
    }
}
