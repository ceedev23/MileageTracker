package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LatLngPoint
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
        composeTestRule.onNodeWithTag("home_start_drive_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_profile_avatar").assertExists()
        composeTestRule.onNodeWithTag("home_total_driven_dial").assertExists()
        composeTestRule.onNodeWithText("TOTAL DRIVEN").assertExists()
        composeTestRule.onNodeWithText("Tracking is off").assertExists()
        composeTestRule.onNodeWithText("Resets Dec 31").assertExists()
        composeTestRule.onNodeWithText("Your trips will appear here").assertExists()
        // Driving mode layout is not active
        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertDoesNotExist()
    }

    @Test
    fun testMovingModeShowsLiveTrackingScreen() {
        viewModel.startManualTracking(app)
        TrackingManager.processGpsPoint(
            LatLngPoint(37.77, -122.42, timestamp = System.currentTimeMillis(), speedKmh = 25f)
        )

        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertExists()
        composeTestRule.onNodeWithTag("home_tracking_banner").assertExists()
        composeTestRule.onNodeWithTag("home_tracking_stats").assertExists()
        composeTestRule.onNodeWithText("Tracking is starting").assertExists()
        composeTestRule.onNodeWithText("Personal").assertExists()
        composeTestRule.onNodeWithText("Business").assertExists()
        composeTestRule.onNodeWithTag("home_tracking_stop").assertExists()

        composeTestRule.onNodeWithTag("home_total_driven_dial").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_profile_avatar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_active_live_trip_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_all_trips_classified_banner").assertDoesNotExist()
    }

    @Test
    fun testStoppingDriveReturnsToStandardDashboard() {
        viewModel.startManualTracking(app)
        TrackingManager.processGpsPoint(
            LatLngPoint(37.77, -122.42, timestamp = System.currentTimeMillis(), speedKmh = 25f)
        )

        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertExists()

        composeTestRule.onNodeWithTag("home_tracking_stop").performClick()
        composeTestRule.waitForIdle()

        // Standard dashboard is restored
        composeTestRule.onNodeWithTag("home_screen").assertExists()
        composeTestRule.onNodeWithTag("home_screen_driving_mode").assertDoesNotExist()
    }
}
