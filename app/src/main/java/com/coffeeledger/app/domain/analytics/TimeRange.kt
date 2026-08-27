package com.coffeeledger.app.domain.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A half-open [start, endExclusive) window of epoch millis, with a label for the UI. */
data class TimeRange(
    val label: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    operator fun contains(millis: Long): Boolean = millis in startMillis until endMillis
}

/** Builds the handful of windows the whole app reasons about. */
object TimeRanges {

    fun month(yearMonth: YearMonth, zone: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange(monthLabel(yearMonth), start, end)
    }

    fun currentMonth(now: Long, zone: ZoneId = ZoneId.systemDefault()): TimeRange =
        month(yearMonthOf(now, zone), zone)

    fun previousMonth(now: Long, zone: ZoneId = ZoneId.systemDefault()): TimeRange =
        month(yearMonthOf(now, zone).minusMonths(1), zone)

    /** Monday-to-now, matching how people talk about "this week". */
    fun currentWeek(now: Long, zone: ZoneId = ZoneId.systemDefault()): TimeRange {
        val today = dateOf(now, zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange("This week", start, end)
    }

    fun lastDays(now: Long, days: Long, zone: ZoneId = ZoneId.systemDefault()): TimeRange {
        val today = dateOf(now, zone)
        val start = today.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange("Last $days days", start, end)
    }

    fun allTime(): TimeRange = TimeRange("All time", Long.MIN_VALUE, Long.MAX_VALUE)

    fun yearMonthOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(dateOf(millis, zone))

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun monthLabel(yearMonth: YearMonth): String =
        MONTH_NAMES[yearMonth.monthValue - 1] + " " + yearMonth.year

    fun shortMonthLabel(yearMonth: YearMonth): String = MONTH_NAMES[yearMonth.monthValue - 1]

    /**
     * How far through the current month we are, 0..1. Used to say whether spending is
     * ahead of pace rather than just comparing two raw totals.
     */
    fun monthElapsedFraction(now: Long, zone: ZoneId = ZoneId.systemDefault()): Float {
        val date = dateOf(now, zone)
        val daysInMonth = YearMonth.from(date).lengthOfMonth()
        return (date.dayOfMonth.toFloat() / daysInMonth).coerceIn(0f, 1f)
    }

    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
