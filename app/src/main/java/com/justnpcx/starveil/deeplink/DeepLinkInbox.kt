package com.justnpcx.starveil.deeplink

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One share link, handed from the Activity to whichever screen can import it.
 *
 * A single slot rather than a queue: a second link arriving before the first is
 * consumed is either the same intent delivered twice (a configuration change)
 * or a user who tapped another link in the meantime, and in both cases the
 * newest is the one they meant.
 *
 * It exists because the two ends start at different times. The intent arrives
 * in `onCreate`, before there is a screen, and the screens that can import are
 * behind onboarding — so the link has to wait somewhere that outlives both.
 *
 * A scheme arrives from outside the app and is therefore untrusted. Validation
 * happens here, once, against the same list the registry uses to parse: an
 * unregistered scheme is dropped at the door rather than handed to a parser
 * that would only report it as unparseable later.
 */
@Singleton
class DeepLinkInbox @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)

    /** The link waiting to be imported, or null. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * Offer a link from an intent.
     *
     * Returns whether it was accepted, which is what the caller logs. The link
     * itself is never logged: it carries the node's credentials.
     */
    fun offer(raw: String?): Boolean {
        val link = raw?.trim().orEmpty()
        if (link.isEmpty()) return false
        val scheme = link.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme.isEmpty()) return false
        if (scheme !in SUPPORTED_SCHEMES) return false
        _pending.value = link
        return true
    }

    /**
     * Take the link, if there is one.
     *
     * Clearing on read is what makes a configuration change safe: the Activity
     * is recreated and reads the same intent again, and without this the same
     * node would be imported twice.
     */
    fun consume(): String? {
        val link = _pending.value ?: return null
        _pending.value = null
        return link
    }

    /** Whether anything is waiting, for the screen to decide to act. */
    val hasPending: Boolean get() = _pending.value != null

    companion object {
        /**
         * The schemes this app can parse, and only those.
         *
         * `http` and `https` are deliberately absent: a subscription URL is
         * one, and registering them would put this app in the chooser every
         * time someone taps a link in a browser. A subscription is pasted, not
         * tapped.
         */
        val SUPPORTED_SCHEMES = setOf(
            "ss",
            "vless",
            "vmess",
            "trojan",
            "hysteria2",
            "hy2",
            "tuic",
            "wireguard",
        )
    }
}