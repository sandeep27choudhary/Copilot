package com.coffeeledger.app.domain.advisor

import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.analytics.TimeRange
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the advisor is allowed to look at: the local ledger, and nothing else. */
data class AdvisorContext(
    val txns: List<Txn>,
    val trackerProgress: List<TrackerProgress>,
    val now: Long,
    val openingBalanceMinor: Long = 0L,
    val zone: ZoneId = ZoneId.systemDefault(),
)

enum class AdvisorIntent {
    TOP_SPEND,
    CATEGORY_SPEND,
    MERCHANT_SPEND,
    PERIOD_SPEND,
    INCOME,
    BALANCE,
    REDUCE,
    RECURRING,
    SAVE,
    AFFORD,
    TOP_MERCHANTS,
    COMPARE,
    UNKNOWN,
}

/**
 * An answer, split so the UI can show a large figure above a calm explanation.
 * [isPlanning] marks answers that shade into forward-looking planning, which the UI
 * labels as general information rather than regulated investment advice.
 */
data class AdvisorAnswer(
    val intent: AdvisorIntent,
    val headline: String,
    val detail: List<String>,
    val isPlanning: Boolean = false,
)

/**
 * A rule-based advisor that answers questions from structured local data.
 *
 * There is no model call and no network call anywhere in this file. Questions are matched
 * to an intent, the intent runs a query over the in-memory ledger, and the answer is
 * assembled from the result.
 */
object LocalAdvisor {

    val suggestedQuestions = listOf(
        "Where did I spend the most?",
        "How much did I spend on food?",
        "What can I reduce next month?",
        "What are my recurring expenses?",
        "How much can I save?",
        "Can I afford a ₹60,000 purchase?",
        "How much did I spend this week?",
        "Which merchants do I use most?",
        "What changed compared with last month?",
    )

    fun answer(question: String, context: AdvisorContext): AdvisorAnswer {
        val q = question.lowercase().trim()
        val range = detectRange(q, context)
        return when (detectIntent(q)) {
            AdvisorIntent.TOP_SPEND -> topSpend(q, context, range)
            AdvisorIntent.CATEGORY_SPEND -> categorySpend(q, context, range)
            AdvisorIntent.MERCHANT_SPEND -> merchantSpend(q, context, range)
            AdvisorIntent.PERIOD_SPEND -> periodSpend(context, range)
            AdvisorIntent.INCOME -> income(context, range)
            AdvisorIntent.BALANCE -> balance(context)
            AdvisorIntent.REDUCE -> reduce(context)
            AdvisorIntent.RECURRING -> recurring(context)
            AdvisorIntent.SAVE -> save(context)
            AdvisorIntent.AFFORD -> afford(q, context)
            AdvisorIntent.TOP_MERCHANTS -> topMerchants(context, range)
            AdvisorIntent.COMPARE -> compare(context)
            AdvisorIntent.UNKNOWN -> unknown()
        }
    }

    // ------------------------------------------------------------- routing

    internal fun detectIntent(q: String): AdvisorIntent = when {
        contains(q, "afford", "can i buy", "should i buy") -> AdvisorIntent.AFFORD
        contains(q, "recurring", "subscription", "every month", "regular payment") -> AdvisorIntent.RECURRING
        contains(q, "reduce", "cut back", "cut down", "save more next", "spend less") -> AdvisorIntent.REDUCE
        contains(q, "how much can i save", "how much could i save", "saving potential") -> AdvisorIntent.SAVE
        contains(q, "changed", "compared", "vs last month", "versus last month", "difference") -> AdvisorIntent.COMPARE
        contains(q, "which merchant", "merchants do i", "top merchant", "who do i pay", "most used merchant") -> AdvisorIntent.TOP_MERCHANTS
        contains(q, "balance", "how much do i have", "net worth") -> AdvisorIntent.BALANCE
        contains(q, "earn", "income", "received", "credited", "salary") -> AdvisorIntent.INCOME
        contains(q, "where did i spend", "spend the most", "biggest expense", "top categor", "largest spend") -> AdvisorIntent.TOP_SPEND
        matchCategory(q) != null && contains(q, "spend", "spent", "cost", "how much") -> AdvisorIntent.CATEGORY_SPEND
        contains(q, "spend", "spent", "spending", "outflow", "expenses") -> AdvisorIntent.PERIOD_SPEND
        contains(q, "save", "savings") -> AdvisorIntent.SAVE
        else -> AdvisorIntent.UNKNOWN
    }

    internal fun detectRange(q: String, context: AdvisorContext): TimeRange = when {
        contains(q, "this week", "past week", "last 7 days") -> TimeRanges.currentWeek(context.now, context.zone)
        contains(q, "last month", "previous month") -> TimeRanges.previousMonth(context.now, context.zone)
        contains(q, "last 30 days", "past 30 days", "last thirty days") -> TimeRanges.lastDays(context.now, 30, context.zone)
        contains(q, "last 90 days", "past 3 months", "last quarter") -> TimeRanges.lastDays(context.now, 90, context.zone)
        contains(q, "all time", "ever", "overall", "in total") -> TimeRanges.allTime()
        contains(q, "today") -> TimeRanges.lastDays(context.now, 1, context.zone)
        else -> TimeRanges.currentMonth(context.now, context.zone)
    }

    // ------------------------------------------------------------ answers

    private fun topSpend(q: String, context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val totals = AnalyticsEngine.categoryTotals(context.txns, range)
        val top = totals.firstOrNull() ?: return noData(range)
        val merchants = AnalyticsEngine.merchantTotals(context.txns, range)
        val detail = buildList {
            add("That is ${shareOf(top.amountMinor, totals.sumOf { it.amountMinor })} of your spending in ${range.label.lowercase()}, across ${top.count} transactions.")
            totals.drop(1).take(3).forEach { add("${it.category.label}: ${Money.format(it.amountMinor)}") }
            merchants.firstOrNull()?.let {
                add("Your largest single merchant was ${it.merchant} at ${Money.format(it.amountMinor)}.")
            }
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.TOP_SPEND,
            headline = "${top.category.label} — ${Money.format(top.amountMinor)}",
            detail = detail,
        )
    }

    private fun categorySpend(q: String, context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val category = matchCategory(q) ?: return unknown()
        val matching = AnalyticsEngine.inRange(context.txns, range)
            .filter { it.category == category && !it.isTransfer }
        val total = matching.sumOf { it.amountMinor }
        if (matching.isEmpty()) {
            return AdvisorAnswer(
                intent = AdvisorIntent.CATEGORY_SPEND,
                headline = "Nothing on ${category.label.lowercase()}",
                detail = listOf("No ${category.label.lowercase()} transactions in ${range.label.lowercase()}."),
            )
        }
        val tracker = context.trackerProgress.firstOrNull { it.tracker.categoryIds.contains(category.id) }
        val detail = buildList {
            add("${matching.size} transactions in ${range.label.lowercase()}, averaging ${Money.format(total / matching.size)}.")
            matching.groupBy { it.merchant }
                .map { (m, list) -> m to list.sumOf { it.amountMinor } }
                .sortedByDescending { it.second }
                .take(3)
                .forEach { (m, amount) -> add("$m: ${Money.format(amount)}") }
            tracker?.let {
                add("Against your ${it.tracker.title} tracker: ${Money.format(it.currentMinor)} of ${Money.format(it.targetMinor)}, ${it.percent}% used.")
            }
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.CATEGORY_SPEND,
            headline = Money.format(total),
            detail = detail,
        )
    }

    private fun merchantSpend(q: String, context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val merchants = AnalyticsEngine.merchantTotals(context.txns, range)
        val match = merchants.firstOrNull { q.contains(it.merchant.lowercase()) } ?: return unknown()
        return AdvisorAnswer(
            intent = AdvisorIntent.MERCHANT_SPEND,
            headline = Money.format(match.amountMinor),
            detail = listOf("${match.count} payments to ${match.merchant} in ${range.label.lowercase()}."),
        )
    }

    private fun periodSpend(context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val summary = AnalyticsEngine.summarize(context.txns, range)
        if (summary.transactionCount == 0) return noData(range)
        val top = AnalyticsEngine.categoryTotals(context.txns, range).take(3)
        val detail = buildList {
            add("${summary.transactionCount} transactions in ${range.label.lowercase()}.")
            if (summary.transferMinor > 0L) {
                add("${Money.format(summary.transferMinor)} moved between your own accounts is excluded from this figure.")
            }
            top.forEach { add("${it.category.label}: ${Money.format(it.amountMinor)}") }
        }
        return AdvisorAnswer(AdvisorIntent.PERIOD_SPEND, Money.format(summary.spendMinor), detail)
    }

    private fun income(context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val summary = AnalyticsEngine.summarize(context.txns, range)
        val sources = AnalyticsEngine.merchantTotals(context.txns, range, Direction.CREDIT).take(3)
        val detail = buildList {
            add("Money received in ${range.label.lowercase()}, excluding transfers between your own accounts.")
            sources.forEach { add("${it.merchant}: ${Money.format(it.amountMinor)}") }
        }
        return AdvisorAnswer(AdvisorIntent.INCOME, Money.format(summary.incomeMinor), detail)
    }

    private fun balance(context: AdvisorContext): AdvisorAnswer {
        val balance = AnalyticsEngine.totalBalance(context.txns, context.openingBalanceMinor)
        return AdvisorAnswer(
            intent = AdvisorIntent.BALANCE,
            headline = Money.format(balance),
            detail = listOf(
                "Opening balances plus every credit and debit recorded on this device.",
                "This is only as complete as the transactions the app has seen.",
            ),
        )
    }

    private fun reduce(context: AdvisorContext): AdvisorAnswer {
        val thisMonth = TimeRanges.currentMonth(context.now, context.zone)
        val lastMonth = TimeRanges.previousMonth(context.now, context.zone)
        val current = AnalyticsEngine.categoryTotals(context.txns, thisMonth)
        val previous = AnalyticsEngine.categoryTotals(context.txns, lastMonth).associateBy { it.category }

        val discretionary = current.filter { it.category.discretionary }.sortedByDescending { it.amountMinor }
        if (discretionary.isEmpty()) return noData(thisMonth)

        val target = discretionary.first()
        val trimmed = target.amountMinor / 4
        val detail = buildList {
            add("${target.category.label} is your largest flexible category at ${Money.format(target.amountMinor)} across ${target.count} transactions.")
            previous[target.category]?.let {
                val delta = target.amountMinor - it.amountMinor
                if (delta > 0) add("That is ${Money.format(delta)} more than last month.")
            }
            add("Trimming it by a quarter would free about ${Money.format(trimmed)} a month.")
            discretionary.drop(1).take(2).forEach {
                add("${it.category.label} is also flexible at ${Money.format(it.amountMinor)}.")
            }
            add("This is a summary of your own spending, not investment advice.")
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.REDUCE,
            headline = "About ${Money.format(trimmed)} a month",
            detail = detail,
            isPlanning = true,
        )
    }

    private fun recurring(context: AdvisorContext): AdvisorAnswer {
        val recurring = AnalyticsEngine.recurringPayments(context.txns)
        if (recurring.isEmpty()) {
            return AdvisorAnswer(
                intent = AdvisorIntent.RECURRING,
                headline = "None detected yet",
                detail = listOf("A payment needs to repeat at least three times before it is treated as recurring."),
            )
        }
        val monthly = recurring.sumOf { it.monthlyEquivalentMinor }
        val detail = buildList {
            add("${recurring.size} repeating payments found in your ledger.")
            recurring.take(6).forEach {
                add("${it.merchant}: ${Money.format(it.typicalAmountMinor)} about every ${it.averageIntervalDays} days")
            }
        }
        return AdvisorAnswer(AdvisorIntent.RECURRING, "${Money.format(monthly)} a month", detail)
    }

    private fun save(context: AdvisorContext): AdvisorAnswer {
        val thisMonth = TimeRanges.currentMonth(context.now, context.zone)
        val lastMonth = TimeRanges.previousMonth(context.now, context.zone)
        val current = AnalyticsEngine.summarize(context.txns, thisMonth)
        val previous = AnalyticsEngine.summarize(context.txns, lastMonth)
        val income = if (current.incomeMinor > 0L) current.incomeMinor else previous.incomeMinor
        if (income <= 0L) return noData(thisMonth)

        val essentials = AnalyticsEngine.categoryTotals(context.txns, lastMonth)
            .filterNot { it.category.discretionary }
            .sumOf { it.amountMinor }
        val flexible = AnalyticsEngine.categoryTotals(context.txns, lastMonth)
            .filter { it.category.discretionary }
            .sumOf { it.amountMinor }
        val realistic = (income - essentials - flexible / 2).coerceAtLeast(0L)

        return AdvisorAnswer(
            intent = AdvisorIntent.SAVE,
            headline = "Up to ${Money.format(realistic)} a month",
            detail = listOf(
                "Based on ${Money.format(income)} of income and ${Money.format(essentials)} of essential spending last month.",
                "It assumes you halve the ${Money.format(flexible)} you spent on flexible categories.",
                "This is a calculation from your own data, not investment advice.",
            ),
            isPlanning = true,
        )
    }

    private fun afford(q: String, context: AdvisorContext): AdvisorAnswer {
        val price = extractAmount(q) ?: return AdvisorAnswer(
            intent = AdvisorIntent.AFFORD,
            headline = "How much is it?",
            detail = listOf("Include the amount, for example \"Can I afford a ₹60,000 purchase?\""),
        )
        val balance = AnalyticsEngine.totalBalance(context.txns, context.openingBalanceMinor)
        val lastMonth = AnalyticsEngine.summarize(context.txns, TimeRanges.previousMonth(context.now, context.zone))
        val monthlySurplus = lastMonth.netMinor
        val goals = context.trackerProgress.filter { it.tracker.kind != TrackerKind.SPENDING_LIMIT }
        val committed = goals.sumOf { it.currentMinor }
        val free = balance - committed

        val verdict = when {
            free >= price * 2 -> "Comfortably"
            free >= price -> "Yes, but it is a large share of what is free"
            monthlySurplus > 0 -> "Not outright"
            else -> "Not from current balances"
        }
        val detail = buildList {
            add("Recorded balance is ${Money.format(balance)}, of which ${Money.format(committed)} is already sitting against goals.")
            add("That leaves about ${Money.format(free)} uncommitted.")
            if (free < price && monthlySurplus > 0) {
                val months = ((price - free).toDouble() / monthlySurplus).let { if (it < 1) 1 else it.roundToInt() }
                add("At last month's surplus of ${Money.format(monthlySurplus)}, you would cover the gap in about $months months.")
            }
            add("This reflects only what this app has recorded, and is general information rather than investment advice.")
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.AFFORD,
            headline = "$verdict — ${Money.format(price)}",
            detail = detail,
            isPlanning = true,
        )
    }

    private fun topMerchants(context: AdvisorContext, range: TimeRange): AdvisorAnswer {
        val merchants = AnalyticsEngine.merchantTotals(context.txns, range)
        if (merchants.isEmpty()) return noData(range)
        val byCount = merchants.sortedByDescending { it.count }
        val top = byCount.first()
        val detail = byCount.take(6).map {
            "${it.merchant}: ${it.count} payments, ${Money.format(it.amountMinor)}"
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.TOP_MERCHANTS,
            headline = "${top.merchant} — ${top.count} payments",
            detail = detail,
        )
    }

    private fun compare(context: AdvisorContext): AdvisorAnswer {
        val thisMonth = TimeRanges.currentMonth(context.now, context.zone)
        val lastMonth = TimeRanges.previousMonth(context.now, context.zone)
        val current = AnalyticsEngine.summarize(context.txns, thisMonth)
        val previous = AnalyticsEngine.summarize(context.txns, lastMonth)
        if (previous.transactionCount == 0) {
            return AdvisorAnswer(
                intent = AdvisorIntent.COMPARE,
                headline = "No prior month to compare",
                detail = listOf("Once there is a full month of history this comparison becomes available."),
            )
        }
        val delta = current.spendMinor - previous.spendMinor
        val direction = if (delta >= 0) "more" else "less"
        val currentCats = AnalyticsEngine.categoryTotals(context.txns, thisMonth).associateBy { it.category }
        val previousCats = AnalyticsEngine.categoryTotals(context.txns, lastMonth).associateBy { it.category }
        val movers = (currentCats.keys + previousCats.keys)
            .map { category ->
                category to ((currentCats[category]?.amountMinor ?: 0L) - (previousCats[category]?.amountMinor ?: 0L))
            }
            .sortedByDescending { abs(it.second) }
            .take(4)

        val detail = buildList {
            add("${Money.format(current.spendMinor)} this month against ${Money.format(previous.spendMinor)} last month.")
            movers.forEach { (category, change) ->
                val word = if (change >= 0) "up" else "down"
                add("${category.label} $word ${Money.format(abs(change))}")
            }
        }
        return AdvisorAnswer(
            intent = AdvisorIntent.COMPARE,
            headline = "${Money.format(abs(delta))} $direction",
            detail = detail,
        )
    }

    private fun noData(range: TimeRange) = AdvisorAnswer(
        intent = AdvisorIntent.UNKNOWN,
        headline = "Nothing recorded",
        detail = listOf("There are no transactions in ${range.label.lowercase()} yet."),
    )

    private fun unknown() = AdvisorAnswer(
        intent = AdvisorIntent.UNKNOWN,
        headline = "Not sure yet",
        detail = listOf(
            "This advisor answers from your local transactions only.",
            "Try one of the suggested questions below.",
        ),
    )

    // ------------------------------------------------------------ helpers

    private fun contains(q: String, vararg needles: String) = needles.any { q.contains(it) }

    private fun shareOf(part: Long, whole: Long): String =
        if (whole <= 0L) "all" else "${((part * 100.0) / whole).roundToInt()}%"

    private val CATEGORY_SYNONYMS: Map<Category, List<String>> = mapOf(
        Category.FOOD to listOf("food", "eating out", "restaurant", "food delivery", "dining", "takeaway"),
        Category.GROCERIES to listOf("grocer", "groceries", "supermarket", "kirana"),
        Category.SHOPPING to listOf("shopping", "clothes", "apparel", "gadget"),
        Category.TRANSPORT to listOf("transport", "cab", "taxi", "fuel", "petrol", "commute", "uber", "ola"),
        Category.RENT to listOf("rent", "landlord"),
        Category.BILLS to listOf("bill", "bills", "phone", "broadband", "internet"),
        Category.UTILITIES to listOf("utilities", "electricity", "water", "gas"),
        Category.SUBSCRIPTIONS to listOf("subscription", "subscriptions", "streaming"),
        Category.ENTERTAINMENT to listOf("entertainment", "movies", "cinema"),
        Category.HEALTH to listOf("health", "medical", "pharmacy", "doctor"),
        Category.EDUCATION to listOf("education", "course", "tuition"),
        Category.TRAVEL to listOf("travel", "trip", "flight", "holiday", "hotel"),
        Category.INVESTMENT to listOf("investment", "invested", "sip", "mutual fund"),
        Category.SALARY to listOf("salary", "payroll"),
    )

    internal fun matchCategory(q: String): Category? {
        var best: Category? = null
        var bestLength = 0
        for ((category, words) in CATEGORY_SYNONYMS) {
            for (word in words) {
                if (word.length > bestLength && q.contains(word)) {
                    best = category
                    bestLength = word.length
                }
            }
        }
        return best
    }

    private val QUESTION_AMOUNT = Regex(
        """(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(k|l|lakh|lakhs|cr|crore)?""",
        RegexOption.IGNORE_CASE,
    )

    internal fun extractAmount(q: String): Long? {
        for (match in QUESTION_AMOUNT.findAll(q)) {
            val base = Money.parseAmount(match.groupValues[1]) ?: continue
            if (base <= 0L) continue
            val multiplier = when (match.groupValues[2].lowercase()) {
                "k" -> 1_000L
                "l", "lakh", "lakhs" -> 100_000L
                "cr", "crore" -> 10_000_000L
                else -> 1L
            }
            val value = base * multiplier
            // Ignore stray small numbers such as "next 3 months".
            if (value < 100_00L) continue
            return value
        }
        return null
    }
}
