package com.coffeeledger.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the passphrase that unlocks the local database.
 *
 * The passphrase itself is 32 random bytes generated on first launch. It is never stored in
 * the clear: it is sealed with an AES-GCM key that lives inside the Android Keystore and
 * cannot be exported from it, so the sealed blob on disk is useless on any other device.
 */
class DatabaseKeyManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** @return the database passphrase, creating and sealing one on first use. */
    fun databasePassphrase(): ByteArray {
        prefs.getString(KEY_SEALED_PASSPHRASE, null)?.let { stored ->
            runCatching { unseal(stored) }.getOrNull()?.let { return it }
            // A key that can no longer decrypt means the Keystore entry was invalidated.
            // There is nothing to recover: the database is unreadable and must be rebuilt.
            prefs.edit().remove(KEY_SEALED_PASSPHRASE).apply()
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_SEALED_PASSPHRASE, seal(passphrase)).apply()
        return passphrase
    }

    /** True once a passphrase exists, which is also when the database has been created. */
    fun hasPassphrase(): Boolean = prefs.contains(KEY_SEALED_PASSPHRASE)

    /**
     * Drops the sealed passphrase and the Keystore key. After this the database file cannot
     * be decrypted by anyone, including this app, which is what "delete all data" must mean.
     */
    fun destroy() {
        prefs.edit().remove(KEY_SEALED_PASSPHRASE).apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    /** Describes the protection in place, for the privacy screen. */
    fun protectionSummary(): String = if (isStrongBoxBacked()) {
        "Database key held in hardware-backed Android Keystore (StrongBox)"
    } else {
        "Database key held in the Android Keystore"
    }

    private fun seal(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val ciphertext = cipher.doFinal(plaintext)
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun unseal(stored: String): ByteArray {
        val combined = Base64.decode(stored, Base64.NO_WRAP)
        require(combined.size > GCM_IV_BYTES) { "sealed passphrase is truncated" }
        val iv = combined.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun isStrongBoxBacked(): Boolean = runCatching {
        appContext.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
    }.getOrDefault(false)

    private companion object {
        const val PREFS_NAME = "coffee_ledger_keys"
        const val KEY_SEALED_PASSPHRASE = "sealed_db_passphrase"
        const val KEY_ALIAS = "coffee_ledger_db_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
    }
}
