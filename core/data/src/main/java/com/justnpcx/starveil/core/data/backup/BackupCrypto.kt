package com.justnpcx.starveil.core.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.SCrypt

/**
 * Encrypted backup envelope: `scrypt` key derivation + AES-256-GCM.
 *
 * Layout: `"SVB" || version(1) || salt(16) || nonce(12) || ciphertext||tag(16)`.
 * The salt and nonce are random per backup, so encrypting the same data twice
 * never yields the same bytes. GCM gives integrity: a wrong passphrase or a
 * tampered file fails the tag check rather than decrypting to garbage.
 *
 * Pure Kotlin/Java (BouncyCastle for scrypt) so it is unit-testable on the JVM.
 * The passphrase is never turned into a String and never logged.
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 1)
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_LEN = TAG_BITS / 8
    private const val KEY_LEN = 32

    // scrypt work factors. A backup is a rare, interactive operation, so we can
    // afford a deliberately slow KDF (~32 MB, tens of ms).
    private const val N = 1 shl 15
    private const val R = 8
    private const val P = 1

    private const val AES_GCM = "AES/GCM/NoPadding"

    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + salt + nonce + ciphertext
    }

    /** @throws BackupCryptoException on a malformed, tampered or wrong-passphrase blob. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        val header = MAGIC.size + SALT_LEN + NONCE_LEN + TAG_LEN
        if (blob.size < header) throw BackupCryptoException("backup file is too short")
        if (!blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupCryptoException("not a Starveil backup file")
        }
        var offset = MAGIC.size
        val salt = blob.copyOfRange(offset, offset + SALT_LEN).also { offset += SALT_LEN }
        val nonce = blob.copyOfRange(offset, offset + NONCE_LEN).also { offset += NONCE_LEN }
        val ciphertext = blob.copyOfRange(offset, blob.size)

        val key = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Never surface the underlying exception: it can hint at the key.
            throw BackupCryptoException("wrong passphrase or corrupted backup")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray =
        SCrypt.generate(charsToBytes(passphrase), salt, N, R, P, KEY_LEN)

    /**
     * Low-order-byte conversion, deliberately not `String(chars).toByteArray()`:
     * that would leave an immutable copy of the passphrase on the heap.
     * Passphrases are expected to be ASCII (they are machine-generated).
     */
    private fun charsToBytes(chars: CharArray): ByteArray =
        ByteArray(chars.size) { i -> chars[i].code.toByte() }
}

/** A backup could not be decrypted or is not a Starveil backup. Carries no key material. */
class BackupCryptoException(message: String) : IllegalArgumentException(message)
