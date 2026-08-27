package com.coffeeledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.coffeeledger.app.data.db.dao.AccountDao
import com.coffeeledger.app.data.db.dao.InsightDao
import com.coffeeledger.app.data.db.dao.PreferenceDao
import com.coffeeledger.app.data.db.dao.RuleDao
import com.coffeeledger.app.data.db.dao.TrackerDao
import com.coffeeledger.app.data.db.dao.TransactionDao
import com.coffeeledger.app.data.db.entity.AccountEntity
import com.coffeeledger.app.data.db.entity.CategoryRuleEntity
import com.coffeeledger.app.data.db.entity.InsightEntity
import com.coffeeledger.app.data.db.entity.MerchantRuleEntity
import com.coffeeledger.app.data.db.entity.PreferenceEntity
import com.coffeeledger.app.data.db.entity.TrackerEntity
import com.coffeeledger.app.data.db.entity.TransactionEntity
import com.coffeeledger.app.data.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        TrackerEntity::class,
        CategoryRuleEntity::class,
        MerchantRuleEntity::class,
        InsightEntity::class,
        PreferenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun trackerDao(): TrackerDao
    abstract fun ruleDao(): RuleDao
    abstract fun insightDao(): InsightDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        const val DATABASE_NAME = "coffee_ledger.db"

        /**
         * Opens the encrypted database. Every page on disk is encrypted by SQLCipher with a
         * passphrase that only the Android Keystore can unseal, so the file is inert if it
         * is copied off the device.
         */
        fun open(context: Context, keyManager: DatabaseKeyManager): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = keyManager.databasePassphrase()
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

        /** Removes the database files themselves, used by "delete all financial data". */
        fun deleteFiles(context: Context) {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                File(context.getDatabasePath(DATABASE_NAME).path + suffix).delete()
            }
        }
    }
}
