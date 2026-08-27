package com.coffeeledger.app.domain.parse

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pulls the transaction date and time out of the message body, falling back to the time
 * the SMS arrived. Banks use at least half a dozen date shapes, so each is tried in turn.
 */
object SmsDateParser {

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    // 26-08-2026, 26/08/26, 26.08.2026
    private val NUMERIC_DATE = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""")

    // 26-Aug-26, 26 Aug 2026, 26Aug26
    private val TEXT_DATE = Regex(
        """\b(\d{1,2})[-\s]?([A-Za-z]{3,4})[-\s,]?(\d{2,4})\b""",
        RegexOption.IGNORE_CASE,
    )

    // Aug 26, 2026
    private val TEXT_DATE_LEADING = Regex(
        """\b([A-Za-z]{3,4})[-\s](\d{1,2})[-\s,]+(\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TIME = Regex(
        """\b(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(am|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(body: String, fallbackMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = findDate(body) ?: return fallbackMillis
        val time = findTime(body) ?: fallbackTime(fallbackMillis, zone)
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    internal fun findDate(body: String): LocalDate? {
        NUMERIC_DATE.find(body)?.let { match ->
            val (d, m, y) = match.destructured
            build(d.toInt(), m.toInt(), y.toInt(), y.length)?.let { return it }
        }
        TEXT_DATE.find(body)?.let { match ->
            val (d, monthText, y) = match.destructured
            val month = MONTHS[monthText.lowercase().take(4)] ?: MONTHS[monthText.lowercase().take(3)]
            if (month != null) build(d.toInt(), month, y.toInt(), y.length)?.let { return it }
        }
        TEXT_DATE_LEADING.find(body)?.let { match ->
            val (monthText, d, y) = match.destructured
            val month = MONTHS[monthText.lowercase().take(3)]
            if (month != null) build(d.toInt(), month, y.toInt(), y.length)?.let { return it }
        }
        return null
    }

    internal fun findTime(body: String): LocalTime? {
        val match = TIME.find(body) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val second = match.groupValues[3].toIntOrNull() ?: 0
        val meridiem = match.groupValues[4].lowercase()
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return LocalTime.of(hour, minute, second)
    }

    private fun build(day: Int, month: Int, year: Int, yearDigits: Int): LocalDate? {
        val fullYear = if (yearDigits <= 2) 2000 + year else year
        if (month !in 1..12 || day !in 1..31 || fullYear !in 2000..2100) return null
        return runCatching { LocalDate.of(fullYear, month, day) }.getOrNull()
    }

    private fun fallbackTime(millis: Long, zone: ZoneId): LocalTime =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
}
