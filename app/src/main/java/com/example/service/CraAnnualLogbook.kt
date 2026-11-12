package com.example.service

import com.example.data.OdometerReadingEntity
import com.example.data.TripEntity
import java.util.Calendar
import java.util.TimeZone

data class CraAnnualVehicleSummary(
    val vehicle: String,
    val openingKm: Double?,
    val closingKm: Double?,
    val totalKm: Double?,
    val businessKm: Double,
    val unclassifiedTrips: Int,
    val completeYearBoundaries: Boolean
)

/** Evidence summary only. First/last in-year readings are not assumed to be Jan 1/Dec 31. */
object CraAnnualLogbook {
    fun summarize(year: Int, readings: List<OdometerReadingEntity>, trips: List<TripEntity>,
                  zone: TimeZone = TimeZone.getDefault()): List<CraAnnualVehicleSummary> {
        val start = Calendar.getInstance(zone).apply {
            clear(); set(year, Calendar.JANUARY, 1)
        }.timeInMillis
        val end = Calendar.getInstance(zone).apply {
            clear(); set(year + 1, Calendar.JANUARY, 1)
        }.timeInMillis
        val vehicles = (readings.map { it.vehicle } + trips.mapNotNull { it.vehicle }).filter { it.isNotBlank() }.distinct().sorted()
        return vehicles.map { vehicle ->
            val annualReadings = readings.filter { it.vehicle == vehicle && it.recordedAt >= start && it.recordedAt < end }
                .sortedWith(compareBy<OdometerReadingEntity> { it.recordedAt }.thenBy { it.id })
            val first = annualReadings.firstOrNull()
            val last = annualReadings.lastOrNull()
            val annualTrips = trips.filter { it.vehicle == vehicle && it.startDate >= start && it.startDate < end }
            val change = if (first != null && last != null && first.id != last.id && last.readingKm >= first.readingKm)
                last.readingKm - first.readingKm else null
            val firstDayEnd = start + 24L * 60 * 60 * 1000
            val lastDayStart = end - 24L * 60 * 60 * 1000
            CraAnnualVehicleSummary(vehicle, first?.readingKm, last?.readingKm, change,
                annualTrips.filter { it.type == "Business" }.sumOf { it.distanceKm },
                annualTrips.count { it.type == null },
                first != null && last != null && first.recordedAt < firstDayEnd && last.recordedAt >= lastDayStart)
        }
    }
}
