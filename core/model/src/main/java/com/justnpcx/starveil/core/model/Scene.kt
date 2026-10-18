package com.justnpcx.starveil.core.model

/**
 * A scene: "when the network looks like this, use that policy".
 *
 * The model is deliberately small — a trigger, a preset, and optionally a node
 * — because the value is in being predictable. A scene that can do anything is
 * a rule engine, and a rule engine is a thing the user has to debug at the
 * worst possible moment (on a train, with no signal).
 */
data class Scene(
    val id: Long,
    val name: String,
    val trigger: SceneTrigger,
    val preset: RoutePreset,
    /**
     * Where to point the tunnel, or null to keep whatever is selected.
     *
     * A scene that names a node outranks the "switch node on drop" fallback:
     * one is a decision the user made in advance, the other is a guess made
     * after a failure.
     */
    val target: SceneTarget? = null,
)

/** What makes a scene apply. */
sealed interface SceneTrigger {
    /**
     * One of these Wi-Fi names.
     *
     * An empty list means "any Wi-Fi", which is what a build without location
     * permission falls back to: the SSID cannot be read, but the transport can.
     */
    data class Wifi(val ssids: List<String> = emptyList()) : SceneTrigger

    /** Mobile data: any cellular network, whatever the carrier. */
    data object Mobile : SceneTrigger

    /** Whether this trigger can be evaluated at all right now. */
    fun canMatch(ssidReadable: Boolean): Boolean = when (this) {
        is Wifi -> ssids.isEmpty() || ssidReadable
        Mobile -> true
    }
}

/** The node or group a scene selects, if it names one. */
sealed interface SceneTarget {
    data class Node(val profileId: Long) : SceneTarget
    data class Group(val groupId: Long) : SceneTarget
}

/** The network as the matcher sees it. */
enum class NetworkTransport { WIFI, CELLULAR, OTHER }

data class NetworkState(
    val transport: NetworkTransport,
    /** The SSID when it can be read, null otherwise. */
    val ssid: String? = null,
)

/**
 * Which scene applies to a network state.
 *
 * First match wins, in the order the user arranged them: two scenes can both
 * be true (a Wi-Fi scene and a "mobile data" one cannot, but two Wi-Fi scenes
 * with overlapping names can), and the answer has to be the one the user put
 * first rather than the one that happens to sort first.
 */
object SceneMatcher {

    fun match(scenes: List<Scene>, state: NetworkState, ssidReadable: Boolean = true): Scene? =
        scenes.firstOrNull { it.trigger.matches(state, ssidReadable) }
}

/** Whether this trigger fires for [state]. */
fun SceneTrigger.matches(state: NetworkState, ssidReadable: Boolean = true): Boolean =
    when (this) {
        SceneTrigger.Mobile -> state.transport == NetworkTransport.CELLULAR
        is SceneTrigger.Wifi -> when (state.transport) {
            NetworkTransport.WIFI -> when {
                // No names configured: any Wi-Fi, which is the degraded mode.
                ssids.isEmpty() -> true
                // Names configured but unreadable: the trigger cannot be
                // evaluated, so it does not fire. Firing "any Wi-Fi" here would
                // silently turn a home-only scene into an everywhere scene.
                state.ssid == null -> false
                else -> ssids.any { it.equals(state.ssid, ignoreCase = true) }
            }

            else -> false
        }
    }

/** Timing rules for automatic switching, as constants so they can be tested. */
object SceneTiming {
    /** Several network changes inside this window are one change. */
    const val DEBOUNCE_MS = 5_000L

    /**
     * After a manual preset change, scenes keep out of the way for this long.
     *
     * The user overriding a scene is a statement about right now — "I know it
     * says home, but I want the proxy" — and re-applying the scene a second
     * later would be an argument with them.
     */
    const val MANUAL_GRACE_MS = 30 * 60 * 1000L

    /** Whether a scene may apply, given when the user last chose a preset. */
    fun allowsAutomatic(nowMs: Long, lastManualAtMs: Long): Boolean =
        nowMs - lastManualAtMs >= MANUAL_GRACE_MS
}
