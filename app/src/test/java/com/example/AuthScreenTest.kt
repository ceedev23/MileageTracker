package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MileageViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.AuthStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: MileageViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MileageViewModel(app)
        viewModel.supabaseConfig.setAnonKey("")
    }

    @Test
    fun testWelcomeScreenRendersHelloAndButtons() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.WELCOME
            )
        }

        composeTestRule.onNodeWithText("HELLO").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_sign_up_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_guest_button").assertIsDisplayed()
    }

    @Test
    fun testNavigatingToSignInFromWelcome() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.WELCOME
            )
        }

        // Tap Sign In button on Welcome screen
        composeTestRule.onNodeWithTag("welcome_sign_in_button").performClick()

        // Form elements for Sign In should be displayed
        composeTestRule.onNodeWithTag("auth_email_input").assertExists()
        composeTestRule.onNodeWithTag("auth_password_input").assertExists()
        composeTestRule.onNodeWithTag("auth_sign_in_button").assertExists()
    }

    @Test
    fun testNavigatingToSignUpFromWelcome() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.WELCOME
            )
        }

        // Tap Sign Up button on Welcome screen
        composeTestRule.onNodeWithTag("welcome_sign_up_button").performClick()

        // Form elements for Sign Up should be displayed
        composeTestRule.onNodeWithTag("auth_name_input").assertExists()
        composeTestRule.onNodeWithTag("auth_email_input").assertExists()
        composeTestRule.onNodeWithTag("auth_password_input").assertExists()
        composeTestRule.onNodeWithTag("auth_confirm_password_input").assertExists()
        composeTestRule.onNodeWithTag("auth_sign_up_button").assertExists()
    }

    @Test
    fun testSuccessfulSignInUpdatesViewModel() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.SIGN_IN
            )
        }

        composeTestRule.onNodeWithTag("auth_email_input")
            .performScrollTo()
            .performTextInput("testuser@example.com")

        composeTestRule.onNodeWithTag("auth_password_input")
            .performScrollTo()
            .performTextInput("password123")

        composeTestRule.onNodeWithTag("auth_sign_in_button")
            .performScrollTo()
            .performClick()

        val profile = viewModel.userProfile.value
        assertNotNull(profile)
        assertEquals("testuser@example.com", profile?.email)
        assertFalse(profile!!.isGuest)
        assertFalse(viewModel.isAuthPromptVisible.value)
    }

    @Test
    fun testGuestModeContinuesWithoutCredentials() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.WELCOME
            )
        }

        composeTestRule.onNodeWithTag("auth_guest_button").performClick()

        val profile = viewModel.userProfile.value
        assertNotNull(profile)
        assertTrue(profile!!.isGuest)
        assertFalse(viewModel.isAuthPromptVisible.value)
    }

    @Test
    fun testAppLogoDisplayedOnSignInAndSignUp() {
        // Sign In Step
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.SIGN_IN
            )
        }

        composeTestRule.onNodeWithTag("auth_screen_app_logo").assertExists()
        composeTestRule.onNodeWithTag("auth_top_app_logo").assertExists()
    }

    @Test
    fun captureWelcomeScreenshot() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.WELCOME
            )
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/welcome.png")
    }

    @Test
    fun captureSignInScreenshot() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.SIGN_IN
            )
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/signin.png")
    }

    @Test
    fun captureSignUpScreenshot() {
        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onDismiss = {},
                canDismiss = false,
                initialStep = AuthStep.SIGN_UP
            )
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/signup.png")
    }
}

