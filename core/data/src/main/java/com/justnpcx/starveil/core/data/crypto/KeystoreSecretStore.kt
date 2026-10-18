package com.justnpcx.starveil.core.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the SQLCipher passphrase for the app database.
 *
 * The passphrase is 32 random bytes, generated once, wrapped with an AES-GCM
 * key held in the Android Keystore (non-exportable), and stored as ciphertext
 * in the app's private files. The raw passphrase never touches disk, is never
 * hardcoded, and is never logged (README "安全红线").
 *
 * Keystore + GCM is Android-only, so this class is exercised by instrumentation
 * (or manual smoke), not JVM unit tests.
 */
class KeystoreSecretStore(
    private val context: Context,
    private val alias: String = KEY_ALIAS,
    private val file: File = File(context.filesDir, FILE_NAME),
) {

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        if (file.exists()) {
            return unwrap(file.readBytes())
        }
        val fresh = ByteArray(PASSPHRASE_LEN).also { SecureRandom().nextBytes(it) }
        file.writeBytes(wrap(fresh))
        return fresh
    }

    /**
     * Throw the stored secret away; the next read makes a new one.
     *
     * Used when a token has to be revoked — an old dashboard still holding it
     * must stop working. The Keystore key stays: it protects whatever comes
     * next, and deleting it would invalidate nothing else that matters.
     */
    @Synchronized
    fun reset() {
        file.delete()
    }

    private fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        return cipher.iv + cipher.doFinal(secret)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "wrapped passphrase is truncated" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ciphertext = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "starveil.db.passphrase.key"
        const val FILE_NAME = "starveil.db.passphrase"
        const val PASSPHRASE_LEN = 32
        const val IV_LEN = 12
        const val TAG_BITS = 128
    }
}
