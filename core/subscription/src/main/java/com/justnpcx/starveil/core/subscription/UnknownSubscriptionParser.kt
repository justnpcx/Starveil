package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile

/**
 * Fallback for text that matches no known format.
 *
 * Deliberately yields nothing rather than fabricating nodes: a subscription
 * that cannot be understood must surface as a failure and keep the last-good
 * config, never silently import demo entries. (The skeleton's
 * FakeSubscriptionParser did the opposite; real formats have since landed.)
 */
class UnknownSubscriptionParser : SubscriptionParser {
    override fun parse(text: String): List<Profile> = emptyList()
}
