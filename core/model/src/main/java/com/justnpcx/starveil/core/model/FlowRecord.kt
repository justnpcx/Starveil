package com.justnpcx.starveil.core.model

/**
 * One routing decision the kernel actually made.
 *
 * Read from the kernel's connection tracker, never derived from the configured
 * rules: a diagram built from the rule list would answer "where was this
 * traffic supposed to go", and the question the flow view exists to answer is
 * "where did it go". Those are different answers exactly when something is
 * misconfigured, which is when someone is looking.
 */
data class FlowRecord(
    /**
     * Kernel-side connection id. The same connection is reported again as it
     * carries traffic, so this is what lets a later event replace an earlier
     * one instead of stacking up duplicates of the same connection.
     */
    val connectionId: String,
    /** Host or address the app asked for, as the kernel saw it. */
    val destination: String,
    /** Sniffed domain, when there is one; empty for a bare-IP connection. */
    val domain: String,
    /** The rule that decided it, in the kernel's own words. */
    val rule: String,
    /** Where it went. */
    val outcome: FlowOutcome,
    /** The kernel's connection time, epoch millis. */
    val createdAtMs: Long,
) {
    /** What to show as the request: the domain if sniffed, else the address. */
    val label: String get() = domain.ifBlank { destination }
}

/**
 * Where a connection ended up.
 *
 * Deliberately the same three outcomes the rule list offers as actions, so the
 * two halves of the screen speak one vocabulary instead of the list saying
 * "direct" while the flow says "bypass".
 */
enum class FlowOutcome {
    PROXY,
    DIRECT,
    BLOCK,
    ;

    companion object {
        /**
         * Maps the kernel's outbound type.
         *
         * The kernel reports the outbound's *type* — `direct`, `block`, or the
         * protocol (`vless`, `hysteria2`, …) — so anything that is not one of
         * the two terminal types went through a proxy. An unknown type is
         * therefore PROXY rather than a fourth state: guessing "blocked" for
         * something unrecognised would be a worse lie than saying it was
         * proxied, which is what the kernel does with everything it does not
         * reject or send direct.
         */
        fun fromOutboundType(outboundType: String): FlowOutcome = when (outboundType.lowercase()) {
            "direct" -> DIRECT
            "block" -> BLOCK
            else -> PROXY
        }
    }
}
