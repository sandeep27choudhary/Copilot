package com.coffeeledger.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Date and time strings, in one place so every screen reads the same. */
object Formats {

    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM")
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DAY.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun shortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DAY_SHORT.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${date(millis, zone)} · ${time(millis, zone)}"

    /** "Today" and "Yesterday" earn their place; anything older gets a real date. */
    fun dayHeading(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> if (day.year == today.year) DAY_SHORT.format(day) else DAY.format(day)
        }
    }

    fun dayKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
