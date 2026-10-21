package com.example.service

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Turns OCR text from a fuel receipt into merchant, total, volume, price, date, and payment.
 * Pure text parsing so it can be tested without a camera or ML Kit.
 */
object ReceiptTextParser {

    private val brands = listOf(
        "PETRO-CANADA", "PETRO CANADA", "CANADIAN TIRE", "SAM'S CLUB", "SAMS CLUB",
        "CIRCLE K", "7-ELEVEN", "7 ELEVEN", "PHILLIPS 66", "QUIKTRIP", "LOVE'S", "LOVES",
        "CASEY'S", "CASEYS", "EXXONMOBIL", "COSTCO GAS", "EXXON", "MOBIL", "CHEVRON",
        "SHELL", "COSTCO", "SPEEDWAY", "SUNOCO", "VALERO", "MARATHON", "CITGO", "TEXACO",
        "SINCLAIR", "PILOT", "GETGO", "RACETRAC", "MURPHY", "SHEETZ", "KROGER", "WALMART",
        "ESSO", "ULTRAMAR", "HUSKY", "IRVING", "WAWA", "ARCO", "BP", "QT"
    ).sortedByDescending { it.length }

    private val moneyRegex = Regex("""(?<![A-Z0-9])\$?\s*(\d{1,4}[.,]\d{2})(?!\d)""")

    private val volumeThenUnit = Regex(
        """(?i)(\d{1,3}[.,]\d{2,3})\s*(gallons?|gal|litres?|liters?)\b"""
    )
    private val volumeThenLitersAbbrev = Regex("""(?i)(\d{1,3}[.,]\d{3})\s*L\b""")
    private val unitThenVolume = Regex(
        """(?i)\b(gallons?|litres?|liters?|volume)\b\s*(?:pumped)?\s*[:\-]?\s*(\d{1,3}[.,]\d{2,3})"""
    )
    private val volumeBeforeAt = Regex("""(?i)(\d{1,3}[.,]\d{3})\s*(?:gal(?:lons?)?|litres?|liters?)?\s*@""")

    private val pricePatterns = listOf(
        Regex("""(?i)(?:price|ppu|ppg|unit\s*price)\s*(?:/|per)?\s*(?:gal(?:lon)?s?|litres?|liters?|l)?\s*[:\-]?\s*\$?\s*(\d{1,2}[.,]\d{2,3})"""),
        Regex("""(?i)\$?\s*(\d{1,2}[.,]\d{2,3})\s*/\s*(?:gal(?:lon)?|l|litre|liter)\b"""),
        Regex("""(?i)@\s*\$?\s*(\d{1,2}[.,]\d{2,3})"""),
        Regex("""(?i)\$/\s*(?:gal|l)\s*[:\-]?\s*\$?\s*(\d{1,2}[.,]\d{2,3})""")
    )

    private val fuelRegex = Regex(
        """(?i)\b(unleaded\s+regular|premium\s+unleaded|regular\s+unleaded|unleaded\s+plus|mid[-\s]?grade|unleaded|premium|supreme|diesel|e-?\s?85|ethanol|regular|super|plus|prem(?:ium)?|reg(?:ular)?)\b(?:\s*[#-]?\s*(8[7-9]|9[0-3]))?"""
    )

    private val isoDate = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
    private val mdyDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})\b""")
    private val mdyShortDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2})\b""")

    private val specificPayments = listOf(
        "AMERICAN EXPRESS" to "American Express",
        "MASTERCARD" to "Mastercard",
        "MASTER CARD" to "Mastercard",
        "VISA" to "Visa",
        "DISCOVER" to "Discover",
        "AMEX" to "Amex",
        "INTERAC" to "Interac",
        "DEBIT" to "Debit",
        "CASH" to "Cash"
    )
    private val paymentExtras = listOf(
        "APPLE PAY" to "Apple Pay",
        "GOOGLE PAY" to "Google Pay",
        "CONTACTLESS" to "Contactless"
    )

    fun parse(rawText: String): ExtractedReceiptData {
        val original = rawText.trim()
        if (original.isEmpty()) return ExtractedReceiptData(rawText = null)

        val normalized = normalizeOcrDigits(original)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val searchTexts = buildSearchTexts(lines)

        val volume = findVolume(searchTexts)
        var gallons = volume?.value
        val unit = volume?.unit ?: "gal"
        var total = findTotal(lines)
        var price = findPrice(searchTexts, gallons, total)

        if (gallons != null && (gallons < 0.2 || gallons > 250.0)) gallons = null
        if (price != null && (price < 0.4 || price > 15.0)) price = null
        if (total != null && (total < 0.5 || total > 2000.0)) total = null

        if (total == null && gallons != null && price != null) {
            total = roundTo(gallons * price, 2)
        }
        if (price == null && total != null && gallons != null && gallons > 0.0) {
            val computed = total / gallons
            if (computed in 0.4..15.0) price = roundTo(computed, 3)
        }
        if (gallons == null && total != null && price != null && price > 0.0) {
            val computed = total / price
            if (computed in 0.2..250.0) gallons = roundTo(computed, 3)
        }

        return ExtractedReceiptData(
            merchant = findMerchant(lines),
            totalAmount = total ?: 0.0,
            currency = "$",
            fuelType = findFuel(lines),
            gallonsOrLiters = gallons,
            unit = unit,
            pricePerUnit = price,
            date = findDate(lines),
            paymentMethod = findPayment(normalized),
            summary = null,
            rawText = original.take(8000)
        )
    }

    private fun buildSearchTexts(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val texts = lines.toMutableList()
        for (i in 0 until lines.lastIndex) {
            texts.add(lines[i] + " " + lines[i + 1])
        }
        return texts
    }

    private fun normalizeOcrDigits(text: String): String {
        var value = text.replace('—', '-').replace('–', '-')
        value = Regex("""(?<![A-Za-z])[Ss]\s*(?=\d)""").replace(value) { "$" }
        value = Regex("""(?<=\d)[Oo](?=\d|[.,])|(?<=[.,])[Oo](?=\d)|(?<=\d)[Oo](?!\w)""")
            .replace(value, "0")
        value = Regex("""(?<=\d)[Il|](?=\d)|(?<=[.,])[Il|](?=\d)""")
            .replace(value, "1")
        return value
    }

    private fun findTotal(lines: List<String>): Double? {
        data class Candidate(val priority: Int, val index: Int, val amount: Double)
        val candidates = mutableListOf<Candidate>()
        lines.forEachIndexed { index, line ->
            val upper = line.uppercase(Locale.US)
            if (isVolumeOrPriceLine(upper) && !Regex("""\bTOTAL\b|AMOUNT\s+DUE|BALANCE|AMT\s+DUE""").containsMatchIn(upper)) {
                return@forEachIndexed
            }
            val amount = amountForLabel(lines, index) ?: return@forEachIndexed
            val isSubtotal = Regex("""\bSUB\s*TOTAL\b""").containsMatchIn(upper)
            val priority = when {
                Regex("""\bGRAND\b|\bAMOUNT\s+DUE\b|\bBALANCE\b|\bAMT\s+DUE\b""").containsMatchIn(upper) -> 3
                Regex("""\bTOTAL\b""").containsMatchIn(upper) && !isSubtotal -> 2
                isSubtotal -> 1
                else -> 0
            }
            if (priority > 0) candidates += Candidate(priority, index, amount)
        }
        return candidates.maxWithOrNull(compareBy<Candidate> { it.priority }.thenBy { it.index })?.amount
    }

    private fun amountForLabel(lines: List<String>, index: Int): Double? {
        val onLine = moneyAmounts(lines[index])
        if (onLine.isNotEmpty()) return onLine.last()
        val next = lines.getOrNull(index + 1) ?: return null
        if (!isAmountOnly(next)) return null
        return moneyAmounts(next).lastOrNull()
    }

    private fun isAmountOnly(line: String): Boolean {
        if (moneyAmounts(line).isEmpty()) return false
        val stripped = moneyRegex.replace(line, "")
            .replace(Regex("""[\s$:.\-]"""), "")
        return stripped.isEmpty()
    }

    private fun isVolumeOrPriceLine(upper: String): Boolean {
        return Regex("""\bPRICE\b|/GAL|PER\s+GAL|\bPPG\b|\bPPU\b|\bGALLONS?\b|\bLITRES?\b|\bLITERS?\b""")
            .containsMatchIn(upper)
    }

    private data class VolumeHit(val value: Double, val unit: String, val decimals: Int)

    private fun findVolume(texts: List<String>): VolumeHit? {
        val hits = mutableListOf<VolumeHit>()
        for (text in texts) {
            for (match in volumeThenUnit.findAll(text)) {
                val token = match.groupValues[1]
                val value = parseNumber(token) ?: continue
                hits += VolumeHit(value, unitOf(match.groupValues[2]), decimalPlaces(token))
            }
            for (match in volumeThenLitersAbbrev.findAll(text)) {
                val token = match.groupValues[1]
                val value = parseNumber(token) ?: continue
                hits += VolumeHit(value, "L", decimalPlaces(token))
            }
            val priceLine = Regex("""(?i)\bprice\b|/\s*gal|per\s+gal|\$\s*/""").containsMatchIn(text)
            if (!priceLine) {
                for (match in unitThenVolume.findAll(text)) {
                    val token = match.groupValues[2]
                    val value = parseNumber(token) ?: continue
                    hits += VolumeHit(value, unitOf(match.groupValues[1]), decimalPlaces(token))
                }
            }
            for (match in volumeBeforeAt.findAll(text)) {
                val token = match.groupValues[1]
                val value = parseNumber(token) ?: continue
                val unit = if (Regex("""(?i)litre|liter""").containsMatchIn(text)) "L" else "gal"
                hits += VolumeHit(value, unit, decimalPlaces(token))
            }
        }
        val valid = hits.filter { it.value in 0.2..250.0 }
        if (valid.isEmpty()) return null
        val bestDecimals = valid.maxOf { it.decimals }
        return valid.first { it.decimals == bestDecimals }
    }

    private fun findPrice(texts: List<String>, gallons: Double?, total: Double?): Double? {
        val found = mutableListOf<Double>()
        for (text in texts) {
            for (pattern in pricePatterns) {
                for (match in pattern.findAll(text)) {
                    val value = parseNumber(match.groupValues[1]) ?: continue
                    if (value in 0.4..15.0) found += value
                }
            }
        }
        val unique = found.distinct()
        if (unique.isEmpty()) return null
        if (unique.size == 1 || gallons == null || total == null || gallons <= 0.0) return unique.first()
        val expected = total / gallons
        return unique.minBy { abs(it - expected) }
    }

    private fun findFuel(lines: List<String>): String? {
        val ordered = lines.filter { Regex("""(?i)product|grade|fuel|pump|unlead|diesel|regular|premium""").containsMatchIn(it) } + lines
        for (line in ordered) {
            val match = fuelRegex.find(line) ?: continue
            val token = match.value.trim()
            val weak = Regex("""(?i)^(plus|super)$""").matches(token)
            val fuelContext = Regex("""(?i)pump|product|grade|unlead|fuel|diesel|regular|premium|8[7-9]|9[0-3]""")
                .containsMatchIn(line)
            if (weak && !fuelContext) continue
            return displayFuel(token)
        }
        return null
    }

    private fun displayFuel(token: String): String {
        var value = token.replace(Regex("""\s+"""), " ").trim()
        value = value.replace(Regex("""(?i)^reg\b"""), "Regular")
        value = value.replace(Regex("""(?i)^prem\b"""), "Premium")
        value = value.replace(Regex("""(?i)^e-?\s?85$"""), "E85")
        if (value.equals("E85", ignoreCase = true)) return "E85"
        return toTitleCase(value)
    }

    private fun findDate(lines: List<String>): String? {
        val ordered = lines.filter { it.contains("DATE", ignoreCase = true) } + lines
        for (line in ordered) {
            parseDate(line)?.let { return it }
        }
        return null
    }

    private fun parseDate(line: String): String? {
        isoDate.find(line)?.let { match ->
            return formatDate(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
        mdyDate.find(line)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            val month = if (first > 12 && second <= 12) second else first
            val day = if (first > 12 && second <= 12) first else second
            return formatDate(year, month, day)
        }
        mdyShortDate.find(line)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val yearTwo = match.groupValues[3].toInt()
            val year = if (yearTwo >= 50) 1900 + yearTwo else 2000 + yearTwo
            val month = if (first > 12 && second <= 12) second else first
            val day = if (first > 12 && second <= 12) first else second
            return formatDate(year, month, day)
        }
        return null
    }

    private fun formatDate(year: Int, month: Int, day: Int): String? {
        if (month !in 1..12 || day !in 1..31) return null
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    private fun findPayment(text: String): String? {
        val upper = text.uppercase(Locale.US)
        val parts = mutableListOf<String>()
        for ((needle, label) in specificPayments) {
            if (needle == "DEBIT") continue
            if (brandIn(upper, needle) && label !in parts) parts += label
        }
        for ((needle, label) in paymentExtras) {
            if (brandIn(upper, needle) && label !in parts) parts += label
        }
        if (brandIn(upper, "DEBIT") && "Debit" !in parts) parts += "Debit"
        if (parts.isEmpty()) return null
        val last4 = Regex("""(?:\*{2,}|X{2,}|ENDING(?:\s+IN)?\s+)(\d{4})\b""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.get(1)
        val name = parts.distinct().joinToString(" ")
        return if (last4 != null) "$name *$last4" else name
    }

    private fun findMerchant(lines: List<String>): String? {
        val candidates = lines.map { it.trim() }.filter { it.length >= 3 }
        val brandLine = candidates.firstOrNull { line ->
            !isBoilerplate(line) && !isAddressLine(line) && brands.any { brandIn(line, it) }
        }
        if (brandLine != null) return cleanMerchant(brandLine)
        val fallback = candidates.firstOrNull { line ->
            !isBoilerplate(line) &&
                !isAddressLine(line) &&
                line.count { it.isLetter() } >= 4 &&
                !line.contains('$') &&
                parseDate(line) == null &&
                fuelRegex.replace(line, "").count { it.isLetter() } >= 4 &&
                !Regex("""(?i)\b(total|subtotal|gallons?|price|pump|product|visa|mastercard|debit|cash|auth|transaction|payment|card|date|approved|invoice)\b""")
                    .containsMatchIn(line)
        }
        return fallback?.let { cleanMerchant(it) }
    }

    private fun cleanMerchant(line: String): String {
        val stripped = line.replace(Regex("""[*#]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        return toTitleCase(stripped).take(48).trim()
    }

    private fun isBoilerplate(line: String): Boolean {
        val cleaned = line.uppercase(Locale.US)
            .replace(Regex("""[^A-Z\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.isEmpty()) return true
        val phrases = setOf(
            "FUEL RECEIPT", "RECEIPT", "CUSTOMER COPY", "MERCHANT COPY", "STORE COPY",
            "WELCOME", "THANK YOU", "THANK YOU FOR YOUR PATRONAGE", "TAX INVOICE",
            "INVOICE", "APPROVED", "SALE", "COPY"
        )
        return cleaned in phrases || cleaned.startsWith("THANK YOU") || cleaned.startsWith("AUTH CODE") ||
            cleaned.startsWith("TRANSACTION")
    }

    private fun isAddressLine(line: String): Boolean {
        val upper = line.uppercase(Locale.US)
        return Regex("""\b(AVE|AVENUE|STREET|ST|RD|ROAD|BLVD|HWY|HIGHWAY|DRIVE|DR|LANE|LN|TEL|PHONE|WWW|HTTP)\b""")
            .containsMatchIn(upper) ||
            Regex("""\b\d{3}[-.)\s]\d{3}[-.\s]\d{4}\b""").containsMatchIn(line)
    }

    private fun brandIn(line: String, brand: String): Boolean {
        val pattern = Regex("(?<![A-Z0-9])${Regex.escape(brand)}(?![A-Z0-9])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(line)
    }

    private fun moneyAmounts(line: String): List<Double> {
        return moneyRegex.findAll(line).mapNotNull { parseNumber(it.groupValues[1]) }.toList()
    }

    private fun unitOf(token: String): String {
        return if (Regex("""(?i)^l$|litre|liter""").containsMatchIn(token)) "L" else "gal"
    }

    private fun decimalPlaces(token: String): Int {
        val separator = token.lastIndexOfAny(charArrayOf('.', ','))
        if (separator < 0) return 0
        return token.length - separator - 1
    }

    private fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().replace("$", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = when {
            cleaned.count { it == ',' } > 0 && cleaned.count { it == '.' } > 0 -> cleaned.replace(",", "")
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    private fun toTitleCase(value: String): String {
        return value.lowercase(Locale.US)
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.split('-').joinToString("-") { part -> titleWord(part) }
            }
    }

    private fun titleWord(word: String): String {
        if (word.isEmpty()) return word
        if (word.uppercase(Locale.US) in setOf("BP", "QT", "E85", "USA")) {
            return word.uppercase(Locale.US)
        }
        return word.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
    }

    private fun roundTo(value: Double, places: Int): Double {
        val factor = 10.0.pow(places)
        return round(value * factor) / factor
    }
}
