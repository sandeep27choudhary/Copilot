package com.coffeeledger.app.domain.analytics

import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightTone { NEUTRAL, CAUTION, POSITIVE }

enum class InsightKind {
    TRACKER_NEAR_LIMIT,
    TRACKER_OVER,
    CATEGORY_INCREASE,
    CATEGORY_DECREASE,
    FREQUENT_MERCHANT,
    RECURRING_COMMITMENT,
    UNUSUAL_TRANSACTION,
    SAVINGS_OPPORTUNITY,
    CASH_FLOW,
    PACE,
}

/**
 * One observation about the user's own money. Every insight is derived on-device from
 * stored transactions; [evidence] states which numbers produced it.
 */
data class Insight(
    val id: String,
    val kind: InsightKind,
    val tone: InsightTone,
    val title: String,
    val evidence: String,
    val priority: Int,
)

/**
 * Turns the raw analytics into a short, ranked list of things worth saying.
 *
 * The generator is deliberately terse: an insight the user scrolls past is worse than no
 * insight at all, so only a handful survive.
 */
object InsightGenerator {

    fun generate(
        txns: List<Txn>,
        trackerProgress: List<TrackerProgress>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 6,
    ): List<Insight> {
        val insights = mutableListOf<Insight>()
        val thisMonth = TimeRanges.currentMonth(now, zone)
        val lastMonth = TimeRanges.previousMonth(now, zone)
        val current = AnalyticsEngine.summarize(txns, thisMonth)
        val previous = AnalyticsEngine.summarize(txns, lastMonth)

        insights += trackerInsights(trackerProgress)
        insights += categoryShiftInsights(txns, thisMonth, lastMonth)
        insights += frequentMerchantInsights(txns, thisMonth)
        insights += recurringInsights(txns)
        insights += unusualInsights(txns, thisMonth)
        insights += paceInsight(current, trackerProgress, now, zone)
        insights += cashFlowInsight(current, previous)
        insights += savingsInsight(current)

        return insights.sortedByDescending { it.priority }.take(limit)
    }

    private fun trackerInsights(progress: List<TrackerProgress>): List<Insight> =
        progress.mapNotNull { item ->
            val tracker = item.tracker
            when {
                tracker.kind == TrackerKind.SPENDING_LIMIT && item.isOver -> Insight(
                    id = "tracker-over-${tracker.id}",
                    kind = InsightKind.TRACKER_OVER,
                    tone = InsightTone.CAUTION,
                    title = "${tracker.title} is over by ${Money.format(item.overBy)}.",
                    evidence = "${Money.format(item.currentMinor)} spent against a ${Money.format(item.targetMinor)} limit.",
                    priority = 100,
                )
                item.isNearLimit -> Insight(
                    id = "tracker-near-${tracker.id}",
                    kind = InsightKind.TRACKER_NEAR_LIMIT,
                    tone = InsightTone.CAUTION,
                    title = "${tracker.title} is at ${item.percent}% of its limit.",
                    evidence = "${Money.format(item.currentMinor)} of ${Money.format(item.targetMinor)}, " +
                        "${Money.format(item.remainingMinor)} left.",
                    priority = 90,
                )
                tracker.kind != TrackerKind.SPENDING_LIMIT && item.fraction >= 1f -> Insight(
                    id = "tracker-done-${tracker.id}",
                    kind = InsightKind.TRACKER_NEAR_LIMIT,
                    tone = InsightTone.POSITIVE,
                    title = "${tracker.title} is fully funded.",
                    evidence = "${Money.format(item.currentMinor)} of ${Money.format(item.targetMinor)} reached.",
                    priority = 60,
                )
                else -> null
            }
        }

    private fun categoryShiftInsights(
        txns: List<Txn>,
        thisMonth: TimeRange,
        lastMonth: TimeRange,
    ): List<Insight> {
        val current = AnalyticsEngine.categoryTotals(txns, thisMonth).associateBy { it.category }
        val previous = AnalyticsEngine.categoryTotals(txns, lastMonth).associateBy { it.category }
        if (previous.isEmpty()) return emptyList()

        return current.values
            .mapNotNull { total ->
                val before = previous[total.category]?.amountMinor ?: return@mapNotNull null
                if (before < 50_000L) return@mapNotNull null // ignore rounding-level categories
                val delta = total.amountMinor - before
                val share = delta.toDouble() / before
                if (abs(share) < 0.25) return@mapNotNull null
                val percent = abs(share * 100).roundToInt()
                if (delta > 0) {
                    Insight(
                        id = "cat-up-${total.category.id}",
                        kind = InsightKind.CATEGORY_INCREASE,
                        tone = InsightTone.CAUTION,
                        title = "${total.category.label} spending is up $percent% on last month.",
                        evidence = "${Money.format(total.amountMinor)} this month against ${Money.format(before)} last month.",
                        priority = 80 + (percent / 10).coerceAtMost(9),
                    )
                } else {
                    Insight(
                        id = "cat-down-${total.category.id}",
                        kind = InsightKind.CATEGORY_DECREASE,
                        tone = InsightTone.POSITIVE,
                        title = "${total.category.label} spending is down $percent% on last month.",
                        evidence = "${Money.format(total.amountMinor)} this month against ${Money.format(before)} last month.",
                        priority = 50,
                    )
                }
            }
            .sortedByDescending { it.priority }
            .take(2)
    }

    private fun frequentMerchantInsights(txns: List<Txn>, range: TimeRange): List<Insight> {
        val merchants = AnalyticsEngine.merchantTotals(txns, range)
        val frequent = merchants.filter { it.count >= 6 }.maxByOrNull { it.count } ?: return emptyList()
        return listOf(
            Insight(
                id = "frequent-${frequent.merchant}",
                kind = InsightKind.FREQUENT_MERCHANT,
                tone = InsightTone.NEUTRAL,
                title = "You paid ${frequent.merchant} ${frequent.count} times this month.",
                evidence = "${Money.format(frequent.amountMinor)} in total, " +
                    "averaging ${Money.format(frequent.amountMinor / frequent.count)} a time.",
                priority = 70,
            ),
        )
    }

    private fun recurringInsights(txns: List<Txn>): List<Insight> {
        val recurring = AnalyticsEngine.recurringPayments(txns)
        val subscriptions = recurring.filter { it.isSubscriptionLike && it.category == Category.SUBSCRIPTIONS }
        if (subscriptions.isEmpty()) return emptyList()
        val monthly = subscriptions.sumOf { it.monthlyEquivalentMinor }
        return listOf(
            Insight(
                id = "recurring-subscriptions",
                kind = InsightKind.RECURRING_COMMITMENT,
                tone = InsightTone.NEUTRAL,
                title = "${subscriptions.size} subscriptions cost about ${Money.format(monthly)} a month.",
                evidence = subscriptions.take(4).joinToString(", ") { it.merchant },
                priority = 65,
            ),
        )
    }

    private fun unusualInsights(txns: List<Txn>, range: TimeRange): List<Insight> {
        val unusual = AnalyticsEngine.unusualTransactions(txns, range).firstOrNull() ?: return emptyList()
        return listOf(
            Insight(
                id = "unusual-${unusual.txn.id}",
                kind = InsightKind.UNUSUAL_TRANSACTION,
                tone = InsightTone.CAUTION,
                title = "${Money.format(unusual.txn.amountMinor)} at ${unusual.txn.merchant} is unusually large.",
                evidence = "About ${unusual.timesTypical.roundToInt()}x your usual " +
                    "${unusual.txn.category.label.lowercase()} transaction of ${Money.format(unusual.typicalMinor)}.",
                priority = 85,
            ),
        )
    }

    /**
     * Compares spending against how much of the month has actually elapsed, which is the
     * only comparison that can warn early rather than after the fact.
     */
    private fun paceInsight(
        current: PeriodSummary,
        progress: List<TrackerProgress>,
        now: Long,
        zone: ZoneId,
    ): List<Insight> {
        val monthly = progress.firstOrNull {
            it.tracker.kind == TrackerKind.SPENDING_LIMIT && it.tracker.categoryIds.isEmpty()
        } ?: return emptyList()
        if (monthly.targetMinor <= 0L) return emptyList()

        val elapsed = TimeRanges.monthElapsedFraction(now, zone)
        if (elapsed < 0.15f) return emptyList()
        val used = monthly.fraction
        if (used <= elapsed + 0.1f) return emptyList()

        val projected = (current.spendMinor / elapsed).toLong()
        return listOf(
            Insight(
                id = "pace-monthly",
                kind = InsightKind.PACE,
                tone = InsightTone.CAUTION,
                title = "At this pace you will spend about ${Money.format(projected)} this month.",
                evidence = "${(used * 100).roundToInt()}% of the budget used with " +
                    "${(elapsed * 100).roundToInt()}% of the month gone.",
                priority = 95,
            ),
        )
    }

    private fun cashFlowInsight(current: PeriodSummary, previous: PeriodSummary): List<Insight> {
        if (current.incomeMinor <= 0L && current.spendMinor <= 0L) return emptyList()
        return if (current.netMinor < 0L) {
            listOf(
                Insight(
                    id = "cashflow-negative",
                    kind = InsightKind.CASH_FLOW,
                    tone = InsightTone.CAUTION,
                    title = "You have spent ${Money.format(-current.netMinor)} more than you received this month.",
                    evidence = "${Money.format(current.incomeMinor)} in, ${Money.format(current.spendMinor)} out. " +
                        "Transfers between your own accounts are excluded.",
                    priority = 88,
                ),
            )
        } else if (previous.netMinor > 0L && current.netMinor > previous.netMinor) {
            listOf(
                Insight(
                    id = "cashflow-improving",
                    kind = InsightKind.CASH_FLOW,
                    tone = InsightTone.POSITIVE,
                    title = "You are keeping ${Money.format(current.netMinor)} this month, more than last month.",
                    evidence = "Last month you kept ${Money.format(previous.netMinor)}.",
                    priority = 55,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun savingsInsight(current: PeriodSummary): List<Insight> {
        if (current.incomeMinor <= 0L) return emptyList()
        val rate = (current.savingsRate * 100).roundToInt()
        if (rate >= 20) {
            return listOf(
                Insight(
                    id = "savings-rate",
                    kind = InsightKind.SAVINGS_OPPORTUNITY,
                    tone = InsightTone.POSITIVE,
                    title = "You are keeping $rate% of what you receive.",
                    evidence = "${Money.format(current.netMinor)} of ${Money.format(current.incomeMinor)} is unspent so far.",
                    priority = 45,
                ),
            )
        }
        return emptyList()
    }
}
