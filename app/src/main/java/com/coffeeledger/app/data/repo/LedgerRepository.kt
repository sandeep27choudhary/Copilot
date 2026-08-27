package com.coffeeledger.app.data.repo

import com.coffeeledger.app.data.db.AppDatabase
import com.coffeeledger.app.data.db.defaultDedupeKey
import com.coffeeledger.app.data.db.toDomain
import com.coffeeledger.app.data.db.toEntity
import com.coffeeledger.app.data.sample.SampleData
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.analytics.Insight
import com.coffeeledger.app.domain.analytics.InsightGenerator
import com.coffeeledger.app.domain.categorize.CategoryRule
import com.coffeeledger.app.domain.categorize.Categorizer
import com.coffeeledger.app.domain.importer.ImportedRow
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.CategorySource
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.normalize.MerchantNormalizer
import com.coffeeledger.app.domain.parse.ParsedTransaction
import com.coffeeledger.app.domain.parse.SmsTransactionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** Everything the screens read, resolved together so totals and trackers never disagree. */
data class LedgerSnapshot(
    val transactions: List<Txn>,
    val accounts: List<Account>,
    val trackers: List<Tracker>,
    val rules: List<CategoryRule>,
) {
    val openingBalanceMinor: Long
        get() = accounts.filter { it.includeInTotals }.sumOf { it.openingBalanceMinor }

    val totalBalanceMinor: Long
        get() = AnalyticsEngine.totalBalance(transactions.filter { includesAccount(it.accountId) }, openingBalanceMinor)

    private fun includesAccount(accountId: String?): Boolean {
        if (accountId == null) return true
        return accounts.firstOrNull { it.id == accountId }?.includeInTotals ?: true
    }

    val ownAccountTails: Set<String>
        get() = accounts.mapNotNull { it.tail }.toSet()
}

/**
 * The single door to stored financial data.
 *
 * Nothing in this class reaches the network. Ingestion, categorisation and insight
 * generation all run against the local encrypted database.
 */
class LedgerRepository(private val db: AppDatabase) {

    fun observeSnapshot(): Flow<LedgerSnapshot> = combine(
        db.transactionDao().observeAll(),
        db.accountDao().observeAll(),
        db.trackerDao().observeAll(),
        db.ruleDao().observeCategoryRules(),
    ) { transactions, accounts, trackers, rules ->
        LedgerSnapshot(
            transactions = transactions.map { it.toDomain() },
            accounts = accounts.map { it.toDomain() },
            trackers = trackers.map { it.toDomain() },
            rules = rules.map { it.toDomain() },
        )
    }

    suspend fun transactionCount(): Int = db.transactionDao().count()

    suspend fun transaction(id: String): Txn? = db.transactionDao().byId(id)?.toDomain()

    /** The original message a parsed entry came from, shown only in the detail screen. */
    suspend fun rawMessage(id: String): String? = db.transactionDao().byId(id)?.rawMessage

    // ------------------------------------------------------------ ingest

    /**
     * Converts parsed messages into ledger entries and stores the ones not already present.
     * @return how many new transactions were added.
     */
    suspend fun ingestParsed(parsed: List<ParsedTransaction>): Int {
        if (parsed.isEmpty()) return 0
        val accounts = db.accountDao().all().map { it.toDomain() }
        val rules = db.ruleDao().categoryRules().map { it.toDomain() }
        val ownTails = accounts.mapNotNull { it.tail }.toSet()

        val entities = parsed.map { item ->
            val categorization = Categorizer.categorize(
                merchantRaw = item.merchantRaw,
                direction = item.direction,
                rawText = item.rawBody,
                userRules = rules,
                ownAccountTails = ownTails,
                counterpartyTail = counterpartyTail(item, ownTails),
            )
            val account = accounts.firstOrNull { it.tail != null && it.tail == item.accountTail }
            val txn = Txn(
                id = UUID.randomUUID().toString(),
                occurredAt = item.occurredAt,
                amountMinor = item.amountMinor,
                direction = item.direction,
                merchant = MerchantNormalizer.normalize(item.merchantRaw)
                    .takeUnless { it == "Unknown" } ?: (item.institution ?: "Unknown"),
                merchantRaw = item.merchantRaw ?: item.institution.orEmpty(),
                category = categorization.category,
                categorySource = CategorySource.AUTO,
                accountId = account?.id,
                accountTail = item.accountTail,
                sourceType = SourceType.SMS,
                sourceApp = item.institution ?: item.sender,
                paymentMethod = item.paymentMethod,
                reference = item.reference,
                isTransfer = categorization.isTransfer,
            )
            txn.toEntity(
                needsReview = item.confidence <= SmsTransactionParser.REVIEW_THRESHOLD,
                confidence = item.confidence,
                rawMessage = item.rawBody,
                smsSender = item.sender,
            )
        }
        val inserted = db.transactionDao().insertIgnoringDuplicates(entities)
        return inserted.count { it >= 0 }
    }

    /** Stores rows read from a CSV or PDF statement against the account the user chose. */
    suspend fun ingestImported(
        rows: List<ImportedRow>,
        accountId: String?,
        sourceType: SourceType,
        sourceApp: String,
    ): Int {
        if (rows.isEmpty()) return 0
        val accounts = db.accountDao().all().map { it.toDomain() }
        val rules = db.ruleDao().categoryRules().map { it.toDomain() }
        val ownTails = accounts.mapNotNull { it.tail }.toSet()
        val account = accounts.firstOrNull { it.id == accountId }

        val entities = rows.map { row ->
            val categorization = Categorizer.categorize(
                merchantRaw = row.description,
                direction = row.direction,
                rawText = row.description,
                userRules = rules,
                ownAccountTails = ownTails,
            )
            Txn(
                id = UUID.randomUUID().toString(),
                occurredAt = row.occurredAt,
                amountMinor = row.amountMinor,
                direction = row.direction,
                merchant = MerchantNormalizer.normalize(row.description),
                merchantRaw = row.description,
                category = categorization.category,
                accountId = account?.id,
                accountTail = account?.tail,
                sourceType = sourceType,
                sourceApp = sourceApp,
                paymentMethod = PaymentMethod.UNKNOWN,
                reference = row.reference,
                isTransfer = categorization.isTransfer,
            ).toEntity(rawMessage = row.description)
        }
        return db.transactionDao().insertIgnoringDuplicates(entities).count { it >= 0 }
    }

    suspend fun addManual(txn: Txn) {
        db.transactionDao().upsert(
            txn.toEntity(dedupeKey = "manual:${txn.id}"),
        )
    }

    // ------------------------------------------------------------- edits

    /**
     * Saves an edited transaction. When the user changes the category, the change is also
     * remembered as a rule so the same merchant lands correctly next time.
     */
    suspend fun updateTransaction(updated: Txn, learnCategoryRule: Boolean) {
        val existing = db.transactionDao().byId(updated.id)
        db.transactionDao().upsert(
            updated.copy(categorySource = CategorySource.USER, needsReview = false).toEntity(
                needsReview = false,
                confidence = existing?.confidence ?: 1f,
                rawMessage = existing?.rawMessage,
                smsSender = existing?.smsSender,
                dedupeKey = existing?.dedupeKey ?: updated.defaultDedupeKey(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        if (learnCategoryRule && updated.merchant.isNotBlank()) {
            db.ruleDao().upsert(
                Categorizer.ruleForMerchant(
                    id = "rule-${updated.merchant.lowercase()}",
                    merchant = updated.merchant,
                    category = updated.category,
                ).toEntity(),
            )
        }
    }

    suspend fun deleteTransaction(id: String) = db.transactionDao().delete(id)

    suspend fun categoryRules(): List<CategoryRule> =
        db.ruleDao().categoryRules().map { it.toDomain() }

    suspend fun deleteCategoryRule(id: String) = db.ruleDao().deleteCategoryRule(id)

    // ---------------------------------------------------------- trackers

    suspend fun saveTracker(tracker: Tracker) = db.trackerDao().upsert(tracker.toEntity())

    suspend fun deleteTracker(id: String) = db.trackerDao().delete(id)

    suspend fun nextTrackerSortOrder(): Int = (db.trackerDao().all().maxOfOrNull { it.sortOrder } ?: -1) + 1

    // ---------------------------------------------------------- accounts

    suspend fun saveAccount(account: Account) = db.accountDao().upsert(account.toEntity())

    suspend fun deleteAccount(id: String) = db.accountDao().delete(id)

    // ---------------------------------------------------------- insights

    /** Recomputes insights from local data and caches them for the Insights screen. */
    suspend fun refreshInsights(snapshot: LedgerSnapshot, now: Long): List<Insight> {
        val progress = AnalyticsEngine.progressOf(snapshot.trackers, snapshot.transactions, now)
        val generated = InsightGenerator.generate(snapshot.transactions, progress, now)
        val dismissed = db.insightDao().dismissedIds().toSet()
        val kept = generated.filterNot { it.id in dismissed }
        db.insightDao().replaceActive(kept.map { it.toEntity(now) })
        return kept
    }

    suspend fun dismissInsight(id: String) = db.insightDao().dismiss(id)

    // ------------------------------------------------------------- seed

    /** Seeds the sample ledger, but only into an empty database. */
    suspend fun seedSampleDataIfEmpty(now: Long): Boolean {
        if (db.transactionDao().count() > 0) return false
        db.accountDao().upsertAll(SampleData.accounts().map { it.toEntity() })
        db.trackerDao().upsertAll(SampleData.trackers().map { it.toEntity() })
        db.transactionDao().insertIgnoringDuplicates(
            SampleData.transactions(now).map { it.toEntity(dedupeKey = "sample:${it.id}") },
        )
        return true
    }

    /** Removes the sample rows without touching anything the user added themselves. */
    suspend fun removeSampleData(): Int {
        val samples = db.transactionDao().all().filter { it.dedupeKey.startsWith("sample:") }
        samples.forEach { db.transactionDao().delete(it.id) }
        return samples.size
    }

    // ----------------------------------------------------------- erasure

    /** Wipes every table. The caller is responsible for the key material. */
    suspend fun deleteAllData() {
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()
        db.trackerDao().deleteAll()
        db.ruleDao().deleteAllCategoryRules()
        db.ruleDao().deleteAllMerchantRules()
        db.insightDao().deleteAll()
        db.preferenceDao().deleteAll()
    }

    private fun counterpartyTail(item: ParsedTransaction, ownTails: Set<String>): String? {
        // A message naming two of the user's own accounts is an internal transfer.
        val digits = Regex("""\d{4,6}""").findAll(item.rawBody).map { it.value.takeLast(4) }.toList()
        return digits.firstOrNull { it != item.accountTail && it in ownTails }
    }

    /** Builds the empty-state defaults used when a user declines the sample ledger. */
    suspend fun ensureStarterTrackers() {
        if (db.trackerDao().all().isNotEmpty()) return
        db.trackerDao().upsertAll(
            listOf(
                Tracker(
                    id = UUID.randomUUID().toString(),
                    title = "Monthly spending",
                    kind = com.coffeeledger.app.domain.model.TrackerKind.SPENDING_LIMIT,
                    period = com.coffeeledger.app.domain.model.TrackerPeriod.MONTHLY,
                    targetMinor = 1_00_000_00L,
                    sortOrder = 0,
                ).toEntity(),
            ),
        )
    }

    /** Categories offered when the user edits an entry, in the direction that makes sense. */
    fun categoriesFor(direction: Direction): List<Category> =
        if (direction == Direction.CREDIT) Category.credits() else Category.debits()
}
