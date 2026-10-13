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
}
