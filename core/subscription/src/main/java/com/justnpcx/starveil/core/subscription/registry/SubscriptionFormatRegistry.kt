package com.justnpcx.starveil.core.subscription.registry

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.subscription.Base64SubscriptionParser
import com.justnpcx.starveil.core.subscription.ClashYamlSubscriptionParser
import com.justnpcx.starveil.core.subscription.SubscriptionParser
import com.justnpcx.starveil.core.subscription.UnknownSubscriptionParser

/**
 * One subscription format: how to recognise it, and how to parse it.
 *
 * - [sniff]: return true if [text] looks like this format. Must be a
 *   cheap, pure check (prefix / JSON shape / base64 sniffing) — never
 *   network or disk. Must never log [text]: it contains credentials.
 * - [parser]: the parser for this format. Pure function.
 */
data class SubscriptionFormatDescriptor(
    val format: String,
    val sniff: (String) -> Boolean,
    val parser: SubscriptionParser,
)

/**
 * Subscription-format registry: the single dispatch from raw subscription
 * text to a parser.
 *
 * Descriptors are tried in registration order; the first whose [sniff]
 * matches wins. Register specific formats first and the catch-all
 * fallback last. Adding a format = registering one descriptor — no
 * `when` / if-else chain to extend.
 */
class SubscriptionFormatRegistry(
    private val descriptors: List<SubscriptionFormatDescriptor>,
) {

    init {
        require(descriptors.isNotEmpty()) { "SubscriptionFormatRegistry must contain at least one descriptor" }
    }

    fun resolve(text: String): SubscriptionParser =
        descriptors.firstOrNull { it.sniff(text) }?.parser ?: descriptors.first().parser

    fun all(): List<SubscriptionFormatDescriptor> = descriptors.toList()

    companion object {
        /**
         * Skeleton default: the real formats (Clash YAML, base64 lines) are
         * tried first, most-specific first, and the Fake parser is the
         * catch-all fallback last.
         *
         * [resolveLink] dispatches a share link through the ProtocolRegistry;
         * it is injected so this module does not depend on :core:kernel.
         */
        fun default(resolveLink: (String) -> Profile?): SubscriptionFormatRegistry =
            SubscriptionFormatRegistry(
                listOf(
                    SubscriptionFormatDescriptor(
                        format = "clash-yaml",
                        sniff = ClashYamlSubscriptionParser::sniff,
                        parser = ClashYamlSubscriptionParser(),
                    ),
                    SubscriptionFormatDescriptor(
                        format = "base64-lines",
                        sniff = Base64SubscriptionParser::sniff,
                        parser = Base64SubscriptionParser(resolveLink),
                    ),
                    SubscriptionFormatDescriptor(
                        format = "unknown-fallback",
                        sniff = { true },
                        parser = UnknownSubscriptionParser(),
                    ),
                ),
            )
    }
}
