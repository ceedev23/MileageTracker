package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.service.ReceiptTextParser
import com.example.ui.MileageViewModel
import com.example.ui.components.ReceiptScanDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h480dp", application = Application::class)
class ReceiptScanDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `save stays visible as parsed receipt fields scroll on a short screen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MileageViewModel(app)
        val bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888)
        val parsed = ReceiptTextParser.parse(
            """
            ESSO CIRCLE K
            DATE: 2026-10-04
            REGULAR 87
            PUMP 8 PREPAID 30.321
            TOTAL CAD ${'$'}45.45
            MASTERCARD
            """.trimIndent()
        )
        compose.setContent {
            MaterialTheme {
                ReceiptScanDialog(viewModel, onDismiss = viewModel::dismissReceiptScanner)
            }
        }
        compose.runOnIdle { viewModel.processCapturedReceipt(app, bitmap, prefilledData = parsed) }
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.receiptScanState.value.extractedData == parsed }

        val save = compose.onNodeWithTag("save_receipt_button")
        save.assertIsDisplayed().assertIsEnabled()
        val saveBounds = save.getUnclippedBoundsInRoot()
        for ((tag, value) in mapOf(
            "receipt_merchant_input" to "Esso Circle K",
            "receipt_total_amount_input" to "45.45",
            "receipt_gallons_input" to "30.321",
            "receipt_price_per_gal_input" to "1.499",
            "receipt_fuel_grade_input" to "Regular 87",
            "receipt_date_input" to "2026-10-04",
            "receipt_payment_input" to "Mastercard"
        )) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertTextContains(value)
            save.assertIsDisplayed()
            assertEquals(saveBounds, save.getUnclippedBoundsInRoot())
        }
    }
}
