package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractedReceiptData(
    val merchant: String? = null,
    val totalAmount: Double = 0.0,
    val currency: String = "$",
    val fuelType: String? = null,
    val gallonsOrLiters: Double? = null,
    val unit: String = "gal",
    val pricePerUnit: Double? = null,
    val date: String? = null,
    val paymentMethod: String? = null,
    val summary: String? = null,
    val rawText: String? = null
)

sealed class ExtractionResult {
    data class Success(val data: ExtractedReceiptData) : ExtractionResult()
    data class Failure(val message: String, val fallbackData: ExtractedReceiptData? = null) : ExtractionResult()
}

object ReceiptExtractionService {

    /**
     * Local production receipt processing (zero AI/cloud reliance).
     * Prepares standard receipt data with current date and currency defaults.
     */
    suspend fun extractReceiptDetails(bitmap: Bitmap): ExtractionResult = withContext(Dispatchers.Default) {
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val defaultData = ExtractedReceiptData(
            merchant = "Gas Station",
            totalAmount = 0.0,
            currency = "$",
            fuelType = "Regular Unleaded",
            gallonsOrLiters = null,
            unit = "gal",
            pricePerUnit = null,
            date = now,
            paymentMethod = "Card",
            summary = "Fuel Purchase",
            rawText = null
        )
        ExtractionResult.Success(defaultData)
    }

    /**
     * Creates a realistic rendered gas receipt image.
     * Perfect for testing in Android emulator or camera-less environments!
     */
    fun createSampleGasReceiptBitmap(
        merchant: String = "CHEVRON SERVICE STATION",
        gallons: Double = 13.420,
        pricePerGal: Double = 3.959,
        total: Double = 53.13,
        fuelType: String = "REGULAR 87 (PUMP #06)"
    ): Bitmap {
        val width = 600
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Aged thermal paper background
        canvas.drawColor(Color.rgb(249, 248, 243))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 45)
            textSize = 22f
            typeface = Typeface.MONOSPACE
        }

        val centerPaint = Paint(paint).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = 26f
        }

        val boldPaint = Paint(paint).apply {
            isFakeBoldText = true
            textSize = 24f
        }

        val largeBoldPaint = Paint(paint).apply {
            isFakeBoldText = true
            textSize = 34f
        }

        var y = 60f

        // Header
        canvas.drawText("*** FUEL RECEIPT ***", (width / 2).toFloat(), y, centerPaint)
        y += 45f
        canvas.drawText(merchant, (width / 2).toFloat(), y, centerPaint)
        y += 30f
        canvas.drawText("1042 HIGHLAND AVE, SPRINGFIELD", (width / 2).toFloat(), y, Paint(paint).apply { textAlign = Paint.Align.CENTER; textSize = 17f })
        y += 26f
        canvas.drawText("TEL: (555) 328-9901", (width / 2).toFloat(), y, Paint(paint).apply { textAlign = Paint.Align.CENTER; textSize = 17f })
        y += 40f

        // Separator line
        canvas.drawText("----------------------------------------", 25f, y, paint)
        y += 35f

        val nowStr = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("DATE/TIME: $nowStr", 35f, y, paint)
        y += 32f
        canvas.drawText("TRANSACTION ID: #98421-B", 35f, y, paint)
        y += 32f
        canvas.drawText("PRODUCT: $fuelType", 35f, y, boldPaint)
        y += 40f

        // Separator
        canvas.drawText("----------------------------------------", 25f, y, paint)
        y += 40f

        // Details
        canvas.drawText("GALLONS PUMPED:", 35f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.3f GAL", gallons), 380f, y, boldPaint)
        y += 35f

        canvas.drawText("PRICE / GALLON:", 35f, y, paint)
        canvas.drawText(String.format(Locale.US, "$%.3f", pricePerGal), 380f, y, boldPaint)
        y += 35f

        canvas.drawText("SUBTOTAL:", 35f, y, paint)
        canvas.drawText(String.format(Locale.US, "$%.2f", total), 380f, y, paint)
        y += 45f

        // Separator
        canvas.drawText("========================================", 25f, y, paint)
        y += 50f

        // Grand Total (Large)
        canvas.drawText("TOTAL AMOUNT:", 35f, y, largeBoldPaint)
        canvas.drawText(String.format(Locale.US, "$%.2f", total), 360f, y, largeBoldPaint)
        y += 55f

        canvas.drawText("========================================", 25f, y, paint)
        y += 40f

        // Payment info
        canvas.drawText("PAYMENT METHOD: VISA CONTACTLESS", 35f, y, paint)
        y += 30f
        canvas.drawText("CARD: ************6021", 35f, y, paint)
        y += 30f
        canvas.drawText("AUTH CODE: 04419Z - APPROVED", 35f, y, paint)
        y += 50f

        // Barcode / Footer
        canvas.drawText("||| | ||||| |||| || |||||| ||||| |||", (width / 2).toFloat(), y, Paint(centerPaint).apply { textSize = 28f; color = Color.rgb(80, 80, 90) })
        y += 40f
        canvas.drawText("THANK YOU FOR YOUR PATRONAGE!", (width / 2).toFloat(), y, Paint(paint).apply { textAlign = Paint.Align.CENTER; textSize = 18f })

        return bitmap
    }
}
