package com.coffeeledger.app.domain.money

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is stored as a [Long] count of minor units (paise) everywhere in the app.
 * Nothing financial is ever held in a floating point type once it has been parsed.
 */
object Money {

    const val MINOR_PER_MAJOR: Long = 100L

    /** Parses "1,20,000.50", "1200", "1 200.5" into minor units, or null if unusable. */
    fun parseAmount(raw: String): Long? {
        val cleaned = raw.trim().replace(",", "").replace(" ", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!cleaned.matches(Regex("""\d+(\.\d+)?"""))) return null
        val dot = cleaned.indexOf('.')
        return if (dot < 0) {
            cleaned.toLongOrNull()?.times(MINOR_PER_MAJOR)
        } else {
            val major = cleaned.substring(0, dot).ifEmpty { "0" }.toLongOrNull() ?: return null
            val fractionText = cleaned.substring(dot + 1).take(3).padEnd(3, '0')
            val thousandths = fractionText.toLongOrNull() ?: return null
            major * MINOR_PER_MAJOR + (thousandths / 10.0).roundToLong()
        }
    }

    /**
     * Formats minor units using the Indian digit grouping (last three digits, then pairs):
     * 12000000 -> "₹1,20,000". Implemented directly rather than through [java.text.NumberFormat]
     * so the output never shifts with the device locale.
     */
    fun format(minor: Long, withDecimals: Boolean = false, symbol: String = "₹"): String {
        val negative = minor < 0
        val absolute = abs(minor)
        val major = absolute / MINOR_PER_MAJOR
        val paise = absolute % MINOR_PER_MAJOR
        val grouped = groupIndian(major.toString())
        val body = if (withDecimals || paise != 0L) {
            "$grouped.${paise.toString().padStart(2, '0')}"
        } else {
            grouped
        }
        return if (negative) "-$symbol$body" else "$symbol$body"
    }

    /** Compact form for dense rows and chart labels: ₹1.2L, ₹78.6K, ₹2.4Cr. */
    fun formatCompact(minor: Long, symbol: String = "₹"): String {
        val negative = minor < 0
        val major = abs(minor) / MINOR_PER_MAJOR
        val body = when {
            major >= 10_000_000L -> trim1(major / 10_000_000.0) + "Cr"
            major >= 100_000L -> trim1(major / 100_000.0) + "L"
            major >= 1_000L -> trim1(major / 1_000.0) + "K"
            else -> major.toString()
        }
        return if (negative) "-$symbol$body" else "$symbol$body"
    }

    private fun trim1(value: Double): String {
        val rounded = (value * 10).roundToLong() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString()
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.substring(0, digits.length - 3)
        val tail = digits.substring(digits.length - 3)
        val pairs = StringBuilder()
        var index = head.length
        while (index > 0) {
            val start = maxOf(0, index - 2)
            if (pairs.isNotEmpty()) pairs.insert(0, ",")
            pairs.insert(0, head.substring(start, index))
            index = start
        }
        return "$pairs,$tail"
    }
}
