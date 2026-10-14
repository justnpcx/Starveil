package com.justnpcx.starveil.core.kernel

/**
 * Process-wide sink for kernel diagnostics.
 *
 * Most diagnostics live on the platform interface, which can be handed a
 * callback. The VpnService cannot: it is instantiated by the system, so it has
 * nowhere to receive one. This gives both the same destination — the log ring
 * behind the Logs screen — which is what makes the teardown path observable
 * without adb logcat (unreliable on the test device).
 *
 * [sink] is set once, by RealKernelBridge.
 */
object KernelDiagnostics {

    private const val TAG = "StarveilKernel"

    @Volatile
    var sink: ((String) -> Unit)? = null

    fun log(message: String) {
        android.util.Log.i(TAG, message)
        sink?.invoke(message)
    }
}
