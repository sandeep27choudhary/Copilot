package com.coffeeledger.app.domain.query

import com.coffeeledger.app.domain.analytics.TimeRange
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.Txn

/** What the timeline is currently showing. Every field is optional and combines with AND. */
data class TransactionFilter(
    val query: String = "",
    val flow: Flow = Flow.ALL,
    val categoryIds: Set<String> = emptySet(),
    val sourceApps: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val range: TimeRange? = null,
    /** Narrows to entries the parser was unsure about, so they can be corrected. */
    val onlyNeedsReview: Boolean = false,
) {
    /** Direction, with transfers as their own choice because they are neither. */
    enum class Flow(val label: String) {
        ALL("All"),
        SPENT("Spent"),
        RECEIVED("Received"),
        TRANSFERS("Transfers"),
    }

    val isActive: Boolean
        get() = query.isNotBlank() || flow != Flow.ALL || categoryIds.isNotEmpty() ||
            sourceApps.isNotEmpty() || accountIds.isNotEmpty() || range != null || onlyNeedsReview

    val activeCount: Int
        get() = listOf(
            query.isNotBlank(),
            flow != Flow.ALL,
            categoryIds.isNotEmpty(),
            sourceApps.isNotEmpty(),
            accountIds.isNotEmpty(),
            range != null,
            onlyNeedsReview,
        ).count { it }

    fun apply(transactions: List<Txn>): List<Txn> = transactions.filter { matches(it) }

    private fun matches(txn: Txn): Boolean {
        if (onlyNeedsReview && !txn.needsReview) return false
        if (!matchesFlow(txn)) return false
        if (categoryIds.isNotEmpty() && txn.category.id !in categoryIds) return false
        if (sourceApps.isNotEmpty() && txn.sourceApp !in sourceApps) return false
        if (accountIds.isNotEmpty() && (txn.accountId == null || txn.accountId !in accountIds)) return false
        if (range != null && txn.occurredAt !in range) return false
        if (query.isNotBlank() && !matchesQuery(txn)) return false
        return true
    }

    private fun matchesFlow(txn: Txn): Boolean = when (flow) {
        Flow.ALL -> true
        Flow.TRANSFERS -> txn.isTransfer
        Flow.SPENT -> txn.direction == Direction.DEBIT && !txn.isTransfer
        Flow.RECEIVED -> txn.direction == Direction.CREDIT && !txn.isTransfer
    }

    /** Free text searches the merchant, the category, the source and the reference. */
    private fun matchesQuery(txn: Txn): Boolean {
        val needle = query.trim().lowercase()
        return txn.merchant.lowercase().contains(needle) ||
            txn.merchantRaw.lowercase().contains(needle) ||
            txn.category.label.lowercase().contains(needle) ||
            txn.sourceApp.lowercase().contains(needle) ||
            txn.notes?.lowercase()?.contains(needle) == true ||
            txn.reference?.lowercase()?.contains(needle) == true
    }

    companion object {
        /** The categories worth offering as chips: the ones actually present. */
        fun categoriesPresent(transactions: List<Txn>): List<Category> =
            transactions.map { it.category }.distinct().sortedBy { it.label }

        fun sourcesPresent(transactions: List<Txn>): List<String> =
            transactions.map { it.sourceApp }.distinct().sorted()
    }
}
