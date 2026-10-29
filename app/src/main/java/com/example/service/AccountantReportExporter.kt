package com.example.service

import com.example.data.GasReceiptEntity
import com.example.data.InputCheck
import com.example.data.TripEntity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ReportPeriod(val label: String) {
    ALL("All Time"), THIS_MONTH("This Month"), THIS_YEAR("This Year")
}

/** A fixed selection shared by preview and export, including units, rate and timezone. */
data class ReportSnapshot(
    val trips: List<TripEntity>,
    val receipts: List<GasReceiptEntity>,
    val period: ReportPeriod,
    val isMiles: Boolean,
    val rate: Double,
    val createdAt: Long,
    val timeZoneId: String,
    val workProfile: com.example.data.WorkProfile = com.example.data.WorkProfile.GENERAL,
    val workPurpose: String? = null,
    val businessOnly: Boolean = false
) {
    companion object {
        fun create(
            trips: List<TripEntity>, receipts: List<GasReceiptEntity>, period: ReportPeriod,
            isMiles: Boolean, rate: Double, now: Long = System.currentTimeMillis(),
            zone: TimeZone = TimeZone.getDefault(),
            workProfile: com.example.data.WorkProfile = com.example.data.WorkProfile.GENERAL,
            workPurpose: String? = null,
            businessOnly: Boolean = false
        ): ReportSnapshot {
            val start = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (period == ReportPeriod.THIS_YEAR) set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val end = (start.clone() as Calendar).apply {
                add(if (period == ReportPeriod.THIS_YEAR) Calendar.YEAR else Calendar.MONTH, 1)
            }
            fun included(time: Long) = period == ReportPeriod.ALL || (time >= start.timeInMillis && time < end.timeInMillis)
            val selectedTrips = trips.filter { included(it.startDate) && (workPurpose == null || it.purpose == workPurpose) && (!businessOnly || it.type == "Business") }
                .sortedWith(compareBy<TripEntity> { it.startDate }.thenBy { it.id })
            val selectedIds = selectedTrips.map { it.id }.toSet()
            return ReportSnapshot(
                selectedTrips,
                receipts.filter { included(receiptTime(it, zone)) && ((workPurpose == null && !businessOnly) || it.tripId in selectedIds) }
                    .sortedWith(compareBy<GasReceiptEntity> { receiptTime(it, zone) }.thenBy { it.id }),
                period, isMiles, rate, now, zone.id, workProfile, workPurpose, businessOnly
            )
        }
    }
}

fun receiptTime(receipt: GasReceiptEntity, zone: TimeZone): Long {
    val date = receipt.receiptDate?.let { InputCheck.receiptDate(it) }
    if (date != null) return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = zone; isLenient = false
    }.parse(date)?.time ?: receipt.timestamp
    return receipt.timestamp
}

data class MissingReceiptPhoto(val receiptId: Long, val reason: String)
data class AccountantReportBundle(
    val file: File, val tripCount: Int, val receiptCount: Int, val photoCount: Int,
    val missingPhotos: List<MissingReceiptPhoto>, val periodLabel: String
)

/** Writes only the chosen records and app-private receipt photos; never exports cloud URLs or credentials. */
object AccountantReportExporter {
    private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
    private const val CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000

    private fun text(value: String?): String {
        val raw = value.orEmpty()
        // CSV quoting alone does not stop spreadsheet formulas in user-controlled text.
        val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            raw.firstOrNull() in listOf('\t', '\r', '\n')) "'$raw" else raw
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    private fun number(value: Double, decimals: Int = 2): String {
        require(value.isFinite()) { "Invalid report amount" }
        return String.format(Locale.US, "%.${decimals}f", value)
    }
    private fun date(time: Long, zone: String, pattern: String = "yyyy-MM-dd HH:mm:ss"): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(time))

    fun tripCsv(snapshot: ReportSnapshot): String = buildString {
        append("Trip ID,Date,Start Address,End Address,Distance (${if (snapshot.isMiles) "Miles" else "Kilometers"}),Type,${snapshot.workProfile.purposeLabel.replace(" / ", "-")},Vehicle,Rate/Unit,Rate-based Estimate\r\n")
        for (trip in snapshot.trips) {
            val distance = if (snapshot.isMiles) trip.distanceKm * 0.621371 else trip.distanceKm
            append(listOf(trip.id.toString(), text(date(trip.startDate, snapshot.timeZoneId)), text(trip.startAddress),
                text(trip.endAddress), number(distance), text(trip.type ?: "Unclassified"), text(trip.purpose),
                text(trip.vehicle), number(snapshot.rate, 4), number(if (trip.type == "Business") distance * snapshot.rate else 0.0)).joinToString(","))
            append("\r\n")
        }
    }

    fun expenseCsv(snapshot: ReportSnapshot, photoStatuses: Map<Long, String>? = null): String = buildString {
        append("Receipt ID,Receipt Date,Date Source,Saved At,Merchant,Total Amount,Currency as Recorded,Fuel Type,Volume,Volume Unit,Price/Unit,Payment Method,Linked Trip ID,Linked Trip in Export,Image File,Image Status\r\n")
        val tripIds = snapshot.trips.map { it.id }.toSet()
        for (receipt in snapshot.receipts) {
            val validDate = receipt.receiptDate?.let { InputCheck.receiptDate(it) }
            val status = photoStatuses?.get(receipt.id) ?: "Not checked until export"
            append(listOf(receipt.id.toString(), text(validDate ?: date(receipt.timestamp, snapshot.timeZoneId, "yyyy-MM-dd")),
                text(if (validDate == null) "Saved timestamp fallback" else "Receipt date"), text(date(receipt.timestamp, snapshot.timeZoneId)),
                text(receipt.merchant), number(receipt.totalAmount), text(receipt.currency), text(receipt.fuelType),
                receipt.gallonsOrLiters?.let { number(it, 3) }.orEmpty(), text(receipt.unit),
                receipt.pricePerUnit?.let { number(it, 3) }.orEmpty(), text(receipt.paymentMethod), receipt.tripId?.toString().orEmpty(),
                text(if (receipt.tripId == null) "Not linked" else if (receipt.tripId in tripIds) "Yes" else "No"),
                text(if (photoStatuses == null || status == "Included") imageName(receipt.id) else ""), text(status)).joinToString(","))
            append("\r\n")
        }
    }

    private fun imageName(id: Long) = "receipts/receipt_$id.jpg"

    fun create(snapshot: ReportSnapshot, filesDir: File, cacheDir: File): AccountantReportBundle {
        require(snapshot.trips.map { it.id }.distinct().size == snapshot.trips.size)
        require(snapshot.receipts.all { it.id > 0 } && snapshot.receipts.map { it.id }.distinct().size == snapshot.receipts.size)
        require(snapshot.rate.isFinite() && snapshot.rate >= 0)
        val directory = File(cacheDir, "reports").apply { check(exists() || mkdirs()) }
        // Prune only our old packages/staging directories, preserving recently shared files.
        directory.listFiles()?.filter { it.name.startsWith("Accountant_Report_") &&
            System.currentTimeMillis() - it.lastModified() > CACHE_LIFETIME_MS }?.forEach { it.deleteRecursively() }
        val token = UUID.randomUUID().toString()
        val staging = File(directory, "Accountant_Report_${token}_staging").apply { check(mkdir()) }
        val target = File(directory, "Accountant_Report_${date(snapshot.createdAt, "UTC", "yyyyMMdd_HHmmss")}_$token.zip")
        val temporary = File(directory, "Accountant_Report_$token.tmp")
        val photos = mutableMapOf<Long, File>()
        val missing = mutableListOf<MissingReceiptPhoto>()
        try {
            for (receipt in snapshot.receipts) {
                val input = InputCheck.receiptFile(filesDir, receipt.imagePath)
                val output = File(staging, "receipt_${receipt.id}.jpg")
                try {
                    if (input == null || !input.isFile) throw IOException("Photo not available on this device")
                    if (input.length() !in 4..MAX_PHOTO_BYTES) throw IOException("Photo is empty or exceeds 20 MB")
                    input.inputStream().buffered().use { source ->
                        val magic = ByteArray(3)
                        if (source.read(magic) != 3 || magic[0] != 0xff.toByte() || magic[1] != 0xd8.toByte() || magic[2] != 0xff.toByte()) {
                            throw IOException("Photo is not a JPEG")
                        }
                        output.outputStream().buffered().use { destination ->
                            destination.write(magic)
                            val buffer = ByteArray(8192)
                            var size = 3L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                size += count
                                if (size > MAX_PHOTO_BYTES) throw IOException("Photo exceeds 20 MB")
                                destination.write(buffer, 0, count)
                            }
                        }
                    }
                    photos[receipt.id] = output
                } catch (_: Exception) {
                    output.delete()
                    missing += MissingReceiptPhoto(receipt.id, "Photo missing, unreadable or unsupported on this device. Restore it or use the original phone.")
                }
            }
            val statuses = snapshot.receipts.associate { it.id to if (it.id in photos) "Included" else "Missing - see missing_photos.csv" }
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                entry("trips.csv", tripCsv(snapshot))
                entry("fuel_expenses.csv", expenseCsv(snapshot, statuses))
                entry("missing_photos.csv", "Receipt ID,Issue\r\n" + missing.joinToString("") { "${it.receiptId},${text(it.reason)}\r\n" })
                entry("README.txt", """
                    MileageTracker accountant export
                    Work profile: ${snapshot.workProfile.label}
                    Purpose filter: ${snapshot.workPurpose ?: "All purposes"}
                    Trip classification: ${if (snapshot.businessOnly) "Business only" else "All classifications"}
                    Period: ${snapshot.period.label}
                    Generated (UTC): ${date(snapshot.createdAt, "UTC")}
                    Record dates use timezone: ${snapshot.timeZoneId}
                    Trips: ${snapshot.trips.size}
                    Fuel expense records: ${snapshot.receipts.size}
                    Included receipt photos: ${photos.size}
                    MISSING receipt photos: ${missing.size}

                    trips.csv and fuel_expenses.csv contain the selected report records.
                    When a purpose or Business-only filter is selected, only selected trips and
                    fuel expenses linked to those selected trips are included. Unlinked fuel
                    expenses are excluded from a purpose-filtered report.
                    Each included image is linked by receipt ID in fuel_expenses.csv.
                    Check missing_photos.csv before relying on this package as complete evidence.
                    Photos missing from this device are not fetched automatically during export.
                    Restore them through optional photo backup, or export from the original phone.
                    A receipt date takes precedence over its saved timestamp for period selection.
                    A linked trip can be outside the selected period and is identified in the expense CSV.

                    Fuel expenses are amounts recorded, not calculated tax deductions. Currency
                    symbols are preserved as recorded; '$' does not identify CAD versus USD.
                    Rate-based estimates are for reference. Tax eligibility and treatment require
                    review. This package does not certify CRA, IRS or other tax compliance.
                    Formula-like text is prefixed with an apostrophe for safer spreadsheet opening.
                    Raw OCR text, cloud locations and account credentials are not exported.
                    Receipt images may contain personal and payment information: share carefully.
                """.trimIndent() + "\n")
                for ((id, photo) in photos) {
                    zip.putNextEntry(ZipEntry(imageName(id)))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(temporary.renameTo(target)) { "Could not finish export" }
            return AccountantReportBundle(target, snapshot.trips.size, snapshot.receipts.size, photos.size, missing.toList(), snapshot.period.label)
        } catch (error: Exception) {
            temporary.delete(); target.delete(); throw error
        } finally { staging.deleteRecursively() }
    }
}
