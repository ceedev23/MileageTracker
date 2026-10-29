package com.example

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.service.ExtractedReceiptData
import com.example.service.ReceiptField
import com.example.ui.components.ReceiptFieldReviewHint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReceiptFieldReviewHintTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `calculated hint appears and clears after manual edit`() {
        val edited = mutableStateOf(false)
        val data = ExtractedReceiptData(totalAmount = 80.0, calculatedFields = setOf(ReceiptField.TOTAL))
        compose.setContent { MaterialTheme { ReceiptFieldReviewHint(data, ReceiptField.TOTAL, edited.value) } }
        compose.onNodeWithText("Calculated from other amounts. Check against the photo.").assertExists()
        compose.runOnIdle { edited.value = true }
        compose.onNodeWithText("Calculated from other amounts. Check against the photo.").assertDoesNotExist()
    }

    @Test fun `date review hint does not claim the date is certain`() {
        val data = ExtractedReceiptData(date = "2026-04-05", reviewFields = setOf(ReceiptField.DATE))
        compose.setContent { MaterialTheme { ReceiptFieldReviewHint(data, ReceiptField.DATE, false) } }
        compose.onNodeWithText("Ambiguous day/month order. Confirm the date.").assertExists()
    }
}
