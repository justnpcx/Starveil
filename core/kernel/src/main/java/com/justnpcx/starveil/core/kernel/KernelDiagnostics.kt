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

    /**
     * Where a line goes, with its level. The bridge decides how a level is
     * drawn; this object only knows that something wants to say it.
     */
    @Volatile
    var sink: ((String, LogLevel) -> Unit)? = null

    /** Ordinary narration. */
    fun log(message: String) = emit(LogLevel.DEBUG, message)

    /**
     * A failure the user should be able to find in the Logs screen without
     * adb: the rule set that failed verification, a sweep that could not run.
     */
    fun logError(message: String) = emit(LogLevel.ERROR, message)

    private fun emit(level: LogLevel, message: String) {
        if (level == LogLevel.ERROR) {
            android.util.Log.e(TAG, message)
        } else {
            android.util.Log.i(TAG, message)
        }
        sink?.invoke(message, level)
    }
}
