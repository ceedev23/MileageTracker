package com.example.service

import com.example.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReportSnapshotCraTest {
    private val zone = TimeZone.getTimeZone("America/Toronto")

    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis

    private fun trip(id: Long, year: Int, month: Int, day: Int, km: Double, type: String? = "Business") =
        TripEntity(id = id, startDate = date(year, month, day), endDate = date(year, month, day) + 60000L,
            distanceKm = km, type = type)

    private fun snapshot(trips: List<TripEntity>, period: ReportPeriod, now: Long, territory: Boolean = false) =
        ReportSnapshot.create(trips, emptyList(), period, false, 0.5, now, zone, "CA", territory)

    @Test fun monthlyReportCarriesEarlierYearBusinessDistance() {
        val s = snapshot(listOf(trip(1, 2026, 1, 10, 4800.0), trip(2, 2026, 10, 10, 500.0)),
            ReportPeriod.THIS_MONTH, date(2026, 10, 31))
        assertEquals(1, s.trips.size)
        assertEquals(4800.0, s.priorBusinessKmByYear[2026]!!, 0.0)
        assertEquals(341.00, s.craTripEstimates()[2L]!!, 0.001)
        assertEquals("341.00", AccountantReportExporter.tripCsv(s).lines()[1].split(",").last())
    }

    @Test fun personalTripsDoNotConsumeAllowanceTier() {
        val s = snapshot(listOf(trip(1, 2026, 1, 10, 4500.0), trip(2, 2026, 2, 10, 900.0, "Personal"),
            trip(3, 2026, 10, 10, 1000.0)), ReportPeriod.THIS_MONTH, date(2026, 10, 31))
        assertEquals(700.0, s.craTripEstimates()[3L]!!, 0.001)
    }

    @Test fun calendarYearThresholdResets() {
        val s = snapshot(listOf(trip(1, 2025, 12, 10, 7000.0), trip(2, 2026, 1, 10, 1000.0)),
            ReportPeriod.THIS_YEAR, date(2026, 10, 31))
        assertEquals(730.0, s.craTripEstimates()[2L]!!, 0.001)
        assertEquals(0.0, s.priorBusinessKmByYear[2026] ?: 0.0, 0.0)
    }

    @Test fun territorialRateCarriesEarlierKilometres() {
        val s = snapshot(listOf(trip(1, 2026, 1, 10, 4900.0), trip(2, 2026, 10, 10, 200.0)),
            ReportPeriod.THIS_MONTH, date(2026, 10, 31), true)
        assertEquals(148.0, s.craTripEstimates()[2L]!!, 0.001)
    }

    @Test fun allTimeUsesSeparateYearlyThresholds() {
        val s = snapshot(listOf(trip(1, 2025, 12, 10, 6000.0), trip(2, 2026, 1, 10, 6000.0)),
            ReportPeriod.ALL, date(2026, 10, 31))
        assertEquals(4260.0, s.craTripEstimates()[1L]!!, 0.001)
        assertEquals(4320.0, s.craTripEstimates()[2L]!!, 0.001)
    }
}
