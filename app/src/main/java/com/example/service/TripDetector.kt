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
    dwellMinutes: Int = 5,
    private val onTripStarted: (ActiveTrip) -> Unit,
    private val onTripUpdated: (ActiveTrip) -> Unit,
    private val onTripEnded: (ActiveTrip) -> Unit,
    private val onTripDiscarded: (ActiveTrip) -> Unit
) {
    companion object {
        const val SPEED_THRESHOLD_KMH = 5.0f
        const val MIN_TRIP_KM = 0.25
        const val WARMUP_MS = 25_000L // 25 seconds of continuous movement
        const val WARMUP_ABORT_MS = 15_000L // pause this long during warmup and it was not a drive
    }

    var dwellMinutes: Int = dwellMinutes
        private set

    var state: DetectorState = DetectorState.IDLE
        private set

    var currentTrip: ActiveTrip? = null
        private set

    private var movingSince: Long? = null
    private var stoppedSince: Long? = null
    private var lastIdlePoint: LatLngPoint? = null
    private val speedWindow = ArrayDeque<LatLngPoint>()

    fun updateDwellMinutes(minutes: Int) {
        dwellMinutes = minutes.coerceIn(1, 30)
    }

    fun processPoint(point: LatLngPoint) {
        val speedKmh = if (point.speedKmh > 0) point.speedKmh else estimateSpeed(point)
        rememberPoint(point)
        val now = point.timestamp

        when (state) {
            DetectorState.IDLE -> {
                if (speedKmh > SPEED_THRESHOLD_KMH) {
                    state = DetectorState.MOVING
                    movingSince = now
                    stoppedSince = null
                    val startPt = lastIdlePoint ?: point
                    val initialDist = if (startPt != point) {
                        haversineDistanceKm(startPt.latitude, startPt.longitude, point.latitude, point.longitude)
                            .coerceIn(0.0, 3.0)
                    } else {
                        0.0
                    }
                    currentTrip = ActiveTrip(
                        id = now,
                        startTime = startPt.timestamp,
                        endTime = now,
                        distanceKm = initialDist,
                        points = if (startPt != point) mutableListOf(startPt, point) else mutableListOf(point),
                        lastPoint = point
                    )
                } else {
                    lastIdlePoint = point
                }
            }

            DetectorState.MOVING -> {
                val trip = currentTrip
                if (trip == null) {
                    state = DetectorState.IDLE
                    lastIdlePoint = point
                    return
                }

                if (speedKmh <= SPEED_THRESHOLD_KMH) {
                    // Pulling out of a spot often reports one slow fix; only cancel after a real pause.
                    if (stoppedSince == null) stoppedSince = now
                    if (now - (stoppedSince ?: now) >= WARMUP_ABORT_MS) {
                        state = DetectorState.IDLE
                        currentTrip = null
                        movingSince = null
                        stoppedSince = null
                        lastIdlePoint = point
                    }
                } else {
                    stoppedSince = null
                    accumulateDistance(trip, point)
                    if (now - (movingSince ?: now) >= WARMUP_MS || speedKmh >= 20.0f) {
                        state = DetectorState.RECORDING
                        onTripStarted(trip)
                    }
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

    fun checkDwellTimeout(now: Long = System.currentTimeMillis()): ActiveTrip? {
        if (state == DetectorState.RECORDING) {
            val trip = currentTrip ?: return null
            val dwellMs = dwellMinutes * 60 * 1000L
            val lastTime = trip.lastPoint?.timestamp ?: trip.startTime
            val stopped = stoppedSince ?: (if (now - lastTime >= dwellMs) lastTime else null)
            if (stopped != null && (now - stopped) >= dwellMs) {
                return endTrip()
            }
        }
        return null
    }

    fun endTrip(): ActiveTrip? {
        val trip = currentTrip ?: return null
        trip.endTime = stoppedSince ?: trip.lastPoint?.timestamp ?: System.currentTimeMillis()
        if (trip.distanceKm >= MIN_TRIP_KM) {
            onTripEnded(trip)
        } else {
            onTripDiscarded(trip)
        }
        state = DetectorState.IDLE
        currentTrip = null
        movingSince = null
        stoppedSince = null
        lastIdlePoint = null
        speedWindow.clear()
        return trip
    }

    fun reset() {
        state = DetectorState.IDLE
        currentTrip = null
        movingSince = null
        stoppedSince = null
        lastIdlePoint = null
        speedWindow.clear()
    }

    private fun accumulateDistance(trip: ActiveTrip, point: LatLngPoint) {
        val last = trip.lastPoint
        if (last != null) {
            val d = haversineDistanceKm(last.latitude, last.longitude, point.latitude, point.longitude)
            // Ignore GPS noise/teleport jumps (> 3 km in a single update)
            if (d in 0.005..3.0) {
                trip.distanceKm += d
                trip.points.add(point)
            }
        } else {
            trip.points.add(point)
        }
        trip.lastPoint = point
        trip.endTime = point.timestamp
    }

    private fun rememberPoint(point: LatLngPoint) {
        speedWindow.addLast(point)
        val cutoff = point.timestamp - 20_000L
        while (speedWindow.isNotEmpty() && speedWindow.first().timestamp < cutoff) {
            speedWindow.removeFirst()
        }
    }

    private fun estimateSpeed(point: LatLngPoint): Float {
        // Fixes are often < 15m apart, so measure across the recent window instead of one step.
        val now = point.timestamp
        val window = speedWindow.filter { now - it.timestamp in 1_000L..20_000L }
        val prior = currentTrip?.lastPoint ?: lastIdlePoint
        val anchor = window.lastOrNull { now - it.timestamp >= 4_000L }
            ?: window.firstOrNull()
            ?: prior?.takeIf { now - it.timestamp in 1_000L..20_000L }
            ?: return 0f
        val dKm = haversineDistanceKm(anchor.latitude, anchor.longitude, point.latitude, point.longitude)
        if (dKm < 0.015) return 0f
        val dtMillis = maxOf(1000L, now - anchor.timestamp)
        val dtHours = dtMillis.toDouble() / 3_600_000.0
        return (dKm / dtHours).toFloat()
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
