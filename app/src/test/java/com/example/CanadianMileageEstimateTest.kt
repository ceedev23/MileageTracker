package com.example

import com.example.data.TripEntity
import com.example.service.CanadianMileageEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class CanadianMileageEstimateTest {
    private val zone = TimeZone.getTimeZone("America/Toronto")
    private val start = Instant.parse("2026-06-01T12:00:00Z").toEpochMilli()

    private fun trip(km: Double, id: Long = 1, date: Long = start, type: String? = "Business") =
        TripEntity(id = id, distanceKm = km, startDate = date, endDate = date + 60_000, type = type)

    @Test fun `distance below the threshold uses the first rate`() {
        val trip = trip(1_000.0)
        assertEquals(730.0, CanadianMileageEstimate(listOf(trip), zone).forTrip(trip)!!, 0.0)
    }

    @Test fun `distance at the threshold uses the first rate`() {
        val trip = trip(5_000.0)
        assertEquals(3_650.0, CanadianMileageEstimate(listOf(trip), zone).forTrip(trip)!!, 0.0)
    }

    @Test fun `15000 business km gives 10350 dollars`() {
        val trip = trip(15_000.0)
        assertEquals(10_350.0, CanadianMileageEstimate(listOf(trip), zone).forTrip(trip)!!, 0.0)
    }

    @Test fun `threshold can be crossed mid-trip and only prior business trips count`() {
        val prior = trip(4_990.0, id = 1)
        val personal = trip(500.0, id = 2, type = "Personal")
        val crossing = trip(30.0, id = 3, date = start + 100_000)
        val after = trip(20.0, id = 4, date = start + 200_000)
        val priorYear = trip(600.0, id = 5, date = Instant.parse("2026-01-01T04:59:59Z").toEpochMilli())
        val estimates = CanadianMileageEstimate(listOf(after, crossing, personal, priorYear, prior), zone)

        assertNull(estimates.forTrip(priorYear))
        assertEquals(0.0, estimates.forTrip(personal)!!, 0.0)
        assertEquals(20.70, estimates.forTrip(crossing)!!, 0.0)
        assertEquals(13.40, estimates.forTrip(after)!!, 0.0)

        val pending = crossing.copy(type = null)
        val live = CanadianMileageEstimate(listOf(prior, personal, priorYear, pending), zone)
        assertEquals(0.0, live.forTrip(pending)!!, 0.0)
        assertEquals(20.70, live.forTrip(pending, previewBusiness = true)!!, 0.0)
        assertEquals(20.70, live.forLiveTrip(crossing.startDate, 30.0, null)!!, 0.0)
    }
}
