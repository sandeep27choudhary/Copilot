package com.coffeeledger.app.domain.categorize

import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.normalize.MerchantNormalizer

/** A user-taught rule. Rules are learned locally when the user re-categorises something. */
data class CategoryRule(
    val id: String,
    val matchType: MatchType,
    val value: String,
    val categoryId: String,
    val isUserDefined: Boolean = true,
) {
    enum class MatchType { MERCHANT, KEYWORD }
}

/** The decision plus a short reason, which the transaction detail screen shows verbatim. */
data class Categorization(
    val category: Category,
    val isTransfer: Boolean,
    val reason: String,
)

/**
 * Decides a category with a strict precedence: what the user taught, then the merchant
 * catalog, then keywords in the raw text, then a direction-based fallback.
 *
 * Self-transfers are detected here too, because a transfer between the user's own accounts
 * must never be counted as spending anywhere in the app.
 */
object Categorizer {

    private data class KeywordRule(val category: Category, val keywords: List<String>)

    private val KEYWORD_RULES = listOf(
        KeywordRule(Category.SALARY, listOf("salary", "payroll", "sal cr", "salary credit", "wages", "stipend")),
        KeywordRule(Category.REFUND, listOf("refund", "reversal", "reversed", "cashback", "chargeback")),
        KeywordRule(Category.RENT, listOf("rent", "landlord", "nobroker", "housing society", "maintenance charges")),
        KeywordRule(Category.UTILITIES, listOf("electricity", "power bill", "water bill", "gas bill", "lpg", "bescom", "discom")),
        KeywordRule(Category.BILLS, listOf("broadband", "postpaid", "prepaid recharge", "mobile bill", "dth", "fibernet", "insurance premium")),
        KeywordRule(Category.INVESTMENT, listOf("sip", "mutual fund", "nps", "ppf", "rd instal", "recurring deposit", "demat", "elss")),
        KeywordRule(Category.HEALTH, listOf("pharmacy", "hospital", "clinic", "diagnostic", "lab test", "medicine")),
        KeywordRule(Category.EDUCATION, listOf("tuition", "school fee", "college fee", "course fee", "exam fee")),
        KeywordRule(Category.TRANSPORT, listOf("fuel", "petrol", "diesel", "toll", "fastag", "metro", "parking")),
        KeywordRule(Category.TRAVEL, listOf("flight", "hotel", "booking", "irctc", "airlines", "resort")),
        KeywordRule(Category.FOOD, listOf("restaurant", "cafe", "food delivery", "bakery", "biryani", "pizza")),
        KeywordRule(Category.GROCERIES, listOf("supermarket", "kirana", "grocery", "provision store", "vegetables")),
        KeywordRule(Category.SUBSCRIPTIONS, listOf("subscription", "membership", "renewal", "auto renew")),
        KeywordRule(Category.ENTERTAINMENT, listOf("movie", "cinema", "concert", "gaming")),
        KeywordRule(Category.SHOPPING, listOf("shopping", "apparel", "electronics", "furniture")),
    )

    private val TRANSFER_KEYWORDS = listOf(
        "self transfer", "self-transfer", "own account", "to self", "from self",
        "transfer to own", "internal transfer", "acct transfer", "sweep",
    )

    /**
     * @param userRules rules learned from the user's own edits, highest precedence.
     * @param ownAccountTails the last digits of every account the user has added; a
     * counterparty matching one of these is a transfer, not a purchase.
     */
    fun categorize(
        merchantRaw: String?,
        direction: Direction,
        rawText: String?,
        userRules: List<CategoryRule> = emptyList(),
        ownAccountTails: Set<String> = emptySet(),
        counterpartyTail: String? = null,
    ): Categorization {
        val merchant = MerchantNormalizer.normalize(merchantRaw)
        val haystack = buildString {
            append(merchantRaw.orEmpty()).append(' ')
            append(merchant).append(' ')
            append(rawText.orEmpty())
        }.lowercase()

        // 1. Transfers first: a transfer is never spending, whatever the merchant says.
        if (isSelfTransfer(haystack, ownAccountTails, counterpartyTail)) {
            val category = if (direction == Direction.CREDIT) Category.TRANSFER_IN else Category.TRANSFER_OUT
            return Categorization(category, isTransfer = true, reason = "Moves money between your own accounts")
        }

        // 2. Rules the user taught this app.
        userRules.firstOrNull { rule -> matches(rule, merchant, haystack) }?.let { rule ->
            Category.fromId(rule.categoryId)?.let {
                return Categorization(it, isTransfer = it.isTransferCategory, reason = "Your rule for $merchant")
            }
        }

        // 3. The built-in merchant catalog.
        if (direction == Direction.DEBIT) {
            MerchantNormalizer.suggestedCategory(merchantRaw)?.let {
                return Categorization(it, isTransfer = false, reason = "$merchant is a known ${it.label.lowercase()} merchant")
            }
        }

        // 4. Keywords in the message text.
        KEYWORD_RULES.firstOrNull { rule ->
            rule.category.direction == direction && rule.keywords.any { haystack.contains(it) }
        }?.let { rule ->
            val hit = rule.keywords.first { haystack.contains(it) }
            return Categorization(rule.category, isTransfer = false, reason = "Matched \"$hit\" in the message")
        }

        // 5. Fallback by direction.
        val fallback = Category.defaultFor(direction)
        return Categorization(fallback, isTransfer = false, reason = "No rule matched yet")
    }

    private fun matches(rule: CategoryRule, merchant: String, haystack: String): Boolean =
        when (rule.matchType) {
            CategoryRule.MatchType.MERCHANT -> merchant.equals(rule.value, ignoreCase = true)
            CategoryRule.MatchType.KEYWORD -> haystack.contains(rule.value.lowercase())
        }

    private fun isSelfTransfer(
        haystack: String,
        ownAccountTails: Set<String>,
        counterpartyTail: String?,
    ): Boolean {
        if (TRANSFER_KEYWORDS.any { haystack.contains(it) }) return true
        if (counterpartyTail != null && counterpartyTail in ownAccountTails) return true
        return false
    }

    /** Builds the rule that "always categorise X as Y" creates when the user edits an entry. */
    fun ruleForMerchant(id: String, merchant: String, category: Category): CategoryRule =
        CategoryRule(
            id = id,
            matchType = CategoryRule.MatchType.MERCHANT,
            value = merchant,
            categoryId = category.id,
            isUserDefined = true,
        )
}
