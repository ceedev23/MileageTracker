package com.example

import com.example.data.TripEntity
import com.example.service.currentTripText
import com.example.service.isPendingTrip
import com.example.service.newTripText
import com.example.service.pendingTripText
import com.example.service.tripUpdates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNotifierTest {

    @Test
    fun existingTripsAreNotAnnouncedAsNew() {
        val update = tripUpdates(null, listOf(trip(1, null), trip(2, "Business")))
        assertNull(update.newTrip)
        assertEquals(1, update.pendingCount)
    }

    @Test
    fun oneInsertedTripIsANewTrip() {
        val added = trip(3, null)
        val update = tripUpdates(setOf(1), listOf(trip(1, "Business"), added))
        assertEquals(3L, update.newTrip?.id)
        assertTrue(isPendingTrip(update.newTrip!!))
        assertEquals(1, update.pendingCount)
    }

    @Test
    fun aBatchOnlyUpdatesThePendingCount() {
        val update = tripUpdates(emptySet(), listOf(trip(1, null), trip(2, "Unclassified")))
        assertNull(update.newTrip)
        assertEquals(2, update.pendingCount)
    }

    @Test
    fun noticeCopyMatchesTheThreeUpdates() {
        assertEquals("Current trip" to "3.2 km • 40 km/h", currentTripText(3.2, 40f))
        assertEquals("New trip" to "12.0 km • tap to classify", newTripText(trip(1, null, 12.0)))
        assertEquals("New trip" to "4.5 km • Business", newTripText(trip(2, "Business", 4.5)))
        assertEquals("1 trip to classify", pendingTripText(1))
        assertEquals("3 trips to classify", pendingTripText(3))
        assertFalse(isPendingTrip(trip(2, "Personal")))
    }

    private fun trip(id: Long, type: String?, km: Double = 1.0) = TripEntity(
        id = id,
        startDate = 1L,
        endDate = 2L,
        distanceKm = km,
        type = type
    )
}
