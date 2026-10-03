package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TripEntity
import com.example.service.TrackingManager
import com.example.ui.MileageViewModel
import com.example.ui.screens.HomeScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenMovingModeTest {

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

    @Test
    fun testIdleHomeScreenShowsStandardDashboard() {
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Standard idle elements exist
        composeTestRule.onNodeWithTag("home_screen").assertExists()
        composeTestRule.onNodeWithTag("home_start_drive_button").assertExists()
        composeTestRule.onNodeWithTag("home_profile_avatar").assertExists()
        // Driving mode layout is not active
        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertDoesNotExist()
    }

    @Test
    fun testMovingModeShowsOnlyTripsAndRemovesWhenClassified() {
        // Seed one unclassified trip directly into Room
        val unclassified = TripEntity(
            startDate = System.currentTimeMillis() - 600000L,
            endDate = System.currentTimeMillis(),
            distanceKm = 5.2,
            type = null,
            startAddress = "742 Evergreen Terrace",
            endAddress = "Springfield Plaza"
        )
        val insertedId = runBlocking {
            AppDatabase.getDatabase(app).tripDao().insertTrip(unclassified)
        }

        // Start manual tracking (driver starts moving)
        viewModel.startManualTracking(app)

        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.trips.value.any { it.id == insertedId }
        }
        composeTestRule.waitForIdle()

        // Driving mode is now active
        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertExists()
        composeTestRule.onNodeWithTag("home_driving_mode_banner").assertExists()
        composeTestRule.onNodeWithTag("home_driving_trips_section").assertExists()
        composeTestRule.onNodeWithTag("home_active_live_trip_card").assertExists()

        // Non-trip overview elements are completely removed
        composeTestRule.onNodeWithTag("home_total_driven_dial").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_profile_avatar").assertDoesNotExist()

        // Unclassified trip is visible with classification options
        composeTestRule.onNodeWithTag("classify_business_$insertedId").assertExists()

        // Tap Business classification button
        composeTestRule.onNodeWithTag("classify_business_$insertedId").performClick()
        composeTestRule.waitForIdle()

        // Verify the trip is classified in Room and removed from the home screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.trips.value.none { it.id == insertedId && it.type == null }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("classify_business_$insertedId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_all_trips_classified_banner").assertExists()
    }

    @Test
    fun testStoppingDriveReturnsToStandardDashboard() {
        // Start tracking
        viewModel.startManualTracking(app)

        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertExists()

        // Stop tracking
        viewModel.stopManualTracking(app)
        composeTestRule.waitForIdle()

        // Standard dashboard is restored
        composeTestRule.onNodeWithTag("home_screen").assertExists()
        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertDoesNotExist()
    }
}
