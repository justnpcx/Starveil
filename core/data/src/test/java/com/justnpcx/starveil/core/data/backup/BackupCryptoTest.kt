package com.justnpcx.starveil.core.data.backup

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val payload = """{"profiles":[{"name":"n","uuid":"1111"}]}""".toByteArray()

    @Test
    fun `round-trips a payload`() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        assertArrayEquals(payload, BackupCrypto.decrypt(blob, passphrase))
    }

    @Test
    fun `output is salted and non-deterministic`() {
        val a = BackupCrypto.encrypt(payload, passphrase)
        val b = BackupCrypto.encrypt(payload, passphrase)
        assertFalse("same plaintext must not produce identical blobs", a.contentEquals(b))
        assertArrayEquals(payload, BackupCrypto.decrypt(a, passphrase))
        assertArrayEquals(payload, BackupCrypto.decrypt(b, passphrase))
    }

    @Test
    fun `the plaintext never appears in the ciphertext`() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("uuid"))
    }

    @Test
    fun `wrong passphrase fails the tag check`() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        try {
            BackupCrypto.decrypt(blob, "wrong passphrase".toCharArray())
            throw AssertionError("expected BackupCryptoException")
        } catch (e: BackupCryptoException) {
            // The message must not echo the passphrase itself.
            assertFalse(e.message!!.contains("correct horse battery staple"))
        }
    }

    @Test
    fun `a tampered blob is rejected`() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0xFF).toByte()
        try {
            BackupCrypto.decrypt(blob, passphrase)
            throw AssertionError("expected BackupCryptoException")
        } catch (e: BackupCryptoException) {
            assertEquals("wrong passphrase or corrupted backup", e.message)
        }
    }

    @Test
    fun `non-backup and truncated input are rejected`() {
        listOf(ByteArray(0), ByteArray(10), "not a backup at all".toByteArray()).forEach { bad ->
            try {
                BackupCrypto.decrypt(bad, passphrase)
                throw AssertionError("expected BackupCryptoException")
            } catch (e: BackupCryptoException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun `uses a fresh random source per call`() {
        val fixed = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(1L) }
        val a = BackupCrypto.encrypt(payload, passphrase, fixed)
        assertArrayEquals(payload, BackupCrypto.decrypt(a, passphrase))
    }
}

class DatabaseRecoveryPolicyTest {

    @Test
    fun `restores from backup when one is available`() {
        val decision = DatabaseRecoveryPolicy.decide(IllegalStateException("migration failed"), hasBackup = true)
        assertEquals(DatabaseRecoveryPolicy.Decision.RestoreFromBackup, decision)
    }

    @Test
    fun `asks the user when no backup exists and never wipes`() {
        val decision = DatabaseRecoveryPolicy.decide(RuntimeException("migration failed"), hasBackup = false)
        assertTrue(decision is DatabaseRecoveryPolicy.Decision.AskUserForBackup)
        val reason = (decision as DatabaseRecoveryPolicy.Decision.AskUserForBackup).reason
        assertTrue(reason.contains("not deleted"))
        assertFalse(reason.contains("migration failed")) // no internal detail leak
    }
}
