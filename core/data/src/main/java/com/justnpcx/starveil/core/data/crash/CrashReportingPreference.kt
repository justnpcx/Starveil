package com.justnpcx.starveil.core.data.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The crash-reporting switch, in SharedPreferences rather than DataStore.
 *
 * This is the one setting that cannot wait for a Flow. Sentry asks whether to
 * send from its own executor, inside `beforeSend`, where there is no coroutine
 * to await a DataStore read — and a cached "on" that a background flow corrects
 * a moment later is exactly the wrong default for a privacy switch: the crash
 * it lets through is the one that happens during startup.
 *
 * `getBoolean` on this thread is a memory read once the file is loaded, so the
 * synchronous API is cheap here in a way it would not be for the settings that
 * drive the UI.
 *
 * Default on. A beta that never hears about its crashes is not a beta, and the
 * report is opt-out rather than opt-in for that reason — with the switch in
 * Settings and the contents listed in the privacy policy.
 */
@Singleton
class CrashReportingPreference @Inject constructor(
    @ApplicationContext context: Context,
) : CrashReportingSwitch {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val FILE = "crash_reporting"
        const val KEY_ENABLED = "enabled"
    }
}
