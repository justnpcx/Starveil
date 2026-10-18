package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile

/**
 * Parses subscription content (raw text) into profiles.
 *
 * Implementations must be pure functions: no network, no disk, no logging
 * of the input (subscription text contains credentials).
 */
interface SubscriptionParser {
    fun parse(text: String): List<Profile>

    /**
     * The same parse, plus how many entries were recognised and refused.
     *
     * Separate from [parse] so a parser with nothing to count keeps its simple
     * shape, and so the default is safe for any new format: zero dropped is
     * honest only when the parser cannot tell, and every parser that *can*
     * tell overrides this.
     */
    fun parseDetailed(text: String): ParseOutcome = ParseOutcome(parse(text))
}

/**
 * What a parse produced: the nodes it could build, and the entries it refused.
 *
 * [dropped] counts entries the parser *recognised* and rejected — a SIP008
 * server without a method, a sing-box outbound of a protocol this app cannot
 * build. Text that is not this format at all is not "dropped": it is a
 * different failure, handled by the registry.
 */
data class ParseOutcome(
    val profiles: List<Profile>,
    val dropped: Int = 0,
)
