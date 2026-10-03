package com.karan.anuj.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the secret the encrypted database is opened with. */
fun interface DatabasePassphraseProvider {
    /** Reads from disk and the keystore, so it must not be called on the main thread. */
    fun passphrase(): ByteArray
}

/**
 * Keeps the database passphrase as a random value that never leaves the phone.
 *
 * The passphrase is generated once, encrypted with a key held in the Android
 * Keystore, and stored in the app's no-backup folder. The keystore key does not
 * require the user to be present, because reminders must be able to read the
 * database while the phone is locked and the app is closed.
 *
 * File layout: one byte holding the IV length, the IV, then the ciphertext.
 */
@Singleton
class KeystoreDatabasePassphraseProvider @Inject constructor(
    @ApplicationContext context: Context,
) : DatabasePassphraseProvider {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    @Synchronized
    override fun passphrase(): ByteArray =
        if (file.exists()) decrypt(file.readBytes()) else createAndStore()

    private fun createAndStore(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(passphrase)

        /**
         * Written to a temporary file and renamed, so a crash mid-write cannot
         * leave a half-written key that would make the database unreadable.
         */
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        check(temp.renameTo(file)) { "Could not store the database key" }
        return passphrase
    }

    private fun decrypt(stored: ByteArray): ByteArray {
        val ivSize = stored[0].toInt()
        val iv = stored.copyOfRange(1, 1 + ivSize)
        val encrypted = stored.copyOfRange(1 + ivSize, stored.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(encrypted)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val FILE_NAME = "database.key"
        const val KEY_ALIAS = "anuj_database_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
