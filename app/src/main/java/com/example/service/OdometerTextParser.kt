package com.example.service

/**
 * Finds a plausible dashboard odometer reading in ML Kit text.
 * OCR can confuse trip meters, time, speed or other dashboard numbers.
 * Only auto-fills when a single plausible candidate exists; a person must confirm.
 */
object OdometerTextParser {
    private val number = Regex("""(?<![\d.,])\d{1,3}(?:[ ,]\d{3})+(?:[.]\d)?(?![\d.,])|(?<![\d.,])\d{3,7}(?:[.]\d)?(?![\d.,])""")
    private val labeled = Regex("""(?i)\b(?:odo(?:meter)?|total\s*(?:km|mi|miles)?)\s*[:=]?\s*([\d ,.]+)""")

    fun candidate(text: String): String? {
        if (text.isBlank()) return null
        val labeledValues = labeled.findAll(text).mapNotNull { parse(it.groupValues[1]) }.distinct().toList()
        if (labeledValues.size == 1) return labeledValues.single()
        if (labeledValues.size > 1) return null
        return number.findAll(text)
            .mapNotNull { parse(it.value) }
            .distinct()
            .singleOrNull()
    }

    private fun parse(raw: String): String? {
        val cleaned = raw.trim().replace(" ", "").replace(",", "")
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 100 || value > 9_999_999) return null
        val digits = cleaned.substringBefore('.')
        if (digits.length !in 3..7) return null
        if (cleaned.contains('.') && cleaned.substringAfter('.').length != 1) return null
        return cleaned
    }
}
