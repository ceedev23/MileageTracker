package com.example.service

/**
 * SAE J1979 PID 01 A6: odometer, 4 bytes, 0.1 km per bit.
 * Many cars do not support this PID.
 */
object Odometer {
    fun parseElmKm(raw: String): Double? {
        val compact = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val marker = "41A6"
        val start = compact.indexOf(marker)
        if (start < 0) return null
        val hex = compact.substring(start + marker.length).take(8)
        if (hex.length < 8) return null
        val counts = hex.toLongOrNull(16) ?: return null
        if (counts == 0L) return null
        return counts / 10.0
    }

    /** Distance to log between two readings. Null when there is no earlier reading or the dash did not move. */
    fun tripKm(previousKm: Double?, nextKm: Double): Double? {
        val previous = previousKm ?: return null
        val delta = nextKm - previous
        return if (delta > 0.05) delta else null
    }
}
