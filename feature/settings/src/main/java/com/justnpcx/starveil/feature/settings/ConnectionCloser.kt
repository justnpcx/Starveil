package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.kernel.KernelBridge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two close actions, in one place.
 *
 * Kept out of the view model so the mapping — id in, kernel call out, success
 * reported honestly — can be tested without a connection table, a database or
 * an Android context. The view model turns a false into a Snackbar; nothing
 * else happens here.
 */
@Singleton
class ConnectionCloser @Inject constructor(
    private val kernel: KernelBridge,
) {
    /** False when the kernel refused, or when there is no id to close. */
    suspend fun close(id: String): Boolean {
        if (id.isBlank()) return false
        return kernel.closeConnection(id).isSuccess
    }

    suspend fun closeAll(): Boolean = kernel.closeConnections().isSuccess
}
