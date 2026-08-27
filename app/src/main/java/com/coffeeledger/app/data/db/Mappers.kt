package com.coffeeledger.app.data.db

import com.coffeeledger.app.data.db.entity.AccountEntity
import com.coffeeledger.app.data.db.entity.CategoryRuleEntity
import com.coffeeledger.app.data.db.entity.InsightEntity
import com.coffeeledger.app.data.db.entity.TrackerEntity
import com.coffeeledger.app.data.db.entity.TransactionEntity
import com.coffeeledger.app.domain.analytics.Insight
import com.coffeeledger.app.domain.analytics.InsightKind
import com.coffeeledger.app.domain.analytics.InsightTone
import com.coffeeledger.app.domain.categorize.CategoryRule
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.AccountType
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.CategorySource
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerPeriod
import com.coffeeledger.app.domain.model.Txn

/**
 * Translation between stored rows and domain values.
 *
 * Enums are stored by name and read back defensively: a row written by a newer version of
 * the app must never crash an older one, so an unknown name falls back rather than throws.
 */

fun TransactionEntity.toDomain(): Txn = Txn(
    id = id,
    occurredAt = occurredAt,
    amountMinor = amountMinor,
    direction = enumOrDefault(direction, Direction.DEBIT),
    merchant = merchant,
    merchantRaw = merchantRaw,
    category = Category.fromId(categoryId) ?: Category.defaultFor(enumOrDefault(direction, Direction.DEBIT)),
    categorySource = enumOrDefault(categorySource, CategorySource.AUTO),
    accountId = accountId,
    accountTail = accountTail,
    sourceType = enumOrDefault(sourceType, SourceType.MANUAL),
    sourceApp = sourceApp,
    paymentMethod = enumOrDefault(paymentMethod, PaymentMethod.UNKNOWN),
    reference = reference,
    notes = notes,
    isTransfer = isTransfer,
    needsReview = needsReview,
)

fun Txn.toEntity(
    needsReview: Boolean = this.needsReview,
    confidence: Float = 1f,
    rawMessage: String? = null,
    smsSender: String? = null,
    dedupeKey: String = defaultDedupeKey(),
    createdAt: Long = System.currentTimeMillis(),
): TransactionEntity = TransactionEntity(
    id = id,
    occurredAt = occurredAt,
    amountMinor = amountMinor,
    direction = direction.name,
    merchant = merchant,
    merchantRaw = merchantRaw,
    categoryId = category.id,
    categorySource = categorySource.name,
    accountId = accountId,
    accountTail = accountTail,
    sourceType = sourceType.name,
    sourceApp = sourceApp,
    paymentMethod = paymentMethod.name,
    reference = reference,
    notes = notes,
    isTransfer = isTransfer,
    needsReview = needsReview,
    confidence = confidence,
    rawMessage = rawMessage,
    smsSender = smsSender,
    dedupeKey = dedupeKey,
    createdAt = createdAt,
)

/**
 * Identity for de-duplication. A bank often sends the same transaction twice, and a full
 * inbox re-scan sees every old message again, so the key is built from what actually
 * identifies a payment rather than from the message text.
 */
fun Txn.defaultDedupeKey(): String {
    val reference = reference?.takeIf { it.isNotBlank() }
    return if (reference != null) {
        "ref:$reference:$amountMinor:${direction.name}"
    } else {
        // Same amount, same merchant, same minute is one transaction, not two.
        "gen:${occurredAt / 60_000}:$amountMinor:${direction.name}:${merchant.lowercase()}"
    }
}

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    displayName = displayName,
    institution = institution,
    tail = tail,
    type = enumOrDefault(type, AccountType.BANK),
    openingBalanceMinor = openingBalanceMinor,
    includeInTotals = includeInTotals,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    displayName = displayName,
    institution = institution,
    tail = tail,
    type = type.name,
    openingBalanceMinor = openingBalanceMinor,
    includeInTotals = includeInTotals,
)

fun TrackerEntity.toDomain(): Tracker = Tracker(
    id = id,
    title = title,
    kind = enumOrDefault(kind, TrackerKind.SPENDING_LIMIT),
    period = enumOrDefault(period, TrackerPeriod.MONTHLY),
    targetMinor = targetMinor,
    categoryIds = categoryIds.splitCsv(),
    merchantNames = merchantNames.splitCsv(),
    accountIds = accountIds.splitCsv(),
    manualProgressMinor = manualProgressMinor,
    sortOrder = sortOrder,
    archived = archived,
)

fun Tracker.toEntity(): TrackerEntity = TrackerEntity(
    id = id,
    title = title,
    kind = kind.name,
    period = period.name,
    targetMinor = targetMinor,
    categoryIds = categoryIds.joinToString(","),
    merchantNames = merchantNames.joinToString(","),
    accountIds = accountIds.joinToString(","),
    manualProgressMinor = manualProgressMinor,
    sortOrder = sortOrder,
    archived = archived,
)

fun CategoryRuleEntity.toDomain(): CategoryRule = CategoryRule(
    id = id,
    matchType = enumOrDefault(matchType, CategoryRule.MatchType.MERCHANT),
    value = value,
    categoryId = categoryId,
    isUserDefined = isUserDefined,
)

fun CategoryRule.toEntity(createdAt: Long = System.currentTimeMillis()): CategoryRuleEntity =
    CategoryRuleEntity(
        id = id,
        matchType = matchType.name,
        value = value,
        categoryId = categoryId,
        isUserDefined = isUserDefined,
        createdAt = createdAt,
    )

fun InsightEntity.toDomain(): Insight = Insight(
    id = id,
    kind = enumOrDefault(kind, InsightKind.CASH_FLOW),
    tone = enumOrDefault(tone, InsightTone.NEUTRAL),
    title = title,
    evidence = evidence,
    priority = priority,
)

fun Insight.toEntity(generatedAt: Long = System.currentTimeMillis()): InsightEntity = InsightEntity(
    id = id,
    kind = kind.name,
    tone = tone.name,
    title = title,
    evidence = evidence,
    priority = priority,
    generatedAt = generatedAt,
    dismissed = false,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private fun String.splitCsv(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }
