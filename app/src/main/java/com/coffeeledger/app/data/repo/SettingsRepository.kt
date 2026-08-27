package com.coffeeledger.app.data.repo

import com.coffeeledger.app.data.db.AppDatabase
import com.coffeeledger.app.data.db.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences. Small, typed, and stored in the same encrypted database. */
data class Settings(
    val smsIngestionEnabled: Boolean = false,
    val sampleDataPresent: Boolean = true,
    val advisorDisclaimerSeen: Boolean = false,
    val monthlyBudgetMinor: Long = 1_00_000_00L,
    val lastSmsScanAt: Long = 0L,
)

class SettingsRepository(private val db: AppDatabase) {

    fun observe(): Flow<Settings> = db.preferenceDao().observeAll().map { rows ->
        val map = rows.associate { it.key to it.value }
        Settings(
            smsIngestionEnabled = map[KEY_SMS_ENABLED]?.toBooleanStrictOrNull() ?: false,
            sampleDataPresent = map[KEY_SAMPLE_PRESENT]?.toBooleanStrictOrNull() ?: true,
            advisorDisclaimerSeen = map[KEY_ADVISOR_SEEN]?.toBooleanStrictOrNull() ?: false,
            monthlyBudgetMinor = map[KEY_MONTHLY_BUDGET]?.toLongOrNull() ?: 1_00_000_00L,
            lastSmsScanAt = map[KEY_LAST_SCAN]?.toLongOrNull() ?: 0L,
        )
    }

    suspend fun setSmsIngestionEnabled(enabled: Boolean) = put(KEY_SMS_ENABLED, enabled.toString())

    suspend fun setSampleDataPresent(present: Boolean) = put(KEY_SAMPLE_PRESENT, present.toString())

    suspend fun setAdvisorDisclaimerSeen(seen: Boolean) = put(KEY_ADVISOR_SEEN, seen.toString())

    suspend fun setLastSmsScanAt(millis: Long) = put(KEY_LAST_SCAN, millis.toString())

    suspend fun isSmsIngestionEnabled(): Boolean =
        db.preferenceDao().value(KEY_SMS_ENABLED)?.toBooleanStrictOrNull() ?: false

    private suspend fun put(key: String, value: String) =
        db.preferenceDao().put(PreferenceEntity(key, value))

    private companion object {
        const val KEY_SMS_ENABLED = "sms_ingestion_enabled"
        const val KEY_SAMPLE_PRESENT = "sample_data_present"
        const val KEY_ADVISOR_SEEN = "advisor_disclaimer_seen"
        const val KEY_MONTHLY_BUDGET = "monthly_budget_minor"
        const val KEY_LAST_SCAN = "last_sms_scan_at"
    }
}
