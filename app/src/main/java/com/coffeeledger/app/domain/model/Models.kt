package com.coffeeledger.app.domain.model

/** Direction of money movement, from the user's point of view. */
enum class Direction { DEBIT, CREDIT }

/** Where a transaction entered the ledger. Kept so the original source is never lost. */
enum class SourceType(val label: String) {
    SMS("SMS"),
    UPI_APP("UPI app"),
    BANK("Bank"),
    CSV("CSV import"),
    PDF("PDF statement"),
    MANUAL("Manual entry"),
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    NET_BANKING("Net banking"),
    IMPS("IMPS"),
    NEFT("NEFT"),
    ATM("ATM"),
    AUTO_DEBIT("Auto debit"),
    CASH("Cash"),
    UNKNOWN("Other"),
}

enum class AccountType(val label: String) {
    BANK("Bank"),
    CARD("Card"),
    WALLET("Wallet"),
    CASH("Cash"),
}

/**
 * The fixed category vocabulary. Categories are deliberately few: a long list makes
 * every screen noisier without making the totals any more useful.
 */
enum class Category(
    val id: String,
    val label: String,
    val direction: Direction,
    val discretionary: Boolean = false,
) {
    // Credits
    SALARY("salary", "Salary", Direction.CREDIT),
    REFUND("refund", "Refunds", Direction.CREDIT),
    TRANSFER_IN("transfer_in", "Transfer received", Direction.CREDIT),
    PAYMENT_RECEIVED("payment_received", "Payments received", Direction.CREDIT),
    OTHER_INCOME("other_income", "Other income", Direction.CREDIT),

    // Debits
    FOOD("food", "Food", Direction.DEBIT, discretionary = true),
    GROCERIES("groceries", "Groceries", Direction.DEBIT),
    SHOPPING("shopping", "Shopping", Direction.DEBIT, discretionary = true),
    TRANSPORT("transport", "Transport", Direction.DEBIT),
    RENT("rent", "Rent", Direction.DEBIT),
    BILLS("bills", "Bills", Direction.DEBIT),
    UTILITIES("utilities", "Utilities", Direction.DEBIT),
    SUBSCRIPTIONS("subscriptions", "Subscriptions", Direction.DEBIT, discretionary = true),
    ENTERTAINMENT("entertainment", "Entertainment", Direction.DEBIT, discretionary = true),
    HEALTH("health", "Health", Direction.DEBIT),
    EDUCATION("education", "Education", Direction.DEBIT),
    TRAVEL("travel", "Travel", Direction.DEBIT, discretionary = true),
    INVESTMENT("investment", "Investment", Direction.DEBIT),
    TRANSFER_OUT("transfer_out", "Transfer sent", Direction.DEBIT),
    OTHER_EXPENSE("other_expense", "Other expenses", Direction.DEBIT);

    /** True when the category represents money moving between the user's own accounts. */
    val isTransferCategory: Boolean get() = this == TRANSFER_IN || this == TRANSFER_OUT

    companion object {
        fun fromId(id: String?): Category? = id?.let { key -> entries.firstOrNull { it.id == key } }

        fun debits(): List<Category> = entries.filter { it.direction == Direction.DEBIT }

        fun credits(): List<Category> = entries.filter { it.direction == Direction.CREDIT }

        fun defaultFor(direction: Direction): Category =
            if (direction == Direction.CREDIT) OTHER_INCOME else OTHER_EXPENSE
    }
}

/** How a transaction's category was decided; user choices are never overwritten by rules. */
enum class CategorySource { AUTO, USER }

/**
 * A single ledger entry. This is the shape every screen, the analytics engine and the
 * advisor read from, whatever the entry's original source was.
 */
data class Txn(
    val id: String,
    val occurredAt: Long,
    val amountMinor: Long,
    val direction: Direction,
    val merchant: String,
    val merchantRaw: String = merchant,
    val category: Category,
    val categorySource: CategorySource = CategorySource.AUTO,
    val accountId: String? = null,
    val accountTail: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceApp: String = "Manual",
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,
    val reference: String? = null,
    val notes: String? = null,
    val isTransfer: Boolean = false,
    /** True when the parser was not confident. Shown in the list so it can be corrected. */
    val needsReview: Boolean = false,
) {
    /** Signed amount: credits add, debits subtract. */
    val signedMinor: Long get() = if (direction == Direction.CREDIT) amountMinor else -amountMinor

    /** Real spending excludes movements between the user's own accounts. */
    val countsAsSpending: Boolean get() = direction == Direction.DEBIT && !isTransfer

    val countsAsIncome: Boolean get() = direction == Direction.CREDIT && !isTransfer
}

/** A bank account, card or wallet the user holds. */
data class Account(
    val id: String,
    val displayName: String,
    val institution: String,
    val tail: String?,
    val type: AccountType,
    val openingBalanceMinor: Long = 0L,
    val includeInTotals: Boolean = true,
) {
    val maskedLabel: String get() = if (tail.isNullOrBlank()) displayName else "•••• $tail"
}

/** What a tracker measures. */
enum class TrackerKind(val label: String) {
    SPENDING_LIMIT("Spending limit"),
    SAVINGS_TARGET("Savings target"),
    GOAL("Financial goal"),
}

/** The window a tracker resets over. */
enum class TrackerPeriod(val label: String) {
    MONTHLY("This month"),
    ALL_TIME("Overall"),
}

/**
 * A tracker is the app's single budgeting primitive: a monthly spending cap, a savings
 * target or a long-running goal are all the same thing with a different filter.
 */
data class Tracker(
    val id: String,
    val title: String,
    val kind: TrackerKind,
    val period: TrackerPeriod,
    val targetMinor: Long,
    val categoryIds: List<String> = emptyList(),
    val merchantNames: List<String> = emptyList(),
    val accountIds: List<String> = emptyList(),
    val manualProgressMinor: Long = 0L,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/** A tracker plus the progress computed from the local ledger. */
data class TrackerProgress(
    val tracker: Tracker,
    val currentMinor: Long,
) {
    val targetMinor: Long get() = tracker.targetMinor
    val fraction: Float
        get() = if (targetMinor <= 0L) 0f else (currentMinor.toDouble() / targetMinor).toFloat()
    val percent: Int get() = kotlin.math.round(fraction * 100).toInt()
    val remainingMinor: Long get() = (targetMinor - currentMinor).coerceAtLeast(0L)
    val overBy: Long get() = (currentMinor - targetMinor).coerceAtLeast(0L)
    val isOver: Boolean get() = currentMinor > targetMinor
    /** Spending trackers turn cautionary near the limit; goals never do. */
    val isNearLimit: Boolean
        get() = tracker.kind == TrackerKind.SPENDING_LIMIT && !isOver && fraction >= 0.85f
}
