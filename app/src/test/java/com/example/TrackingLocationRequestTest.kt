package com.example

import android.app.Application
import com.example.service.LocationCadence
import com.example.service.trackingLocationRequest
import com.google.android.gms.location.Priority
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrackingLocationRequestTest {
    @Test fun `active drive request retains original accuracy and intervals`() {
        val request = trackingLocationRequest(LocationCadence.ACTIVE)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.priority)
        assertEquals(3000L, request.intervalMillis)
        assertEquals(1500L, request.minUpdateIntervalMillis)
        assertEquals(0L, request.maxUpdateAgeMillis)
        assertFalse(request.isBatched)
    }
    @Test fun `parked request stays accurate and unbatched with bounded slower interval`() {
        val request = trackingLocationRequest(LocationCadence.PARKED)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.priority)
        assertEquals(10000L, request.intervalMillis)
        assertEquals(5000L, request.minUpdateIntervalMillis)
        assertEquals(0L, request.maxUpdateAgeMillis)
        assertFalse(request.isBatched)
    }
}
