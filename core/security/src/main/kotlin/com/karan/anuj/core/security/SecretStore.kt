package com.karan.anuj.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps small secrets (such as the backup password) on the phone so the user
 * is not asked for them again. Both functions read or write a file, so they
 * must not be called on the main thread.
 */
interface SecretStore {
    /** @param value null removes the secret */
    fun put(name: String, value: String?)

    fun get(name: String): String?
}

/**
 * Each secret is encrypted with a key held in the Android Keystore and saved
 * in the app's no-backup folder: one byte holding the IV length, the IV,
 * then the ciphertext. The key does not require the user to be present,
 * because a scheduled backup has to read the password while the app is closed.
 *
 * This uses its own keystore key rather than the database's, so changing or
 * clearing one can never affect the other.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {

    private val folder = context.noBackupFilesDir

    @Synchronized
    override fun put(name: String, value: String?) {
        val file = fileFor(name)
        if (value == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray())

        val temp = File(folder, "${file.name}.tmp")
        temp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        if (!temp.renameTo(file)) {
            /** Renaming onto an existing file fails on some file systems; replace it explicitly. */
            file.delete()
            check(temp.renameTo(file)) { "Could not store the secret" }
        }
    }

    @Synchronized
    override fun get(name: String): String? {
        val file = fileFor(name)
        if (!file.exists()) return null
        return try {
            val stored = file.readBytes()
            val ivSize = stored[0].toInt()
            val iv = stored.copyOfRange(1, 1 + ivSize)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(stored.copyOfRange(1 + ivSize, stored.size)).decodeToString()
        } catch (unreadable: Exception) {
            /** A secret that can no longer be decrypted (the keystore key was reset) is treated as not set. */
            null
        }
    }

    /** Only letters, digits and underscores from the name are used, so it can never point outside the folder. */
    private fun fileFor(name: String) = File(folder, "secret_" + name.filter { it.isLetterOrDigit() || it == '_' })

    private fun key(): SecretKey {
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
        const val KEY_ALIAS = "anuj_secret_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
