package com.example.service

import com.example.data.TripEntity

/** Suggested shifts preserve individual trip records and may span midnight. */
data class TripShift(val trips: List<TripEntity>) {
    val start: Long get() = trips.minOf { it.startDate }
    val end: Long get() = trips.maxOf { it.endDate }
}

object TripShifts {
    const val BREAK_MS = 4L * 60 * 60 * 1000
    const val MAX_SHIFT_MS = 16L * 60 * 60 * 1000
    fun group(trips: List<TripEntity>): List<TripShift> {
        val shifts = mutableListOf<TripShift>()
        for (vehicleTrips in trips.groupBy { it.vehicle.orEmpty() }.values) {
            var current = mutableListOf<TripEntity>()
            for (trip in vehicleTrips.sortedWith(compareBy<TripEntity> { it.startDate }.thenBy { it.id })) {
                if (current.isNotEmpty() && (trip.startDate - current.maxOf { it.endDate } > BREAK_MS ||
                        trip.endDate - current.first().startDate > MAX_SHIFT_MS)) {
                    shifts += TripShift(current.toList()); current = mutableListOf()
                }
                current += trip
            }
            if (current.isNotEmpty()) shifts += TripShift(current.toList())
        }
        return shifts.sortedByDescending { it.start }
    }
}
