package com.example

import com.example.data.GasReceiptEntity
import com.example.data.TripEntity
import com.example.service.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile

class AccountantReportExporterTest {
    private lateinit var root: File
    private lateinit var files: File
    private lateinit var cache: File
    private val zone = TimeZone.getTimeZone("America/Toronto")
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 1, 2, 3)
    private fun time(value: String, tz: TimeZone = zone) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }.parse(value)!!.time
    private val now get() = time("2026-10-28 22:00")
    private fun trip(id: Long = 1, date: String = "2026-10-15 12:00", type: String? = "Business") =
        TripEntity(id, time(date), time(date) + 60000, 10.0, type, "Home", "Client", "Delivery", "Car")
    private fun receipt(id: Long = 1, receiptDate: String? = "2026-10-15", saved: String = "2026-10-28 12:00", path: String = "") =
        GasReceiptEntity(id = id, timestamp = time(saved), imagePath = path, merchant = "Shell", totalAmount = 80.50,
            gallonsOrLiters = 40.0, pricePerUnit = 2.0125, receiptDate = receiptDate, paymentMethod = "Visa *1234", tripId = 1)
    private fun photo(id: Long): File = File(File(files, "receipts").apply { mkdirs() }, "receipt_$id.jpg").apply { writeBytes(jpeg) }
    private fun snapshot(trips: List<TripEntity> = listOf(trip()), receipts: List<GasReceiptEntity> = emptyList(), period: ReportPeriod = ReportPeriod.ALL) =
        ReportSnapshot.create(trips, receipts, period, false, .73, now, zone)
    private fun csv(zip: ZipFile, name: String) = zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        root = Files.createTempDirectory("accountant-export-test").toFile()
        files = File(root, "files").apply { mkdirs() }
        cache = File(root, "cache").apply { mkdirs() }
    }
    @After fun cleanup() { root.deleteRecursively() }

    @Test fun `package links expense row to exact original photo bytes`() {
        val image = photo(7)
        val r = receipt(7, path = image.absolutePath)
        val s = snapshot(receipts = listOf(r))
        val bundle = AccountantReportExporter.create(s, files, cache)
        assertEquals(1, bundle.photoCount); assertTrue(bundle.missingPhotos.isEmpty())
        ZipFile(bundle.file).use { zip ->
            assertEquals(setOf("trips.csv", "fuel_expenses.csv", "missing_photos.csv", "README.txt", "receipts/receipt_7.jpg"), zip.entries().asSequence().map { it.name }.toSet())
            assertArrayEquals(jpeg, zip.getInputStream(zip.getEntry("receipts/receipt_7.jpg")).readBytes())
            assertEquals(AccountantReportExporter.tripCsv(s), csv(zip, "trips.csv"))
            val expenses = csv(zip, "fuel_expenses.csv")
            assertTrue(expenses.contains("80.50")); assertTrue(expenses.contains("Shell"))
            assertTrue(expenses.contains("receipts/receipt_7.jpg")); assertTrue(expenses.contains("Included"))
            assertFalse(expenses.contains(files.absolutePath))
        }
        assertArrayEquals(jpeg, image.readBytes())
    }
    @Test fun `missing photos remain explicit in expense row manifest and result`() {
        val bundle = AccountantReportExporter.create(snapshot(receipts = listOf(receipt(8))), files, cache)
        assertEquals(listOf(8L), bundle.missingPhotos.map { it.receiptId })
        assertEquals(0, bundle.photoCount)
        ZipFile(bundle.file).use { zip ->
            assertTrue(csv(zip, "fuel_expenses.csv").contains("Missing - see missing_photos.csv"))
            assertTrue(csv(zip, "missing_photos.csv").contains("8,"))
            assertTrue(csv(zip, "README.txt").contains("MISSING receipt photos: 1"))
            assertNull(zip.getEntry("receipts/receipt_8.jpg"))
        }
    }
    @Test fun `outside private receipt directory cannot leak into archive`() {
        val outside = File(root, "receipt_9.jpg").apply { writeBytes(jpeg) }
        val bundle = AccountantReportExporter.create(snapshot(receipts = listOf(receipt(9, path = outside.absolutePath))), files, cache)
        assertEquals(0, bundle.photoCount)
        assertEquals(1, bundle.missingPhotos.size)
        ZipFile(bundle.file).use { assertNull(it.getEntry("receipts/receipt_9.jpg")) }
    }
    @Test fun `unreadable image does not create a partial photo entry`() {
        val invalid = photo(3).apply { writeText("Not an image") }
        val bundle = AccountantReportExporter.create(snapshot(receipts = listOf(receipt(3, path = invalid.absolutePath))), files, cache)
        assertEquals(1, bundle.missingPhotos.size)
        ZipFile(bundle.file).use { assertNull(it.getEntry("receipts/receipt_3.jpg")) }
        assertFalse(File(cache, "reports").listFiles()!!.any { it.isDirectory || it.extension == "tmp" })
    }
    @Test fun `month selection uses receipt date over saved timestamp and excludes boundary records`() {
        val s = snapshot(
            trips = listOf(trip(1, "2026-09-30 23:59"), trip(2, "2026-10-01 00:00"), trip(3, "2026-11-01 00:00")),
            receipts = listOf(receipt(1, "2026-09-30"), receipt(2, "2026-10-01", "2026-11-02 12:00"), receipt(3, null), receipt(4, "2026-11-01")).map { it.copy(imagePath = photo(it.id).absolutePath) },
            period = ReportPeriod.THIS_MONTH
        )
        assertEquals(listOf(2L), s.trips.map { it.id })
        assertEquals(listOf(2L, 3L), s.receipts.map { it.id })
        val bundle = AccountantReportExporter.create(s, files, cache)
        assertEquals(1, bundle.tripCount); assertEquals(2, bundle.receiptCount)
        assertEquals(2, bundle.photoCount)
        ZipFile(bundle.file).use {
            assertEquals(AccountantReportExporter.tripCsv(s), csv(it, "trips.csv"))
            assertNull(it.getEntry("receipts/receipt_1.jpg")); assertNull(it.getEntry("receipts/receipt_4.jpg"))
            assertNotNull(it.getEntry("receipts/receipt_2.jpg")); assertNotNull(it.getEntry("receipts/receipt_3.jpg"))
        }
    }
    @Test fun `year selection excludes records from previous and next year`() {
        val s = snapshot(trips = listOf(trip(1, "2025-12-31 23:59"), trip(2, "2026-01-01 00:00"), trip(3, "2027-01-01 00:00")), period = ReportPeriod.THIS_YEAR)
        assertEquals(listOf(2L), s.trips.map { it.id })
    }
    @Test fun `export keeps fixed selection when source list changes`() {
        val source = mutableListOf(trip())
        val s = snapshot(trips = source)
        source.clear()
        assertEquals(1, s.trips.size)
        assertEquals(1, AccountantReportExporter.create(s, files, cache).tripCount)
    }
    @Test fun `spreadsheet formulas commas quotes and line breaks are safely encoded`() {
        val t = trip().copy(purpose = "  =HYPERLINK(\"evil\")", startAddress = "Main, Street\nSuite \"A\"")
        val r = receipt().copy(merchant = "@SUM(1,2)", rawExtraction = "SECRET RAW OCR")
        val s = snapshot(trips = listOf(t), receipts = listOf(r))
        val trips = AccountantReportExporter.tripCsv(s)
        assertTrue(trips.contains("\"'  =HYPERLINK(\"\"evil\"\")\""))
        assertTrue(trips.contains("\"Main, Street\nSuite \"\"A\"\"\""))
        val expenses = AccountantReportExporter.expenseCsv(s)
        assertTrue(expenses.contains("\"'@SUM(1,2)\""))
        assertFalse(expenses.contains("SECRET RAW OCR"))
    }
    @Test fun `missing image never removes expense and trip link outside selection is explicit`() {
        val s = snapshot(trips = emptyList(), receipts = listOf(receipt()))
        val bundle = AccountantReportExporter.create(s, files, cache)
        assertEquals(1, bundle.receiptCount)
        ZipFile(bundle.file).use { assertTrue(csv(it, "fuel_expenses.csv").contains("1,\"No\"")) }
    }
    @Test fun `empty selection still produces useful headers and completeness readme`() {
        val bundle = AccountantReportExporter.create(snapshot(emptyList()), files, cache)
        ZipFile(bundle.file).use {
            assertTrue(csv(it, "trips.csv").startsWith("Trip ID,"))
            assertTrue(csv(it, "fuel_expenses.csv").startsWith("Receipt ID,"))
            assertTrue(csv(it, "README.txt").contains("Fuel expense records: 0"))
        }
    }
    @Test fun `miles conversion and business estimate do not count personal trips`() {
        val s = ReportSnapshot.create(listOf(trip(1), trip(2, type = "Personal")), emptyList(), ReportPeriod.ALL, true, .70, now, zone)
        val csv = AccountantReportExporter.tripCsv(s)
        assertTrue(csv.contains("Distance (Miles)")); assertTrue(csv.contains(",6.21,"))
        assertTrue(csv.contains(",4.35\r\n")); assertTrue(csv.contains(",0.00\r\n"))
    }
    @Test fun `duplicate receipt IDs fail rather than overwrite images`() {
        try { AccountantReportExporter.create(snapshot(receipts = listOf(receipt(), receipt())), files, cache); fail("Should reject duplicate IDs") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun `CSV uses decimal dot regardless of device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertTrue(AccountantReportExporter.expenseCsv(snapshot(receipts = listOf(receipt()))).contains(",80.50,"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun `old packages expire without deleting original photos or recent exports`() {
        val directory = File(cache, "reports").apply { mkdirs() }
        val old = File(directory, "Accountant_Report_old.zip").apply { writeText("old"); setLastModified(1) }
        val recent = File(directory, "Accountant_Report_recent.zip").apply { writeText("recent") }
        val original = photo(1)
        AccountantReportExporter.create(snapshot(), files, cache)
        assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(original.exists())
    }
}
