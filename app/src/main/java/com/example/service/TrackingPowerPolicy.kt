package com.example.service

import com.example.data.LatLngPoint

/** Keep the same high accuracy; only relax cadence after sustained stationary monitoring. */
enum class LocationCadence(val intervalMs: Long, val minimumIntervalMs: Long) {
    ACTIVE(3_000L, 1_500L), PARKED(10_000L, 5_000L)
}

class TrackingPowerPolicy {
    companion object { const val PARKED_AFTER_MS = 120_000L }
    private var stationarySince: Long? = null
    private var stationaryAnchor: LatLngPoint? = null

    fun cadence(state: LiveTrackingState, point: LatLngPoint?, elapsedMs: Long): LocationCadence {
        if (state.showsLiveDrive() || !state.isMonitoringMovement || point == null) {
            reset()
            return LocationCadence.ACTIVE
        }
        val anchor = stationaryAnchor
        val moved = anchor != null && haversineDistanceKm(
            anchor.latitude, anchor.longitude, point.latitude, point.longitude
        ) >= 0.015
        if (point.speedKmh > 3f || moved) {
            reset()
            return LocationCadence.ACTIVE
        }
        if (stationarySince == null || elapsedMs < stationarySince!!) {
            stationarySince = elapsedMs
            stationaryAnchor = point
        }
        return if (elapsedMs - stationarySince!! >= PARKED_AFTER_MS) LocationCadence.PARKED else LocationCadence.ACTIVE
    }

    fun reset() { stationarySince = null; stationaryAnchor = null }
}

/** Transition notifications are immediate; changing telemetry is limited to one post per 10s. */
class TrackingNotificationGate {
    companion object { const val UPDATE_INTERVAL_MS = 10_000L }
    private var lastIdentity: String? = null
    private var lastContent: String? = null
    private var lastPostAt = 0L

    fun shouldPost(identity: String, content: String, elapsedMs: Long): Boolean =
        lastIdentity != identity || (lastContent != content &&
            (elapsedMs < lastPostAt || elapsedMs - lastPostAt >= UPDATE_INTERVAL_MS))

    fun posted(identity: String, content: String, elapsedMs: Long) {
        lastIdentity = identity; lastContent = content; lastPostAt = elapsedMs
    }

    fun reset() { lastIdentity = null; lastContent = null; lastPostAt = 0L }
}

/** TripDetector appends immutable points. Reuse JSON while that route is unchanged. */
internal class CheckpointRouteCache {
    private var tripId: Long? = null
    private var size = -1
    private var lastPoint: LatLngPoint? = null
    private var json = "[]"

    fun serialized(trip: ActiveTrip, encode: (List<LatLngPoint>) -> String): String {
        if (tripId != trip.id || size != trip.points.size || lastPoint != trip.points.lastOrNull()) {
            json = encode(trip.points)
            tripId = trip.id; size = trip.points.size; lastPoint = trip.points.lastOrNull()
        }
        return json
    }
    fun clear() { tripId = null; size = -1; lastPoint = null; json = "[]" }
}
