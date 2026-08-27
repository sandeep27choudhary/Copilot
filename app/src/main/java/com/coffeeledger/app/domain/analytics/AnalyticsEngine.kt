package com.coffeeledger.app.domain.analytics

import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerPeriod
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.model.Txn
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/** Income, spending and transfers for one window, kept strictly separate. */
data class PeriodSummary(
    val range: TimeRange,
    val incomeMinor: Long,
    val spendMinor: Long,
    val transferInMinor: Long,
    val transferOutMinor: Long,
    val transactionCount: Int,
) {
    /** Spending is net of transfers by construction, so this is real cash flow. */
    val netMinor: Long get() = incomeMinor - spendMinor
    val transferMinor: Long get() = transferOutMinor + transferInMinor
    val savingsRate: Float
        get() = if (incomeMinor <= 0L) 0f else (netMinor.toDouble() / incomeMinor).toFloat()
}

data class CategoryTotal(val category: Category, val amountMinor: Long, val count: Int)

data class MerchantTotal(val merchant: String, val amountMinor: Long, val count: Int)

data class MonthPoint(val yearMonth: YearMonth, val label: String, val spendMinor: Long, val incomeMinor: Long)

/** A payment that repeats on a roughly fixed cadence for a roughly fixed amount. */
data class RecurringPayment(
    val merchant: String,
    val category: Category,
    val typicalAmountMinor: Long,
    val occurrences: Int,
    val averageIntervalDays: Int,
    val lastSeenAt: Long,
) {
    val isSubscriptionLike: Boolean get() = averageIntervalDays in 25..35 || averageIntervalDays in 6..8
    val monthlyEquivalentMinor: Long
        get() = if (averageIntervalDays <= 0) typicalAmountMinor
        else (typicalAmountMinor * 30.0 / averageIntervalDays).roundToLong()
}

/** A transaction that is far outside the usual size for its category. */
data class UnusualTransaction(val txn: Txn, val typicalMinor: Long, val timesTypical: Float)

/**
 * All the number crunching, as pure functions over a list of transactions.
 *
 * Nothing here touches the network, the database or Android. That is what makes the
 * "processed locally" promise on the privacy screen checkable rather than a slogan.
 */
object AnalyticsEngine {

    fun inRange(txns: List<Txn>, range: TimeRange): List<Txn> =
        txns.filter { it.occurredAt in range }

    fun summarize(txns: List<Txn>, range: TimeRange): PeriodSummary {
        val window = inRange(txns, range)
        var income = 0L
        var spend = 0L
        var transferIn = 0L
        var transferOut = 0L
        for (txn in window) {
            when {
                txn.isTransfer && txn.direction == Direction.CREDIT -> transferIn += txn.amountMinor
                txn.isTransfer -> transferOut += txn.amountMinor
                txn.direction == Direction.CREDIT -> income += txn.amountMinor
                else -> spend += txn.amountMinor
            }
        }
        return PeriodSummary(range, income, spend, transferIn, transferOut, window.size)
    }

    /** Running balance across every account, from opening balances plus the ledger. */
    fun totalBalance(txns: List<Txn>, openingBalanceMinor: Long = 0L): Long =
        openingBalanceMinor + txns.sumOf { it.signedMinor }

    fun categoryTotals(
        txns: List<Txn>,
        range: TimeRange,
        direction: Direction = Direction.DEBIT,
    ): List<CategoryTotal> = inRange(txns, range)
        .filter { it.direction == direction && !it.isTransfer }
        .groupBy { it.category }
        .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amountMinor }, list.size) }
        .sortedByDescending { it.amountMinor }

    fun merchantTotals(
        txns: List<Txn>,
        range: TimeRange,
        direction: Direction = Direction.DEBIT,
    ): List<MerchantTotal> = inRange(txns, range)
        .filter { it.direction == direction && !it.isTransfer }
        .groupBy { it.merchant }
        .map { (merchant, list) -> MerchantTotal(merchant, list.sumOf { it.amountMinor }, list.size) }
        .sortedByDescending { it.amountMinor }

    fun monthlyTrend(
        txns: List<Txn>,
        months: Int,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthPoint> {
        val current = TimeRanges.yearMonthOf(now, zone)
        return (months - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val summary = summarize(txns, TimeRanges.month(ym, zone))
            MonthPoint(ym, TimeRanges.shortMonthLabel(ym), summary.spendMinor, summary.incomeMinor)
        }
    }

    /**
     * Finds merchants paid at least three times with a stable amount and a stable gap.
     * Three is the smallest count where "repeating" means anything.
     */
    fun recurringPayments(
        txns: List<Txn>,
        minOccurrences: Int = 3,
        amountTolerance: Float = 0.15f,
    ): List<RecurringPayment> {
        val dayMillis = 86_400_000.0
        return txns
            .filter { it.direction == Direction.DEBIT && !it.isTransfer }
            .groupBy { it.merchant }
            .mapNotNull { (merchant, list) ->
                if (list.size < minOccurrences) return@mapNotNull null
                val sorted = list.sortedBy { it.occurredAt }
                val median = medianOf(sorted.map { it.amountMinor })
                if (median <= 0L) return@mapNotNull null
                val stable = sorted.filter {
                    abs(it.amountMinor - median).toDouble() / median <= amountTolerance
                }
                if (stable.size < minOccurrences) return@mapNotNull null

                val gaps = stable.zipWithNext { a, b -> (b.occurredAt - a.occurredAt) / dayMillis }
                if (gaps.isEmpty()) return@mapNotNull null
                val averageGap = gaps.average()
                // Anything longer than a quarter is not a commitment worth flagging.
                if (averageGap < 3.0 || averageGap > 95.0) return@mapNotNull null

                RecurringPayment(
                    merchant = merchant,
                    category = stable.groupingBy { it.category }.eachCount()
                        .maxByOrNull { it.value }?.key ?: Category.OTHER_EXPENSE,
                    typicalAmountMinor = median,
                    occurrences = stable.size,
                    averageIntervalDays = averageGap.roundToLong().toInt(),
                    lastSeenAt = stable.last().occurredAt,
                )
            }
            .sortedByDescending { it.monthlyEquivalentMinor }
    }

    /**
     * Flags transactions far above the usual size for their category. Uses the median
     * rather than the mean so one big spend does not hide the next one.
     */
    fun unusualTransactions(
        txns: List<Txn>,
        range: TimeRange,
        history: List<Txn> = txns,
        multiple: Float = 3f,
        minimumMinor: Long = 100_000L,
    ): List<UnusualTransaction> {
        val baseline = history
            .filter { it.direction == Direction.DEBIT && !it.isTransfer }
            .groupBy { it.category }
            .mapValues { (_, list) -> medianOf(list.map { it.amountMinor }) }

        return inRange(txns, range)
            .filter { it.direction == Direction.DEBIT && !it.isTransfer && it.amountMinor >= minimumMinor }
            .mapNotNull { txn ->
                val typical = baseline[txn.category] ?: return@mapNotNull null
                if (typical <= 0L) return@mapNotNull null
                val times = txn.amountMinor.toFloat() / typical
                if (times < multiple) return@mapNotNull null
                UnusualTransaction(txn, typical, times)
            }
            .sortedByDescending { it.timesTypical }
    }

    /** Current value of a tracker, derived from the ledger (or held manually for goals). */
    fun progressOf(
        tracker: Tracker,
        txns: List<Txn>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TrackerProgress {
        val range = when (tracker.period) {
            TrackerPeriod.MONTHLY -> TimeRanges.currentMonth(now, zone)
            TrackerPeriod.ALL_TIME -> TimeRanges.allTime()
        }
        val direction = if (tracker.kind == TrackerKind.SPENDING_LIMIT) Direction.DEBIT else Direction.CREDIT
        val matched = inRange(txns, range).filter { txn ->
            // A spending limit ignores transfers; a savings target is mostly made of them.
            if (txn.isTransfer && tracker.kind == TrackerKind.SPENDING_LIMIT) return@filter false
            if (txn.direction != direction) return@filter false
            matchesFilters(tracker, txn)
        }
        val derived = matched.sumOf { it.amountMinor }
        return TrackerProgress(tracker, derived + tracker.manualProgressMinor)
    }

    fun progressOf(
        trackers: List<Tracker>,
        txns: List<Txn>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TrackerProgress> = trackers
        .filterNot { it.archived }
        .sortedBy { it.sortOrder }
        .map { progressOf(it, txns, now, zone) }

    /**
     * For a spending limit an empty filter set means "everything you spend", which is what
     * a monthly cap is. For a savings target or a goal it means the opposite: nothing is
     * derived from the ledger, and the progress is whatever the user recorded by hand.
     */
    private fun matchesFilters(tracker: Tracker, txn: Txn): Boolean {
        val noFilters = tracker.categoryIds.isEmpty() &&
            tracker.merchantNames.isEmpty() &&
            tracker.accountIds.isEmpty()
        if (noFilters) return tracker.kind == TrackerKind.SPENDING_LIMIT
        if (tracker.categoryIds.contains(txn.category.id)) return true
        if (txn.accountId != null && tracker.accountIds.contains(txn.accountId)) return true
        return tracker.merchantNames.any { it.equals(txn.merchant, ignoreCase = true) }
    }

    internal fun medianOf(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
