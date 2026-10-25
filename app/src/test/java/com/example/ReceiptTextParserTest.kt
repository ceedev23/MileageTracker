package com.example

import com.example.service.ReceiptTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTextParserTest {

  @Test
  fun `parses a standard pump receipt`() {
    val text = """
      *** FUEL RECEIPT ***
      CHEVRON SERVICE STATION
      1042 HIGHLAND AVE, SPRINGFIELD
      TEL: (555) 328-9901
      DATE/TIME: 2026-10-21  12:00:00
      TRANSACTION ID: #98421-B
      PRODUCT: REGULAR 87 (PUMP #06)
      GALLONS PUMPED: 13.420 GAL
      PRICE / GALLON: ${'$'}3.959
      SUBTOTAL: ${'$'}53.13
      TOTAL AMOUNT: ${'$'}53.13
      PAYMENT METHOD: VISA CONTACTLESS
      CARD: ************6021
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals("Chevron Service Station", parsed.merchant)
    assertEquals(53.13, parsed.totalAmount, 0.001)
    assertEquals(13.420, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals("gal", parsed.unit)
    assertEquals(3.959, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals("Regular 87", parsed.fuelType)
    assertEquals("2026-10-21", parsed.date)
    assertEquals("Visa Contactless *6021", parsed.paymentMethod)
  }

  @Test
  fun `parses shell layout with total on its own label`() {
    val text = """
      SHELL
      123 MAIN STREET
      10/21/2026 2:14 PM
      PUMP # 4
      UNLEADED REGULAR
      GALLONS    12.456
      ${'$'}/GAL      3.499
      FUEL SALE  ${'$'}43.58
      TOTAL      ${'$'}43.58
      VISA
      ************4412
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals("Shell", parsed.merchant)
    assertEquals(43.58, parsed.totalAmount, 0.001)
    assertEquals(12.456, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(3.499, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals("Unleaded Regular", parsed.fuelType)
    assertEquals("2026-10-21", parsed.date)
    assertEquals("Visa *4412", parsed.paymentMethod)
  }

  @Test
  fun `parses costco diesel with total on the next line`() {
    val text = """
      COSTCO GAS
      DIESEL
      14.802 GAL @ 4.259
      TOTAL
      ${'$'}63.04
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals("Costco Gas", parsed.merchant)
    assertEquals("Diesel", parsed.fuelType)
    assertEquals(14.802, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(4.259, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals(63.04, parsed.totalAmount, 0.001)
  }

  @Test
  fun `parses liters and keeps the labeled total`() {
    val text = """
      ESSO
      LITRES 45.200
      PRICE/L ${'$'}1.459
      TOTAL ${'$'}65.95
      REGULAR
      2026-09-01
      DEBIT
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals("Esso", parsed.merchant)
    assertEquals("L", parsed.unit)
    assertEquals(45.200, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(1.459, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals(65.95, parsed.totalAmount, 0.001)
    assertEquals("Regular", parsed.fuelType)
    assertEquals("2026-09-01", parsed.date)
    assertEquals("Debit", parsed.paymentMethod)
  }

  @Test
  fun `prefers grand total over subtotal and ignores the unit price`() {
    val text = """
      PRICE/GALLON ${'$'}3.999
      SUBTOTAL ${'$'}40.00
      TAX ${'$'}1.20
      TOTAL ${'$'}41.20
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals(41.20, parsed.totalAmount, 0.001)
    assertEquals(3.999, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals(10.303, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals("gal", parsed.unit)
  }

  @Test
  fun `repairs common ocr digit mistakes`() {
    val text = """
      TOTAL S53.13
      GALLONS PUMPED: 13.42O GAL
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals(53.13, parsed.totalAmount, 0.001)
    assertEquals(13.420, parsed.gallonsOrLiters ?: 0.0, 0.001)
  }

  @Test
  fun `does not invent details when the photo has no receipt text`() {
    val parsed = ReceiptTextParser.parse("*** FUEL RECEIPT ***\nTHANK YOU")

    assertNull(parsed.merchant)
    assertEquals(0.0, parsed.totalAmount, 0.001)
    assertNull(parsed.gallonsOrLiters)
    assertNull(parsed.pricePerUnit)
    assertNull(parsed.fuelType)
    assertNull(parsed.paymentMethod)
    assertEquals("L", parsed.unit)
  }

  @Test
  fun `reads a canadian pump receipt in litres`() {
    val text = """
      PETRO-CANADA
      88 KING ST W, TORONTO
      2026-10-21
      REGULAR
      LITRES PUMPED: 45.200 L
      PRICE / LITRE: ${'$'}1.599
      TOTAL AMOUNT: ${'$'}72.27
      INTERAC
    """.trimIndent()

    val parsed = ReceiptTextParser.parse(text)

    assertEquals("Petro-Canada", parsed.merchant)
    assertEquals("L", parsed.unit)
    assertEquals(45.200, parsed.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(1.599, parsed.pricePerUnit ?: 0.0, 0.001)
    assertEquals(72.27, parsed.totalAmount, 0.001)
    assertEquals("Regular", parsed.fuelType)
    assertEquals("2026-10-21", parsed.date)
    assertEquals("Interac", parsed.paymentMethod)
  }

  @Test
  fun `reads a short litre mark and an unlabeled volume as litres`() {
    val marked = ReceiptTextParser.parse(
      """
        ESSO
        52.340 L @ 1.549
        TOTAL ${'$'}81.07
      """.trimIndent()
    )
    assertEquals("L", marked.unit)
    assertEquals(52.340, marked.gallonsOrLiters ?: 0.0, 0.001)
    assertEquals(1.549, marked.pricePerUnit ?: 0.0, 0.001)

    val unlabeled = ReceiptTextParser.parse(
      """
        CANADIAN TIRE
        VOLUME 45.200
        TOTAL ${'$'}72.27
      """.trimIndent()
    )
    assertEquals("L", unlabeled.unit)
    assertEquals(45.200, unlabeled.gallonsOrLiters ?: 0.0, 0.001)
  }
}
