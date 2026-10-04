package com.example.service

import com.example.data.LatLngPoint
import kotlin.math.*

enum class DetectorState {
    IDLE,
    MOVING,
    RECORDING
}

data class ActiveTrip(
    val id: Long = System.currentTimeMillis(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long = System.currentTimeMillis(),
    var distanceKm: Double = 0.0,
    val points: MutableList<LatLngPoint> = mutableListOf(),
    var lastPoint: LatLngPoint? = null
)

class TripDetector(
    private var dwellMinutes: Int = 5,
    private val onTripStarted: (ActiveTrip) -> Unit,
    private val onTripUpdated: (ActiveTrip) -> Unit,
    private val onTripEnded: (ActiveTrip) -> Unit,
    private val onTripDiscarded: (ActiveTrip) -> Unit
) {
    companion object {
        const val SPEED_THRESHOLD_KMH = 5.0f
        const val MIN_TRIP_KM = 0.25
        const val WARMUP_MS = 25_000L // 25 seconds of continuous movement
    }

    var state: DetectorState = DetectorState.IDLE
        private set

    var currentTrip: ActiveTrip? = null
        private set

    private var movingSince: Long? = null
    private var stoppedSince: Long? = null

    fun updateDwellMinutes(minutes: Int) {
        dwellMinutes = minutes.coerceIn(1, 30)
    }

    fun processPoint(point: LatLngPoint) {
        val speedKmh = if (point.speedKmh > 0) point.speedKmh else estimateSpeed(point)
        val now = point.timestamp

        when (state) {
            DetectorState.IDLE -> {
                if (speedKmh > SPEED_THRESHOLD_KMH) {
                    state = DetectorState.MOVING
                    movingSince = now
                    stoppedSince = null
                    currentTrip = ActiveTrip(
                        id = now,
                        startTime = now,
                        endTime = now,
                        distanceKm = 0.0,
                        points = mutableListOf(point),
                        lastPoint = point
                    )
                }
            }

            DetectorState.MOVING -> {
                val trip = currentTrip
                if (trip == null) {
                    state = DetectorState.IDLE
                    return
                }

                if (speedKmh <= SPEED_THRESHOLD_KMH) {
                    // False positive / drift, return to IDLE
                    state = DetectorState.IDLE
                    currentTrip = null
                    movingSince = null
                } else if (now - (movingSince ?: now) >= WARMUP_MS || speedKmh >= 20.0f) {
                    // Confirmed trip!
                    state = DetectorState.RECORDING
                    accumulateDistance(trip, point)
                    onTripStarted(trip)
                } else {
                    accumulateDistance(trip, point)
                }
            }

            DetectorState.RECORDING -> {
                val trip = currentTrip
                if (trip != null) {
                    accumulateDistance(trip, point)
                    onTripUpdated(trip)

                    val dwellMs = dwellMinutes * 60 * 1000L
                    if (speedKmh <= SPEED_THRESHOLD_KMH) {
                        if (stoppedSince == null) stoppedSince = now
                        if (now - (stoppedSince ?: now) >= dwellMs) {
                            endTrip()
                        }
                    } else {
                        stoppedSince = null
                    }
                }
            }
        }
    }

    fun startManualRecording(): ActiveTrip {
        val now = System.currentTimeMillis()
        val trip = ActiveTrip(
            id = now,
            startTime = now,
            endTime = now,
            distanceKm = 0.0,
            points = mutableListOf(),
            lastPoint = null
        )
        currentTrip = trip
        state = DetectorState.RECORDING
        movingSince = now
        stoppedSince = null
        onTripStarted(trip)
        return trip
    }

    fun endTrip(): ActiveTrip? {
        val trip = currentTrip ?: return null
        trip.endTime = System.currentTimeMillis()
        if (trip.distanceKm >= MIN_TRIP_KM) {
            onTripEnded(trip)
        } else {
            onTripDiscarded(trip)
        }
        state = DetectorState.IDLE
        currentTrip = null
        movingSince = null
        stoppedSince = null
        return trip
    }

    fun reset() {
        state = DetectorState.IDLE
        currentTrip = null
        movingSince = null
        stoppedSince = null
    }

    private fun accumulateDistance(trip: ActiveTrip, point: LatLngPoint) {
        val last = trip.lastPoint
        if (last != null) {
            val d = haversineDistanceKm(last.latitude, last.longitude, point.latitude, point.longitude)
            // Ignore GPS noise/teleport jumps (> 3 km in a single update)
            if (d in 0.002..3.0) {
                trip.distanceKm += d
                trip.points.add(point)
            }
        } else {
            trip.points.add(point)
        }
        trip.lastPoint = point
        trip.endTime = point.timestamp
    }

    private fun estimateSpeed(point: LatLngPoint): Float {
        val trip = currentTrip ?: return 0f
        val last = trip.lastPoint ?: return 0f
        val dKm = haversineDistanceKm(last.latitude, last.longitude, point.latitude, point.longitude)
        val dtHours = (point.timestamp - last.timestamp).toDouble() / 3_600_000.0
        return if (dtHours > 0) (dKm / dtHours).toFloat() else 0f
    }
}

fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
