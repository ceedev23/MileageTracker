package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Whitelist for every value a person can type or that sync can write back.
 * Blank optional text becomes null. A failed check becomes null so callers skip the write.
 */
object InputCheck {
    private const val MARKS = " .,'’-/&#()@:+%!*"
    private val localPart = Regex("^[A-Za-z0-9._%+\\-]{1,64}$")
    private val domain = Regex("^(?:[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}$")
    private val person = Regex("^[\\p{L}][\\p{L} .'’\\-]{0,79}$")
    private val day = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val project = Regex("^https://[a-z0-9]{20}\\.supabase\\.co$")
    private val jwt = Regex("^[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}$")
    private val publishable = Regex("^sb_publishable_[A-Za-z0-9_\\-]{20,200}$")
    private val mac = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
    private val receiptName = Regex("^(receipt_\\d+|sample_receipt_\\d+)\\.jpg$")
    private val captureName = Regex("^capture_\\d+\\.jpg$")

    fun email(raw: String): String? {
        val clean = raw.trim()
        if (clean.length > 254 || clean.count { it == '@' } != 1) return null
        val local = clean.substringBefore('@')
        val host = clean.substringAfter('@')
        if (local.startsWith('.') || local.endsWith('.') || ".." in local) return null
        if (!localPart.matches(local) || !domain.matches(host)) return null
        return clean
    }

    fun keepEmail(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it in "@._%+-" }.take(254)

    fun passwordProblem(raw: String?): String? = when {
        raw.isNullOrBlank() || raw.length < 6 -> "Password must be at least 6 characters."
        raw.length > 72 || raw.any { it.isISOControl() } -> "Password must be at most 72 characters."
        else -> null
    }

    fun keepPassword(raw: String): String =
        raw.filter { !it.isISOControl() }.take(72)

    fun exactName(raw: String): String? {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (!person.matches(trimmed)) return null
        return trimmed
    }

    fun keepName(raw: String): String =
        raw.filter { it.isLetter() || it in " .'’-" }.take(80)

    fun keepTyped(raw: String, max: Int): String {
        val out = StringBuilder()
        for (ch in raw) {
            if (out.length >= max) break
            if (ch.isLetterOrDigit() || ch in MARKS) out.append(ch)
        }
        return out.toString()
    }

    fun exactLabel(raw: String, max: Int): String? {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty() || trimmed.length > max) return null
        if (trimmed.any { !it.isLetterOrDigit() && it !in MARKS }) return null
        return trimmed
    }

    fun finishLabel(raw: String, max: Int): String? =
        exactLabel(keepTyped(raw, max), max)

    fun decimalText(raw: String, intDigits: Int, fracDigits: Int): Boolean =
        Regex("^\\d{0,$intDigits}(\\.\\d{0,$fracDigits})?$").matches(raw)

    fun intText(raw: String, digits: Int): Boolean =
        Regex("^\\d{0,$digits}$").matches(raw)

    fun dateText(raw: String): Boolean =
        Regex("^[0-9\\-]{0,10}$").matches(raw)

    fun tripKm(km: Double): Double? =
        if (km.isFinite() && km > 0.0 && km <= 10_000.0) km else null

    fun receiptTotal(amount: Double): Double? =
        if (amount.isFinite() && amount in 0.01..9_999.99) amount else null

    fun fuelVolume(amount: Double): Double? =
        if (amount.isFinite() && amount in 0.2..250.0) amount else null

    fun fuelPrice(amount: Double): Double? =
        if (amount.isFinite() && amount in 0.40..15.0) amount else null

    fun rate(amount: Double): Double? =
        if (amount.isFinite() && amount in 0.01..10.0) amount else null

    fun annualGoal(amount: Double): Double? =
        if (amount.isFinite() && amount in 100.0..500_000.0) amount else null

    fun odometerKm(km: Double): Double? =
        if (km.isFinite() && km > 0.0 && km <= 2_000_000.0) km else null

    fun distanceUnit(unit: String): String? =
        if (unit == "km" || unit == "mi") unit else null

    fun odometerSource(source: String): String? =
        if (source == "manual" || source == "bluetooth") source else null

    fun receiptDate(raw: String): String? {
        val clean = raw.trim()
        if (!day.matches(clean)) return null
        val year = clean.substring(0, 4).toIntOrNull() ?: return null
        if (year !in 1950..2100) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.isLenient = false
            fmt.parse(clean)
            clean
        } catch (_: ParseException) {
            null
        }
    }

    fun projectUrl(raw: String): String? {
        val clean = raw.trim().removeSuffix("/")
        return if (project.matches(clean)) clean else null
    }

    /** Blank clears the key. Anything else must be a JWT or a publishable key. */
    fun anonKey(raw: String): String? {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.length > 2_000) return null
        return if (jwt.matches(clean) || publishable.matches(clean)) clean else null
    }

    fun bluetoothAddress(raw: String): String? {
        val clean = raw.trim().uppercase(Locale.US)
        return if (mac.matches(clean)) clean else null
    }

    fun receiptError(
        merchant: String,
        total: String,
        volume: String,
        price: String,
        fuel: String,
        date: String,
        payment: String
    ): String? {
        if (merchant.isNotBlank() && exactLabel(merchant, 80) == null) {
            return "Station name can use letters, numbers, and simple punctuation."
        }
        val amount = total.toDoubleOrNull()
        if (amount == null || receiptTotal(amount) == null) return "Enter a total from 0.01 to 9999.99."
        if (volume.isNotBlank()) {
            val parsed = volume.toDoubleOrNull()
            if (parsed == null || fuelVolume(parsed) == null) return "Enter a fuel amount from 0.2 to 250."
        }
        if (price.isNotBlank()) {
            val parsed = price.toDoubleOrNull()
            if (parsed == null || fuelPrice(parsed) == null) return "Enter a price from 0.40 to 15.00."
        }
        if (fuel.isNotBlank() && exactLabel(fuel, 40) == null) {
            return "Fuel grade can use letters, numbers, and simple punctuation."
        }
        if (date.isNotBlank() && receiptDate(date) == null) return "Enter the date as YYYY-MM-DD."
        if (payment.isNotBlank() && exactLabel(payment, 40) == null) {
            return "Payment can use letters, numbers, and simple punctuation."
        }
        return null
    }

    fun storedTrip(trip: TripEntity, strict: Boolean): TripEntity? {
        val km = tripKm(trip.distanceKm) ?: return null
        if (strict && !trip.type.isNullOrEmpty() && trip.type != "Business" && trip.type != "Personal") return null
        val type = if (trip.type == "Business" || trip.type == "Personal") trip.type else null
        val start = text(trip.startAddress, 120, strict)
        val end = text(trip.endAddress, 120, strict)
        val purpose = text(trip.purpose, 160, strict)
        val vehicle = text(trip.vehicle, 40, strict)
        if (start.invalid || end.invalid || purpose.invalid || vehicle.invalid) return null
        return trip.copy(
            distanceKm = km,
            type = type,
            startAddress = start.value,
            endAddress = end.value,
            purpose = purpose.value,
            vehicle = vehicle.value,
            pathJson = pathJson(trip.pathJson)
        )
    }

    fun storedReceipt(receipt: GasReceiptEntity, strict: Boolean): GasReceiptEntity? {
        val merchant = if (receipt.merchant.isNullOrBlank()) {
            "Gas Station"
        } else {
            val parsed = text(receipt.merchant, 80, strict)
            if (parsed.invalid) return null
            parsed.value ?: "Gas Station"
        }
        val total = receiptTotal(receipt.totalAmount) ?: return null
        val volume = optionalNumber(receipt.gallonsOrLiters, strict, ::fuelVolume) ?: return null
        val price = optionalNumber(receipt.pricePerUnit, strict, ::fuelPrice) ?: return null
        val fuel = text(receipt.fuelType, 40, strict)
        val payment = text(receipt.paymentMethod, 40, strict)
        if (fuel.invalid || payment.invalid) return null
        val date = if (receipt.receiptDate.isNullOrBlank()) {
            null
        } else {
            receiptDate(receipt.receiptDate) ?: if (strict) return null else null
        }
        val unit = when (receipt.unit.lowercase(Locale.US)) {
            "gal" -> "gal"
            "l", "" -> "L"
            else -> if (strict) return null else "L"
        }
        val note = receipt.rawExtraction?.let { scrubNote(it, 2_000) }?.ifBlank { null }
        return receipt.copy(
            merchant = merchant,
            totalAmount = total,
            currency = "$",
            fuelType = fuel.value,
            gallonsOrLiters = volume.value,
            unit = unit,
            pricePerUnit = price.value,
            receiptDate = date,
            paymentMethod = payment.value,
            rawExtraction = note,
            imagePath = safeReceiptPath(receipt.imagePath),
            tripId = receipt.tripId?.takeIf { it > 0L }
        )
    }

    fun pathJson(raw: String): String {
        if (raw.isBlank() || raw == "[]") return "[]"
        return try {
            val array = JSONArray(raw)
            if (array.length() > 5_000) return "[]"
            val clean = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: return "[]"
                val lat = obj.optDouble("lat", Double.NaN)
                val lng = obj.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || lat !in -90.0..90.0) return "[]"
                if (!lng.isFinite() || lng !in -180.0..180.0) return "[]"
                val speed = obj.optDouble("s", 0.0)
                if (!speed.isFinite() || speed !in 0.0..400.0) return "[]"
                clean.put(JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("t", obj.optLong("t", 0L).coerceAtLeast(0L))
                    put("s", speed)
                })
            }
            clean.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun safeReceiptPath(path: String): String {
        if (path.isBlank() || path.contains("..")) return ""
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        return if (receiptName.matches(name)) path else ""
    }

    fun receiptFile(filesDir: File, path: String): File? {
        if (safeReceiptPath(path).isEmpty()) return null
        return try {
            val root = File(filesDir, "receipts").canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile?.canonicalFile != root) null else file
        } catch (_: IOException) {
            null
        }
    }

    fun captureFile(cacheDir: File, segment: String): File? {
        if (!captureName.matches(segment)) return null
        return try {
            val root = File(cacheDir, "receipt_captures").canonicalFile
            val file = File(root, segment).canonicalFile
            if (file.parentFile?.canonicalFile != root) null else file
        } catch (_: IOException) {
            null
        }
    }

    /** Bytes only. The picker MIME type is ignored. */
    fun acceptReceiptImage(bytes: ByteArray): Boolean {
        if (bytes.size !in 24..MAX_RECEIPT_BYTES) return false
        if (!isReceiptImage(bytes)) return false
        return !malwareHit(bytes)
    }

    private fun isReceiptImage(bytes: ByteArray): Boolean {
        if (bytes.startsWith(JPEG) || bytes.startsWith(PNG) || bytes.startsWith(GIF87) || bytes.startsWith(GIF89)) {
            return true
        }
        if (bytes.size >= 12 && bytes.startsWith(RIFF) && bytes.copyOfRange(8, 12).contentEquals(WEBP)) return true
        if (bytes.size < 12 || !bytes.copyOfRange(4, 8).contentEquals(FTYP)) return false
        val brand = String(bytes, 8, 4, Charsets.ISO_8859_1)
        return brand == "heic" || brand == "heix" || brand == "mif1" || brand == "msf1" || brand == "avif"
    }

    // shortcut: signature list only, upgrade when a server keeps the original file
    private fun malwareHit(bytes: ByteArray): Boolean = MALWARE.any { needle -> contains(bytes, needle) }

    private fun contains(hay: ByteArray, needle: ByteArray): Boolean {
        if (hay.size < needle.size) return false
        val last = hay.size - needle.size
        for (i in 0..last) {
            var same = true
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) {
                    same = false
                    break
                }
            }
            if (same) return true
        }
        return false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun scrubNote(raw: String, max: Int): String =
        keepTyped(raw.replace('\n', ' ').replace('\t', ' '), max).trim().replace(Regex("\\s+"), " ")

    private fun text(value: String?, max: Int, strict: Boolean): Field {
        if (value.isNullOrBlank()) return Field(null, false)
        if (!strict) return Field(finishLabel(value, max), false)
        val exact = exactLabel(value, max)
        return if (exact == null) Field(null, true) else Field(exact, false)
    }

    private fun optionalNumber(
        value: Double?,
        strict: Boolean,
        accept: (Double) -> Double?
    ): NumberField? {
        if (value == null) return NumberField(null)
        val clean = accept(value)
        if (clean != null) return NumberField(clean)
        return if (strict) null else NumberField(null)
    }

    private data class Field(val value: String?, val invalid: Boolean)
    private data class NumberField(val value: Double?)

    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val GIF87 = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val GIF89 = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)
    private val FTYP = "ftyp".toByteArray(Charsets.US_ASCII)
    private val MALWARE = listOf(
        "EICAR-STANDARD-ANTIVIRUS-TEST-FILE",
        "<?php",
        "<?PHP",
        "<script",
        "<SCRIPT",
        "javascript:",
        "#!/bin/"
    ).map { it.toByteArray(Charsets.US_ASCII) }

    const val MAX_RECEIPT_BYTES = 8 * 1024 * 1024
}
