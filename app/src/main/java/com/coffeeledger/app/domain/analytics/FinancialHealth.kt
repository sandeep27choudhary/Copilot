package com.coffeeledger.app.domain.analytics

import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import java.time.ZoneId
import kotlin.math.roundToInt

/** One weighted input to the health score, with the sentence that explains it. */
data class HealthComponent(
    val name: String,
    val score: Int,
    val weight: Int,
    val explanation: String,
)

/**
 * A 0-100 score, plus every component that produced it. The breakdown is always shown:
 * a number the user cannot interrogate is not worth putting in a finance app.
 */
data class HealthScore(
    val score: Int,
    val label: String,
    val components: List<HealthComponent>,
) {
    val hasData: Boolean get() = components.any { it.weight > 0 }
}

object FinancialHealth {

    fun evaluate(
        txns: List<Txn>,
        trackerProgress: List<TrackerProgress>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): HealthScore {
        val thisMonth = TimeRanges.currentMonth(now, zone)
        val current = AnalyticsEngine.summarize(txns, thisMonth)
        val previous = AnalyticsEngine.summarize(txns, TimeRanges.previousMonth(now, zone))
        val components = listOf(
            spendingVsIncome(current, previous),
            savingsRate(current, previous),
            budgetAdherence(trackerProgress),
            recurringLoad(txns, current, previous),
            goalProgress(trackerProgress),
        )

        val totalWeight = components.sumOf { it.weight }
        val score = if (totalWeight == 0) 0
        else components.sumOf { it.score * it.weight }.toDouble().div(totalWeight).roundToInt()

        return HealthScore(score, labelFor(score), components)
    }

    private fun labelFor(score: Int): String = when {
        score >= 80 -> "Strong"
        score >= 65 -> "Steady"
        score >= 45 -> "Stretched"
        score > 0 -> "Under strain"
        else -> "Not enough data"
    }

    /** Uses last month's income when this month's salary has not landed yet. */
    private fun referenceIncome(current: PeriodSummary, previous: PeriodSummary): Long =
        if (current.incomeMinor > 0L) current.incomeMinor else previous.incomeMinor

    private fun spendingVsIncome(current: PeriodSummary, previous: PeriodSummary): HealthComponent {
        val income = referenceIncome(current, previous)
        if (income <= 0L) {
            return HealthComponent("Spending vs income", 0, 0, "No income recorded yet.")
        }
        val ratio = current.spendMinor.toDouble() / income
        val score = when {
            ratio <= 0.5 -> 100
            ratio >= 1.2 -> 0
            else -> (100 - ((ratio - 0.5) / 0.7 * 100)).roundToInt()
        }.coerceIn(0, 100)
        return HealthComponent(
            name = "Spending vs income",
            score = score,
            weight = 30,
            explanation = "You have spent ${Money.format(current.spendMinor)} against " +
                "${Money.format(income)} of income, or ${(ratio * 100).roundToInt()}%.",
        )
    }

    private fun savingsRate(current: PeriodSummary, previous: PeriodSummary): HealthComponent {
        val income = referenceIncome(current, previous)
        if (income <= 0L) {
            return HealthComponent("Savings rate", 0, 0, "No income recorded yet.")
        }
        val kept = (income - current.spendMinor).coerceAtLeast(0L)
        val rate = kept.toDouble() / income
        // 30% kept is treated as a full score; beyond that the extra does not add much.
        val score = ((rate / 0.3) * 100).roundToInt().coerceIn(0, 100)
        return HealthComponent(
            name = "Savings rate",
            score = score,
            weight = 25,
            explanation = "You are keeping ${(rate * 100).roundToInt()}% of your income, " +
                "or ${Money.format(kept)} so far this month.",
        )
    }

    private fun budgetAdherence(progress: List<TrackerProgress>): HealthComponent {
        val limits = progress.filter { it.tracker.kind == TrackerKind.SPENDING_LIMIT }
        if (limits.isEmpty()) {
            return HealthComponent("Budget adherence", 0, 0, "No spending limits set yet.")
        }
        val withinLimit = limits.count { !it.isOver }
        val score = (withinLimit * 100.0 / limits.size).roundToInt()
        val over = limits.filter { it.isOver }
        return HealthComponent(
            name = "Budget adherence",
            score = score,
            weight = 20,
            explanation = if (over.isEmpty()) {
                "All ${limits.size} spending limits are still within budget."
            } else {
                "${over.size} of ${limits.size} limits are over: ${over.joinToString(", ") { it.tracker.title }}."
            },
        )
    }

    /** Fixed monthly commitments eating a large share of income is the main fragility signal. */
    private fun recurringLoad(
        txns: List<Txn>,
        current: PeriodSummary,
        previous: PeriodSummary,
    ): HealthComponent {
        val income = referenceIncome(current, previous)
        if (income <= 0L) {
            return HealthComponent("Recurring commitments", 0, 0, "No income recorded yet.")
        }
        val recurring = AnalyticsEngine.recurringPayments(txns)
        val monthly = recurring.sumOf { it.monthlyEquivalentMinor }
        val share = monthly.toDouble() / income
        val score = when {
            share <= 0.2 -> 100
            share >= 0.6 -> 0
            else -> (100 - ((share - 0.2) / 0.4 * 100)).roundToInt()
        }.coerceIn(0, 100)
        return HealthComponent(
            name = "Recurring commitments",
            score = score,
            weight = 15,
            explanation = "${recurring.size} recurring payments come to about " +
                "${Money.format(monthly)} a month, ${(share * 100).roundToInt()}% of your income.",
        )
    }

    private fun goalProgress(progress: List<TrackerProgress>): HealthComponent {
        val goals = progress.filter { it.tracker.kind != TrackerKind.SPENDING_LIMIT }
        if (goals.isEmpty()) {
            return HealthComponent("Goal progress", 0, 0, "No savings goals set yet.")
        }
        val average = goals.map { (it.fraction * 100).coerceIn(0f, 100f) }.average()
        return HealthComponent(
            name = "Goal progress",
            score = average.roundToInt(),
            weight = 10,
            explanation = "Your ${goals.size} goals are ${average.roundToInt()}% funded on average.",
        )
    }
}
