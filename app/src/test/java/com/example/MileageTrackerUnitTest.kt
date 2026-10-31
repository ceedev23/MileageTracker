package com.example

import com.example.data.LatLngPoint
import com.example.data.TripEntity
import com.example.service.DetectorState
import com.example.service.TripDetector
import com.example.service.movingAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
      merchant = "Esso",
      totalAmount = 81.07,
      currency = "$",
      fuelType = "Regular",
      gallonsOrLiters = 52.340,
      unit = "L",
      pricePerUnit = 1.549,
      receiptDate = "2026-09-05",
      paymentMethod = "Visa *4921"
    )

    assertEquals("Esso", receipt.merchant)
    assertEquals(81.07, receipt.totalAmount, 0.001)
    assertEquals(52.340, receipt.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals("L", receipt.unit)
    assertEquals(1.549, receipt.pricePerUnit ?: 0.0, 0.001)
    val calculatedTotal = (receipt.gallonsOrLiters ?: 0.0) * (receipt.pricePerUnit ?: 0.0)
    assertEquals(81.07, calculatedTotal, 0.05)
  }

  @Test
  fun `short gps steps while driving start a trip`() {
    var started = false
    val detector = TripDetector(
      dwellMinutes = 5,
      onTripStarted = { started = true },
      onTripUpdated = {},
      onTripEnded = {},
      onTripDiscarded = {}
    )
    val t0 = 1_700_000_000_000L
    val step = 12.0 / 111_195.0
    repeat(6) { i ->
      detector.processPoint(LatLngPoint(37.77 + i * step, -122.42, t0 + i * 1_500L, 0f))
    }
    assertTrue(started)
    assertEquals(DetectorState.RECORDING, detector.state)
  }

  @Test
  fun `tracking is on only while moving and turns off after a real stop`() {
    assertTrue(movingAfter(false, DetectorState.IDLE, 12f, 0L))
    assertFalse(movingAfter(false, DetectorState.RECORDING, 0f, 0L))
    assertTrue(movingAfter(true, DetectorState.RECORDING, 0f, 5_000L))
    assertFalse(movingAfter(true, DetectorState.RECORDING, 0f, TripDetector.WARMUP_ABORT_MS))
    assertTrue(movingAfter(true, DetectorState.MOVING, 0f, 2_000L))
    assertFalse(movingAfter(true, DetectorState.IDLE, 0f, 0L))
  }

  @Test
  fun `a brief slow fix does not cancel a drive that is starting`() {
    var started = false
    val detector = TripDetector(
      dwellMinutes = 5,
      onTripStarted = { started = true },
      onTripUpdated = {},
      onTripEnded = {},
      onTripDiscarded = {}
    )
    val t0 = 1_700_000_000_000L
    detector.processPoint(LatLngPoint(37.77, -122.42, t0, 12f))
    detector.processPoint(LatLngPoint(37.77005, -122.42, t0 + 2_000L, 0f))
    assertEquals(DetectorState.MOVING, detector.state)
    detector.processPoint(LatLngPoint(37.772, -122.42, t0 + 26_000L, 12f))
    assertTrue(started)
    assertEquals(DetectorState.RECORDING, detector.state)
  }

  @Test
  fun `test extracted receipt data structure`() {
    val extracted = com.example.service.ExtractedReceiptData(
      merchant = "Petro-Canada",
      totalAmount = 72.27,
      currency = "$",
      fuelType = "Regular",
      gallonsOrLiters = 45.200,
      pricePerUnit = 1.599,
      date = "2026-09-06"
    )

    assertEquals("Petro-Canada", extracted.merchant)
    assertEquals(72.27, extracted.totalAmount, 0.001)
    assertEquals(45.200, extracted.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(1.599, extracted.pricePerUnit ?: 0.0, 0.001)
    assertEquals("L", extracted.unit)
  }
}
