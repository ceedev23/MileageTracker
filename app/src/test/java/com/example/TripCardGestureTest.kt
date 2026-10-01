package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.data.TripEntity
import com.example.ui.components.SwipeableTripCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripCardGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testClassifiedTripCannotBeSwipedToClassify() {
        val classifiedTrip = TripEntity(
            id = 42L,
            startDate = 1700000000000L,
            endDate = 1700002400000L,
            distanceKm = 15.0,
            type = "Business",
            startAddress = "100 Pine Street",
            endAddress = "200 Market Street",
            purpose = "Sales Meeting",
            vehicle = "Toyota Camry"
        )

        var classifiedType: String? = null
        var editedTrip: TripEntity? = null

        composeTestRule.setContent {
            SwipeableTripCard(
                trip = classifiedTrip,
                isMiles = false,
                reimbursementRate = 0.70,
                onClassify = { _, type -> classifiedType = type },
                onEdit = { trip -> editedTrip = trip },
                onDelete = {}
            )
        }

        // Attempt swiping left and right on already classified trip
        composeTestRule.onNodeWithTag("trip_card_42").performTouchInput {
            swipeLeft()
        }
        assertEquals("Swiping left should NOT classify already classified trip", null, classifiedType)

        composeTestRule.onNodeWithTag("trip_card_42").performTouchInput {
            swipeRight()
        }
        assertEquals("Swiping right should NOT classify already classified trip", null, classifiedType)
    }

    @Test
    fun testClassifiedTripCardExpandsOnTapWithCompleteDetails() {
        val classifiedTrip = TripEntity(
            id = 101L,
            startDate = 1700000000000L,
            endDate = 1700002400000L,
            distanceKm = 24.5,
            type = "Business",
            startAddress = "742 Evergreen Terrace",
            endAddress = "100 Industrial Way",
            purpose = "Annual Audit Review",
            vehicle = "Honda Civic"
        )

        var editedTrip: TripEntity? = null

        composeTestRule.setContent {
            SwipeableTripCard(
                trip = classifiedTrip,
                isMiles = false,
                reimbursementRate = 0.70,
                onClassify = { _, _ -> },
                onEdit = { trip -> editedTrip = trip },
                onDelete = {}
            )
        }

        // Before tap, detailed section items are not displayed
        composeTestRule.onNodeWithText("Full Date").assertDoesNotExist()
        composeTestRule.onNodeWithText("Honda Civic").assertDoesNotExist()

        // Tap the card to expand complete details
        composeTestRule.onNodeWithTag("trip_card_101").performClick()

        // Now complete details should be visible
        composeTestRule.onNodeWithText("START").assertIsDisplayed()
        composeTestRule.onNodeWithText("DESTINATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("742 Evergreen Terrace").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 Industrial Way").assertIsDisplayed()
        composeTestRule.onNodeWithText("DISTANCE").assertIsDisplayed()
        composeTestRule.onNodeWithText("DURATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("EST. VALUE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Honda Civic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Annual Audit Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("Full Date").assertIsDisplayed()

        // Edit Trip button is present in expanded details
        composeTestRule.onNodeWithTag("edit_trip_expanded_button").assertExists()
    }

    @Test
    fun testEditIconIsClickableToEditTrip() {
        val classifiedTrip = TripEntity(
            id = 202L,
            startDate = 1700000000000L,
            endDate = 1700001800000L,
            distanceKm = 10.0,
            type = "Personal",
            startAddress = "Home",
            endAddress = "Grocery Store"
        )

        var editedTrip: TripEntity? = null

        composeTestRule.setContent {
            SwipeableTripCard(
                trip = classifiedTrip,
                isMiles = false,
                onClassify = { _, _ -> },
                onEdit = { trip -> editedTrip = trip },
                onDelete = {}
            )
        }

        // Click the edit icon
        composeTestRule.onNodeWithContentDescription("Edit Trip").performClick()
        assertEquals(202L, editedTrip?.id)
    }

    @Test
    fun testUnclassifiedTripSwipesToClassify() {
        val unclassifiedTrip = TripEntity(
            id = 303L,
            startDate = 1700000000000L,
            endDate = 1700001800000L,
            distanceKm = 12.0,
            type = null,
            startAddress = "Airport",
            endAddress = "Downtown Hotel"
        )

        var classifiedType: String? = null

        composeTestRule.setContent {
            SwipeableTripCard(
                trip = unclassifiedTrip,
                isMiles = false,
                onClassify = { _, type -> classifiedType = type },
                onEdit = {},
                onDelete = {}
            )
        }

        // Swipe right to classify as Business
        composeTestRule.onNodeWithTag("trip_card_303").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertEquals("Business", classifiedType)
    }

    @Test
    fun testUnclassifiedTripCanAlsoExpandOnTap() {
        val unclassifiedTrip = TripEntity(
            id = 404L,
            startDate = 1700000000000L,
            endDate = 1700001800000L,
            distanceKm = 8.5,
            type = null,
            startAddress = "Office",
            endAddress = "Client Site",
            purpose = "Consulting Visit",
            vehicle = "Work Truck"
        )

        composeTestRule.setContent {
            SwipeableTripCard(
                trip = unclassifiedTrip,
                isMiles = false,
                onClassify = { _, _ -> },
                onEdit = {},
                onDelete = {}
            )
        }

        // Tap on card to expand
        composeTestRule.onNodeWithTag("trip_card_404").performClick()

        // Complete details should be displayed
        composeTestRule.onNodeWithText("START").assertIsDisplayed()
        composeTestRule.onNodeWithText("DESTINATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work Truck").assertIsDisplayed()
        composeTestRule.onNodeWithText("Consulting Visit").assertIsDisplayed()
    }
}
