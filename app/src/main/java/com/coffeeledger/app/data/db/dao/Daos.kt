package com.coffeeledger.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.coffeeledger.app.data.db.entity.AccountEntity
import com.coffeeledger.app.data.db.entity.CategoryRuleEntity
import com.coffeeledger.app.data.db.entity.InsightEntity
import com.coffeeledger.app.data.db.entity.MerchantRuleEntity
import com.coffeeledger.app.data.db.entity.PreferenceEntity
import com.coffeeledger.app.data.db.entity.TrackerEntity
import com.coffeeledger.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT dedupeKey FROM transactions")
    suspend fun allDedupeKeys(): List<String>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    /** Ignores rows whose dedupe key already exists, which is how a re-scan stays idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(transactions: List<TransactionEntity>): List<Long>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY displayName")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY sortOrder")
    fun observeAll(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers")
    suspend fun all(): List<TrackerEntity>

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun byId(id: String): TrackerEntity?

    @Upsert
    suspend fun upsert(tracker: TrackerEntity)

    @Upsert
    suspend fun upsertAll(trackers: List<TrackerEntity>)

    @Query("DELETE FROM trackers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM trackers")
    suspend fun deleteAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM category_rules ORDER BY createdAt DESC")
    fun observeCategoryRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun categoryRules(): List<CategoryRuleEntity>

    @Upsert
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteCategoryRule(id: String)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAllCategoryRules()

    @Query("SELECT * FROM merchant_rules")
    suspend fun merchantRules(): List<MerchantRuleEntity>

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAllMerchantRules()
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM insights WHERE dismissed = 0 ORDER BY priority DESC")
    fun observeActive(): Flow<List<InsightEntity>>

    @Query("SELECT id FROM insights WHERE dismissed = 1")
    suspend fun dismissedIds(): List<String>

    @Query("UPDATE insights SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM insights WHERE dismissed = 0")
    suspend fun clearActive()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(insights: List<InsightEntity>)

    @Transaction
    suspend fun replaceActive(insights: List<InsightEntity>) {
        clearActive()
        insertAll(insights)
    }

    @Query("DELETE FROM insights")
    suspend fun deleteAll()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT value FROM preferences WHERE key = :key")
    suspend fun value(key: String): String?

    @Upsert
    suspend fun put(preference: PreferenceEntity)

    @Query("DELETE FROM preferences")
    suspend fun deleteAll()
}
