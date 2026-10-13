package com.justnpcx.starveil.core.subscription.registry

import com.justnpcx.starveil.core.subscription.FakeSubscriptionParser
import com.justnpcx.starveil.core.subscription.SubscriptionParser

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
         * Skeleton default: only the Fake parser, as an unconditional
         * fallback. Real formats (base64 lines, Clash YAML, …) register
         * ahead of it in Phase 2 with real sniffers.
         */
        fun default(): SubscriptionFormatRegistry = SubscriptionFormatRegistry(
            listOf(
                SubscriptionFormatDescriptor(
                    format = "text-fallback",
                    sniff = { true },
                    parser = FakeSubscriptionParser(),
                ),
            ),
        )
    }
}
