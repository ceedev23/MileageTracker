package com.example

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.WorkProfile
import com.example.ui.components.WorkProfilePicker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkProfilePickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun usersCanSwitchFromCareToDeliveryAndBackToGeneral() {
        var selected by mutableStateOf(WorkProfile.GENERAL)
        compose.setContent { MaterialTheme { WorkProfilePicker(selected) { selected = it } } }
        compose.onNodeWithTag("work_profile_care").performClick()
        compose.runOnIdle { assertEquals(WorkProfile.CARE, selected) }
        compose.onNodeWithTag("work_profile_care").assertIsSelected()
        compose.onNodeWithTag("work_profile_delivery").performClick()
        compose.onNodeWithTag("work_profile_delivery").assertIsSelected()
        compose.onNodeWithTag("work_profile_general").performClick()
        compose.onNodeWithTag("work_profile_general").assertIsSelected()
    }
}
