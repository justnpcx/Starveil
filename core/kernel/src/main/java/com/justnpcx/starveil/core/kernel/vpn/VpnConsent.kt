package com.justnpcx.starveil.core.kernel.vpn

import android.content.Context
import android.content.Intent

/**
 * Narrow port for the one thing the UI must do with the VPN layer: ask the OS
 * for consent.
 *
 * Deliberately **not** part of `KernelBridge` (which must not change), and
 * deliberately not exposing sing-box, JNI or process management — it only
 * hands back the system consent intent. The app is the only module that uses
 * it, and it never touches [StarveilVpnService] directly.
 *
 * Sanctioned exception to the "no kernel dependency outside :core:kernel"
 * rule; rationale recorded in docs/06-vpn-consent-port.md.
 */
fun interface VpnConsent {
    /** Null when consent is already granted; otherwise the intent to launch. */
    fun consentIntent(): Intent?
}

/** The only handle the app needs: a [VpnConsent] bound to this context. */
fun starveilVpnConsent(context: Context): VpnConsent =
    VpnConsent { StarveilVpnService.consentIntent(context) }
