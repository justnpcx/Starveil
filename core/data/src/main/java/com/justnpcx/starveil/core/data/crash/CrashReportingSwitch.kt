package com.justnpcx.starveil.core.data.crash

/**
 * The one bit of state Sentry needs, and the only thing the app asks it about.
 *
 * An interface rather than the concrete preference so the settings view model
 * can be tested without an Android context — the value it holds is a decision
 * about privacy, which is worth a test that says so.
 */
interface CrashReportingSwitch {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
