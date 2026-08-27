package com.coffeeledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The stored form of a ledger entry.
 *
 * [rawMessage] holds the original SMS text so the user can always see what a parsed entry
 * came from. It is written to the encrypted database and to nothing else.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["merchant"]),
        Index(value = ["categoryId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val occurredAt: Long,
    val amountMinor: Long,
    val direction: String,
    val merchant: String,
    val merchantRaw: String,
    val categoryId: String,
    val categorySource: String,
    val accountId: String?,
    val accountTail: String?,
    val sourceType: String,
    val sourceApp: String,
    val paymentMethod: String,
    val reference: String?,
    val notes: String?,
    val isTransfer: Boolean,
    val needsReview: Boolean,
    val confidence: Float,
    val rawMessage: String?,
    val smsSender: String?,
    val dedupeKey: String,
    val createdAt: Long,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val institution: String,
    val tail: String?,
    val type: String,
    val openingBalanceMinor: Long,
    val includeInTotals: Boolean,
)

@Entity(tableName = "trackers")
data class TrackerEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val period: String,
    val targetMinor: Long,
    /** Comma separated; trackers have at most a handful of filters each. */
    val categoryIds: String,
    val merchantNames: String,
    val accountIds: String,
    val manualProgressMinor: Long,
    val sortOrder: Int,
    val archived: Boolean,
)

@Entity(tableName = "category_rules", indices = [Index(value = ["value"])])
data class CategoryRuleEntity(
    @PrimaryKey val id: String,
    val matchType: String,
    val value: String,
    val categoryId: String,
    val isUserDefined: Boolean,
    val createdAt: Long,
)

/** A user-taught merchant name, layered on top of the built-in catalog. */
@Entity(tableName = "merchant_rules", indices = [Index(value = ["pattern"], unique = true)])
data class MerchantRuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val canonical: String,
    val createdAt: Long,
)

/** Cached insights, so the Insights screen opens instantly and reads the same on return. */
@Entity(tableName = "insights")
data class InsightEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val tone: String,
    val title: String,
    val evidence: String,
    val priority: Int,
    val generatedAt: Long,
    val dismissed: Boolean,
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)
