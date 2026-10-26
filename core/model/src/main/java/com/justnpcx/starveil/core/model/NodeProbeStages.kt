package com.justnpcx.starveil.core.model

/** The four layers a connection can fail at. */
enum class ProbeStage { DNS, TCP, TLS, HTTP }

enum class StageOutcome { OK, TIMEOUT, FAILED, SKIPPED }

/**
 * Why a stage was skipped, as a code rather than a sentence.
 *
 * The wording belongs to the screen: a probe has no resources, and a sentence
 * written in the probe would be English in a Chinese app.
 */
enum class StageNote {
    NONE,
    /** The name was already an address; no lookup happened. */
    LITERAL_HOST,
    /** Hysteria2, TUIC and WireGuard speak UDP only. */
    UDP_PROTOCOL,
    /** REALITY's ClientHello is uTLS-shaped; a plain TLS handshake proves nothing. */
    REALITY_HANDSHAKE,
    /** This protocol carries no TLS layer. */
    NO_TLS,
    /** No proxy protocol here is HTTP; a request to the node would not be answered. */
    NO_HTTP,
}

data class StageResult(
    val stage: ProbeStage,
    val outcome: StageOutcome,
    /** Milliseconds this stage took; 0 for a skipped stage. */
    val ms: Long = 0L,
    val note: StageNote = StageNote.NONE,
)

/**
 * What a staged probe found.
 *
 * [failedAt] is the point of the whole thing: "timeout" on a TCP handshake and
 * "timeout" on a TLS handshake are different problems, and the health score's
 * single number cannot say which one a node has.
 */
data class StagedProbeReport(
    val address: String?,
    val stages: List<StageResult>,
) {
    /** The first stage that did not complete, if any. */
    val failedAt: StageResult?
        get() = stages.firstOrNull { it.outcome == StageOutcome.TIMEOUT || it.outcome == StageOutcome.FAILED }

    /** True when every stage that could run, ran. */
    val reached: Boolean
        get() = failedAt == null && stages.any { it.outcome == StageOutcome.OK }
}

/**
 * Runs the staged diagnostic for one node.
 *
 * A port rather than a class the screen constructs: the implementation needs a
 * DoH resolver and a socket, and :feature:profiles is not allowed to see
 * either. :app binds it to the real probe.
 *
 * Deliberately separate from the health probe: the score exists to choose a
 * node and must not change because someone opened a diagnostic.
 */
interface StagedProbePort {
    suspend fun probe(profile: Profile, timeoutMs: Int = 3_000): StagedProbeReport
}
