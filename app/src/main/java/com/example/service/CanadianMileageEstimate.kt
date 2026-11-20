package com.example.service

import com.example.data.TripEntity
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToLong

/** The 2026 provincial automobile allowance benchmark, in CAD. */
class CanadianMileageEstimate(trips: List<TripEntity>, zone: TimeZone = TimeZone.getDefault()) {
    private val taxYear = Calendar.getInstance(zone).run {
        clear()
        set(2026, Calendar.JANUARY, 1)
        val start = timeInMillis
        add(Calendar.YEAR, 1)
        start until timeInMillis
    }
    private val businessTrips = trips.filter {
        it.type == "Business" && it.startDate in taxYear && it.distanceKm.isFinite() && it.distanceKm > 0.0
    }.sortedWith(compareBy<TripEntity> { it.startDate }.thenBy { it.id })
    private val businessKm = DoubleArray(businessTrips.size + 1).apply {
        businessTrips.forEachIndexed { index, trip -> this[index + 1] = this[index] + trip.distanceKm }
    }

    fun forTrip(trip: TripEntity, previewBusiness: Boolean = false): Double? =
        value(trip.startDate, trip.id, trip.distanceKm, trip.type, previewBusiness)

    fun forLiveTrip(startDate: Long, distanceKm: Double, category: String?): Double? =
        value(startDate, Long.MAX_VALUE, distanceKm, category, previewBusiness = true)

    private fun value(
        startDate: Long, id: Long, distanceKm: Double, category: String?, previewBusiness: Boolean
    ): Double? {
        if (startDate !in taxYear || !distanceKm.isFinite() || distanceKm < 0.0) return null
        val pending = category.isNullOrEmpty() || category == "Unclassified"
        if (category != "Business" && !(previewBusiness && pending)) return 0.0
        val position = businessTrips.binarySearch {
            val dateOrder = it.startDate.compareTo(startDate)
            if (dateOrder == 0) it.id.compareTo(id) else dateOrder
        }
        val priorKm = businessKm[if (position >= 0) position else -position - 1]
        // Use the change in cumulative cents so exported trip values add up to the annual estimate.
        return (totalCents(priorKm + distanceKm) - totalCents(priorKm)) / 100.0
    }

    private fun totalCents(km: Double): Long =
        (minOf(km, 5_000.0) * 73 + (km - 5_000.0).coerceAtLeast(0.0) * 67).roundToLong()
}
