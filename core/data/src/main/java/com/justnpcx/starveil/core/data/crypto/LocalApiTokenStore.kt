package com.justnpcx.starveil.core.data.crypto

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * The bearer token for the local Clash-compatible API.
 *
 * An interface so the settings screen can be tested without the Android
 * Keystore, which only exists on a device.
 */
interface LocalApiTokenStore {
    /** The current token, generating one on first use. */
    fun token(): String

    /** Throw the current token away and return the new one. */
    fun reset(): String
}

/**
 * Generated once, from 32 bytes of `SecureRandom`, and stored exactly like the
 * database passphrase: wrapped by an AES-GCM key that lives in the Android
 * Keystore and cannot be exported, with only the ciphertext on disk. The token
 * is 43 URL-safe characters — long enough that guessing is not a threat model,
 * and short enough to paste into a dashboard.
 */
class KeystoreLocalApiTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : LocalApiTokenStore {

    private val secrets = KeystoreSecretStore(
        context = context,
        alias = KEY_ALIAS,
        file = File(context.filesDir, FILE_NAME),
    )

    override fun token(): String = encode(secrets.getOrCreatePassphrase())

    /** Revoke: the old token stops matching the kernel's `secret` immediately. */
    override fun reset(): String {
        secrets.reset()
        return token()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private companion object {
        // Separate key and file from the database passphrase: revoking one must
        // never touch the other, and a rotated token must not re-key the DB.
        const val KEY_ALIAS = "starveil.api.token.key"
        const val FILE_NAME = "starveil.api.token"
    }
}
