package com.coffeeledger.app.di

import android.content.Context
import com.coffeeledger.app.data.db.AppDatabase
import com.coffeeledger.app.data.repo.LedgerRepository
import com.coffeeledger.app.data.repo.SettingsRepository
import com.coffeeledger.app.data.security.DatabaseKeyManager
import com.coffeeledger.app.sms.SmsInboxReader

/**
 * Manual dependency wiring.
 *
 * The graph is small enough that a container beats a code-generating framework here, and it
 * keeps the build free of another annotation processor.
 */
class AppContainer(private val context: Context) {

    val keyManager: DatabaseKeyManager by lazy { DatabaseKeyManager(context) }

    val database: AppDatabase by lazy { AppDatabase.open(context, keyManager) }

    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database) }

    val smsInboxReader: SmsInboxReader by lazy { SmsInboxReader(context) }

    /**
     * Closes and erases the database along with the key that unlocks it, so the file left
     * on disk cannot be decrypted by anything afterwards.
     */
    fun destroyAllStorage() {
        if (database.isOpen) database.close()
        AppDatabase.deleteFiles(context)
        keyManager.destroy()
    }
}
