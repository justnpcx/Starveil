package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType

/**
 * Fake parser for UI development: turns any non-blank line into a demo
 * profile. Never used in production paths.
 */
class FakeSubscriptionParser : SubscriptionParser {
    override fun parse(text: String): List<Profile> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                Profile(
                    id = index.toLong(),
                    name = "Demo Node ${index + 1}",
                    type = ProfileType.VLESS,
                    server = "demo-${index + 1}.example.invalid",
                    port = 443,
                )
            }
            .toList()
}
