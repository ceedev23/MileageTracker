package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LatLngPoint
import com.example.service.DetectorState
import com.example.service.TrackingManager
import com.example.service.TripDetector
import com.example.ui.MileageViewModel
import com.example.ui.screens.HomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripDwellEndTripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: MileageViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        TrackingManager.resetForTesting()
        runBlocking {
            AppDatabase.getDatabase(app).tripDao().clearAllTrips()
        }
        viewModel = MileageViewModel(app)
    }

    @After
    fun tearDown() {
        TrackingManager.resetForTesting()
    }

    private fun waitForClassification(tripId: Long, expectedType: String) {
        // UI idleness does not await Room's asynchronous write. Keep the wait
        // bounded and inspect the exact stored row, so a failed update still fails.
        composeTestRule.waitUntil(5000) {
            runBlocking {
                AppDatabase.getDatabase(app).tripDao().getTripById(tripId)?.type == expectedType
            }
        }
    }

    @Test
    fun testDwellSettingSynchronizesFromSettings() {
        // Change dwell time via settings
        viewModel.changeDwell(2) // 5 + 2 = 7 min
        assertEquals(7, viewModel.settings.value.dwellMinutes)
        assertEquals(7, TrackingManager.getDwellMinutes())

        viewModel.changeDwell(-4) // 7 - 4 = 3 min
        assertEquals(3, viewModel.settings.value.dwellMinutes)
        assertEquals(3, TrackingManager.getDwellMinutes())
    }

    @Test
    fun testTripStopsAfterDwellTimeWhenStopped() {
        var ended = false
        var savedTripDistance = 0.0

        val detector = TripDetector(
            dwellMinutes = 2,
            onTripStarted = {},
            onTripUpdated = {},
            onTripEnded = { trip ->
                ended = true
                savedTripDistance = trip.distanceKm
            },
            onTripDiscarded = {}
        )

        val t0 = 1000000L
        // 1. First moving point transitions IDLE -> MOVING
        detector.processPoint(LatLngPoint(37.7749, -122.4194, timestamp = t0, speedKmh = 25f))
        assertEquals(DetectorState.MOVING, detector.state)

        // 2. Continuous speed >= 20 km/h transitions to RECORDING and drives for > 0.25 km
        detector.processPoint(LatLngPoint(37.7840, -122.4194, timestamp = t0 + 30000L, speedKmh = 30f))
        assertEquals(DetectorState.RECORDING, detector.state)
        assertTrue("Trip distance should be > 0.25 km", (detector.currentTrip?.distanceKm ?: 0.0) >= 0.25)

        // 3. Driver stops (speed <= 5 km/h)
        val stopTime = t0 + 60000L
        detector.processPoint(LatLngPoint(37.7841, -122.4194, timestamp = stopTime, speedKmh = 0f))
        assertFalse("Should still be recording while stopped within dwell delay", ended)

        // 4. Time passes but less than dwell time (1 minute < 2 minutes)
        detector.checkDwellTimeout(stopTime + 60000L)
        assertFalse("Trip should not end before dwell time expires", ended)

        // 5. Driver remains stopped for full dwell time (2 minutes = 120,000 ms)
        detector.checkDwellTimeout(stopTime + 120000L)
        assertTrue("Trip must end when driver stops for configured dwell minutes", ended)
        assertEquals(DetectorState.IDLE, detector.state)
        assertNull(detector.currentTrip)
        assertTrue(savedTripDistance >= 0.25)
    }

    @Test
    fun testStoppedTripShowsOnHomeScreenForClassification() {
        // Set dwell delay to 2 minutes
        viewModel.changeDwell(-3) // 5 - 3 = 2 min
        assertEquals(2, viewModel.settings.value.dwellMinutes)

        // Simulate a trip saved to DB with type = null (unclassified)
        val entity = com.example.data.TripEntity(
            startDate = 1700000000000L,
            endDate = 1700001200000L,
            distanceKm = 12.4,
            type = null, // unclassified
            startAddress = "123 Market St",
            endAddress = "456 Mission St",
            purpose = "Drive (12.4 km)",
            vehicle = "Primary Vehicle"
        )

        val insertedId = runBlocking {
            AppDatabase.getDatabase(app).tripDao().insertTrip(entity)
        }

        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        // Wait for Room flow emission to recompose HomeScreen with the trip card
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("classify_business_$insertedId").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Home Screen shows the unclassified trip card
        composeTestRule.onNodeWithText("1 trip to review", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Review and classify below", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("classify_personal_$insertedId").assertExists()
        composeTestRule.onNodeWithTag("classify_business_$insertedId").assertExists()

        // Classify trip as Business via viewModel or UI
        viewModel.classifyTrip(entity.copy(id = insertedId), "Business")
        waitForClassification(insertedId, "Business")

        // Verify trip was classified as Business
        val trips = runBlocking {
            AppDatabase.getDatabase(app).tripDao().getAllTrips().first()
        }
        assertEquals(1, trips.size)
        assertEquals("Business", trips[0].type)
    }

    @Test
    fun testFullDriverStopDwellEndsTripAndPutsCardOnHomeScreen() {
        // Set dwell delay under 'End trip after stopping' to 2 minutes
        viewModel.changeDwell(-3) // 5 - 3 = 2 min
        assertEquals(2, TrackingManager.getDwellMinutes())

        TrackingManager.init(app)

        val t0 = 2000000L
        // 1. Driver starts driving
        TrackingManager.processGpsPoint(LatLngPoint(37.7749, -122.4194, timestamp = t0, speedKmh = 25f))
        TrackingManager.processGpsPoint(LatLngPoint(37.7840, -122.4194, timestamp = t0 + 10000L, speedKmh = 30f))

        // Live drive is actively showing
        assertTrue("Live drive should show while driving", TrackingManager.trackingState.value.showsLiveDrive())

        // 2. Driver stops (speed = 0)
        val stopTime = t0 + 30000L
        TrackingManager.processGpsPoint(LatLngPoint(37.7841, -122.4194, timestamp = stopTime, speedKmh = 0f))
        assertTrue("Live drive still shows during dwell delay", TrackingManager.trackingState.value.showsLiveDrive())

        // 3. Driver remains stopped for the 2 minutes set under 'End trip after stopping'
        val dwellExpiredTime = stopTime + (2 * 60 * 1000L) + 1000L
        TrackingManager.processGpsPoint(LatLngPoint(37.7841, -122.4194, timestamp = dwellExpiredTime, speedKmh = 0f))

        // Trip should now be ended and live drive dismissed
        assertFalse("Live drive should dismiss when stopped for dwell delay", TrackingManager.trackingState.value.showsLiveDrive())

        // Verify Home Screen displays the unclassified trip card
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("1 trip to review", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Review and classify below", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Personal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Business", useUnmergedTree = true).assertExists()

        // Classify trip as Personal
        val savedTrips = runBlocking {
            AppDatabase.getDatabase(app).tripDao().getAllTrips().first()
        }
        assertEquals(1, savedTrips.size)
        assertNull("Saved trip should be unclassified initially", savedTrips[0].type)

        viewModel.classifyTrip(savedTrips[0], "Personal")
        waitForClassification(savedTrips[0].id, "Personal")

        val classifiedTrips = runBlocking {
            AppDatabase.getDatabase(app).tripDao().getAllTrips().first()
        }
        assertEquals(1, classifiedTrips.size)
        assertEquals("Personal", classifiedTrips[0].type)
    }
}
