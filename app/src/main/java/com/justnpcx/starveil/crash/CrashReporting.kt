package com.justnpcx.starveil.crash

import android.app.Application
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.core.data.crash.CrashReportingSwitch
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.Message

/**
 * Sentry, configured to send as little as a crash report can get away with.
 *
 * Every option here is off by default at the call site rather than left to the
 * SDK's defaults: breadcrumbs would record which screens were open, sessions
 * would record usage times, and replay would record the screen. None of that is
 * needed to fix a crash, and all of it is data the user did not ask to send.
 *
 * A build with no DSN never calls [install] at all, so this is inert on a
 * developer machine — which also means a debug build cannot accidentally report
 * to the beta project.
 */
object CrashReporting {

    fun install(app: Application, preference: CrashReportingSwitch) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return

        SentryAndroid.init(app) { options ->
            options.dsn = dsn
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
            options.environment = if (BuildConfig.DEBUG) "debug" else "beta"

            // No user identity, no device hostname, no request. The device model
            // and OS version stay: a crash that only happens on one ROM is not
            // diagnosable without them.
            options.isSendDefaultPii = false
            options.serverName = null

            // Nothing that reconstructs what the user was doing.
            options.maxBreadcrumbs = 0
            options.isEnableAutoSessionTracking = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            options.tracesSampleRate = 0.0

            // The kernel is native code, so a crash in libbox is only visible
            // through the NDK integration. Symbolication needs a symbol upload,
            // which is not wired up yet — the stack is still reported, just with
            // addresses where names would be.
            options.isEnableNdk = true

            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                // Asked here rather than cached: this runs on Sentry's own
                // thread, and the preference must be the current value even if
                // it changed a second ago.
                if (preference.isEnabled()) redact(event) else null
            }
        }
    }
}

/**
 * Strips identifying strings from an event before it is queued.
 *
 * Message and exception text are the only places they appear, because nothing
 * else is collected — but both are scrubbed, since an exception message is
 * frequently the config line that failed.
 */
internal fun redact(event: SentryEvent): SentryEvent {
    event.message = event.message?.let { original ->
        val text = CrashRedaction.scrub(original.formatted ?: original.message.orEmpty())
        Message().apply {
            // Both fields: `message` is what gets grouped and displayed when
            // there are no params, `formatted` is what the SDK would have sent.
            message = text
            formatted = text
        }
    }
    event.exceptions?.forEach { exception ->
        exception.value = exception.value?.let(CrashRedaction::scrub)
    }
    event.request = null
    event.user = null
    event.serverName = null
    return event
}
