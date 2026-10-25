package com.example

import com.example.data.GasReceiptEntity
import com.example.data.InputCheck
import com.example.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InputCheckTest {

    @Test
    fun emailsPasswordsAndLabelsUseTheWhitelist() {
        assertEquals("driver@example.com", InputCheck.email("  driver@example.com "))
        assertNull(InputCheck.email("invalid-email"))
        assertNull(InputCheck.email("a@b.c"))
        assertNull(InputCheck.passwordProblem("password123"))
        assertEquals("Password must be at least 6 characters.", InputCheck.passwordProblem("123"))
        assertEquals("Password must be at most 72 characters.", InputCheck.passwordProblem("a".repeat(73)))
        assertEquals("Main St, Springfield", InputCheck.exactLabel("Main St, Springfield", 120))
        assertNull(InputCheck.exactLabel("Main <St>", 120))
        assertEquals("Main St", InputCheck.finishLabel("Main <St>", 120))
        assertEquals("John Doe", InputCheck.exactName("John Doe"))
        assertNull(InputCheck.exactName("John3"))
    }

    @Test
    fun numbersDatesAndRemoteFieldsStayInRange() {
        assertEquals(14.8, InputCheck.tripKm(14.8)!!, 0.001)
        assertNull(InputCheck.tripKm(0.0))
        assertNull(InputCheck.tripKm(-1.0))
        assertNull(InputCheck.tripKm(10_001.0))
        assertEquals("2026-09-05", InputCheck.receiptDate("2026-09-05"))
        assertNull(InputCheck.receiptDate("2026-13-01"))
        assertNull(InputCheck.receiptDate("yesterday"))
        assertEquals(
            "https://gszhecjcksyxlptlmwok.supabase.co",
            InputCheck.projectUrl("https://gszhecjcksyxlptlmwok.supabase.co/")
        )
        assertNull(InputCheck.projectUrl("http://gszhecjcksyxlptlmwok.supabase.co"))
        assertEquals("", InputCheck.anonKey("  "))
        assertNull(InputCheck.anonKey("test_anon_key_123"))
        assertEquals("AA:BB:CC:DD:EE:FF", InputCheck.bluetoothAddress("aa:bb:cc:dd:ee:ff"))
        assertEquals("[]", InputCheck.pathJson("""[{"lat":999,"lng":0}]"""))
    }

    @Test
    fun receiptFilesStayInsideTheReceiptsDirectory() {
        val root = File(System.getProperty("java.io.tmpdir"), "mileage-input-check")
        val receipts = File(root, "receipts").apply { mkdirs() }
        val ok = File(receipts, "receipt_123.jpg")
        ok.writeText("x")
        assertEquals(ok.canonicalFile, InputCheck.receiptFile(root, ok.absolutePath)?.canonicalFile)
        assertNull(InputCheck.receiptFile(root, File(receipts, "../receipt_123.jpg").path))
        assertEquals("", InputCheck.safeReceiptPath("/tmp/receipts/../receipt_1.jpg"))

        val trip = InputCheck.storedTrip(
            TripEntity(
                startDate = 1L,
                endDate = 2L,
                distanceKm = 12.0,
                type = "Business",
                purpose = "Client <review>"
            ),
            strict = true
        )
        assertNull(trip)
        val scrubbed = InputCheck.storedTrip(
            TripEntity(
                startDate = 1L,
                endDate = 2L,
                distanceKm = 12.0,
                type = "Other",
                purpose = "Client <review>"
            ),
            strict = false
        )
        assertEquals("Client review", scrubbed?.purpose)
        assertNull(scrubbed?.type)

        val receipt = InputCheck.storedReceipt(
            GasReceiptEntity(
                imagePath = "/tmp/receipts/../../secret.jpg",
                merchant = "Esso",
                totalAmount = 72.27,
                receiptDate = "yesterday"
            ),
            strict = true
        )
        assertNull(receipt)
    }

    @Test
    fun receiptImagesAreTypedRenamedAndClean() {
        assertTrue(InputCheck.acceptReceiptImage(photo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))))
        assertTrue(InputCheck.acceptReceiptImage(photo(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))))
        assertFalse(InputCheck.acceptReceiptImage(photo("%PDF-1.7 ".toByteArray())))
        assertFalse(InputCheck.acceptReceiptImage(photo(byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0x00))))
        val script = photo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        "<?php".toByteArray().copyInto(script, 16)
        assertFalse(InputCheck.acceptReceiptImage(script))
        val eicar = photo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        "EICAR-STANDARD-ANTIVIRUS-TEST-FILE".toByteArray().copyInto(eicar, 16)
        assertFalse(InputCheck.acceptReceiptImage(eicar))
        assertFalse(InputCheck.acceptReceiptImage(ByteArray(0)))
    }

    private fun photo(sig: ByteArray): ByteArray {
        val out = ByteArray(64)
        sig.copyInto(out)
        return out
    }
}
