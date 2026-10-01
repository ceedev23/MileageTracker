package com.example

import com.example.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MileageTrackerUnitTest {

  @Test
  fun `test distance conversion and reimbursement calculation`() {
    val distanceKm = 10.0
    val distanceMi = distanceKm * 0.621371
    assertEquals(6.21371, distanceMi, 0.001)

    // CRA rate $0.70 / km
    val rateCra = 0.70
    val deductionCra = distanceKm * rateCra
    assertEquals(7.00, deductionCra, 0.001)

    // IRS rate $0.67 / mi
    val rateIrs = 0.67
    val deductionIrs = distanceMi * rateIrs
    assertEquals(4.163, deductionIrs, 0.01)
  }

  @Test
  fun `test trip classification and entity attributes`() {
    val trip = TripEntity(
      startDate = 1700000000000L,
      endDate = 1700001200000L,
      distanceKm = 14.5,
      type = "Business",
      startAddress = "100 Main St",
      endAddress = "500 Market Blvd",
      purpose = "Client Presentation",
      vehicle = "Primary Vehicle"
    )

    assertEquals("Business", trip.type)
    assertEquals(14.5, trip.distanceKm, 0.001)
    assertEquals("100 Main St", trip.startAddress)
    assertEquals("500 Market Blvd", trip.endAddress)
    assertEquals("Client Presentation", trip.purpose)
  }

  @Test
  fun `test gas receipt entity attributes and total calculations`() {
    val receipt = com.example.data.GasReceiptEntity(
      imagePath = "/data/user/0/receipts/receipt_1.jpg",
      merchant = "Shell Service Plaza",
      totalAmount = 55.56,
      currency = "$",
      fuelType = "Regular 87",
      gallonsOrLiters = 14.250,
      unit = "gal",
      pricePerUnit = 3.899,
      receiptDate = "2026-09-05",
      paymentMethod = "Visa *4921"
    )

    assertEquals("Shell Service Plaza", receipt.merchant)
    assertEquals(55.56, receipt.totalAmount, 0.001)
    assertEquals(14.250, receipt.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(3.899, receipt.pricePerUnit ?: 0.0, 0.001)
    // Calculated product matches total approximately
    val calculatedTotal = (receipt.gallonsOrLiters ?: 0.0) * (receipt.pricePerUnit ?: 0.0)
    assertEquals(55.56, calculatedTotal, 0.05)
  }

  @Test
  fun `test extracted receipt data structure`() {
    val extracted = com.example.service.ExtractedReceiptData(
      merchant = "Chevron Express",
      totalAmount = 48.75,
      currency = "$",
      fuelType = "Regular Unleaded",
      gallonsOrLiters = 12.50,
      pricePerUnit = 3.90,
      date = "2026-09-06"
    )

    assertEquals("Chevron Express", extracted.merchant)
    assertEquals(48.75, extracted.totalAmount, 0.001)
    assertEquals(12.50, extracted.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(3.90, extracted.pricePerUnit ?: 0.0, 0.001)
  }
}
