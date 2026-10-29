package com.example

import com.example.data.LatLngPoint
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test

class TrackingPowerPolicyTest {
    private val idle = LiveTrackingState(isMonitoringMovement = true)
    private fun point(lat: Double = 43.0, speed: Float = 0f, time: Long = 1000L) = LatLngPoint(lat, -79.0, time, speed)
    private fun parked(policy: TrackingPowerPolicy) {
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 0))
        assertEquals(LocationCadence.PARKED, policy.cadence(idle, point(), 120000))
    }

    @Test fun `monitoring starts fast and only relaxes after two stationary minutes`() {
        val policy = TrackingPowerPolicy()
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, null, 0))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 1000))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 120999))
        assertEquals(LocationCadence.PARKED, policy.cadence(idle, point(), 121000))
    }
    @Test fun `recording stays fast even when stopped at lights`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle.copy(isTracking = true, detectorState = DetectorState.RECORDING), point(), 600000))
    }
    @Test fun `drive warmup immediately switches out of parked cadence`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle.copy(detectorState = DetectorState.MOVING), point(), 121000))
    }
    @Test fun `reported movement resets stationary timer`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(speed = 4f), 121000))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 130000))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 249999))
        assertEquals(LocationCadence.PARKED, policy.cadence(idle, point(), 250000))
    }
    @Test fun `displacement promotes even if provider reports zero speed`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(lat = 43.0002), 130000))
    }
    @Test fun `reset and monotonic clock rollback require fresh stationary observation`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        policy.reset()
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 200000))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(idle, point(), 1))
    }
    @Test fun `manual tracking or disabled monitoring never uses parked cadence`() {
        val policy = TrackingPowerPolicy(); parked(policy)
        assertEquals(LocationCadence.ACTIVE, policy.cadence(LiveTrackingState(isTracking = true), point(), 200000))
        assertEquals(LocationCadence.ACTIVE, policy.cadence(LiveTrackingState(), point(), 400000))
    }
    @Test fun `actual detector promotes after parked samples without losing delivered route points`() {
        var started = false
        val detector = TripDetector(onTripStarted = { started = true }, onTripUpdated = {}, onTripEnded = {}, onTripDiscarded = {})
        val policy = TrackingPowerPolicy()
        fun deliver(point: LatLngPoint, elapsed: Long): LocationCadence {
            detector.processPoint(point)
            return policy.cadence(idle.copy(detectorState = detector.state, isTracking = detector.state == DetectorState.RECORDING), point, elapsed)
        }
        deliver(point(time = 1000), 0)
        assertEquals(LocationCadence.PARKED, deliver(point(time = 121000), 120000))
        val firstMove = point(43.001, time = 131000)
        assertEquals(LocationCadence.ACTIVE, deliver(firstMove, 130000))
        val next = point(43.0015, time = 134000)
        assertEquals(LocationCadence.ACTIVE, deliver(next, 133000))
        assertTrue(started)
        assertTrue(detector.currentTrip!!.points.contains(firstMove))
        assertEquals(next, detector.currentTrip!!.lastPoint)
        assertTrue(detector.currentTrip!!.distanceKm > .1)
    }
    @Test fun `notification telemetry is throttled but state transitions are immediate`() {
        val gate = TrackingNotificationGate()
        assertTrue(gate.shouldPost("monitor", "parked", 0)); gate.posted("monitor", "parked", 0)
        assertFalse(gate.shouldPost("monitor", "moving", 9999))
        assertTrue(gate.shouldPost("recording-trip-1", "distance", 100))
        gate.posted("recording-trip-1", "distance", 100)
        assertFalse(gate.shouldPost("recording-trip-1", "new distance", 101))
        assertTrue(gate.shouldPost("recording-trip-1", "new distance", 10100))
        assertTrue(gate.shouldPost("monitor", "parked", 102))
        assertTrue(gate.shouldPost("recording-trip-2", "distance", 102))
    }
    @Test fun `unchanged notifications stay quiet and failed posts can retry`() {
        val gate = TrackingNotificationGate()
        assertTrue(gate.shouldPost("monitor", "parked", 0))
        assertTrue(gate.shouldPost("monitor", "parked", 1)) // not posted yet
        gate.posted("monitor", "parked", 1)
        assertFalse(gate.shouldPost("monitor", "parked", 100000))
        gate.reset()
        assertTrue(gate.shouldPost("monitor", "parked", 100001))
    }
    @Test fun `unchanged route reuses JSON while appended points are serialized`() {
        val cache = CheckpointRouteCache()
        val trip = ActiveTrip(id = 1, points = mutableListOf(point()))
        var encodes = 0
        val encode: (List<LatLngPoint>) -> String = { encodes++; "route-${it.size}" }
        assertEquals("route-1", cache.serialized(trip, encode))
        trip.endTime += 10000
        assertEquals("route-1", cache.serialized(trip, encode))
        assertEquals(1, encodes)
        trip.points.add(point(43.001))
        assertEquals("route-2", cache.serialized(trip, encode))
        assertEquals(2, encodes)
    }
    @Test fun `new trips and reset cannot reuse another route`() {
        val cache = CheckpointRouteCache()
        var encodes = 0
        val encode: (List<LatLngPoint>) -> String = { (++encodes).toString() }
        cache.serialized(ActiveTrip(id = 1), encode)
        cache.serialized(ActiveTrip(id = 2), encode)
        cache.clear()
        cache.serialized(ActiveTrip(id = 2), encode)
        assertEquals(3, encodes)
    }
}
