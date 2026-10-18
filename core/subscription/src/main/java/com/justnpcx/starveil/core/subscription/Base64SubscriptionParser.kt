package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile
import java.util.Base64

/**
 * Parses a base64 subscription: the body decodes to newline-separated share
 * links (`vless://…`, `trojan://…`, `ss://…`).
 *
 * [resolveLink] is injected (it dispatches through the ProtocolRegistry) so
 * this module stays independent of the kernel and the parser is trivially
 * testable with a fake resolver.
 *
 * Never logs the body: subscription text carries credentials.
 */
class Base64SubscriptionParser(
    private val resolveLink: (String) -> Profile?,
) : SubscriptionParser {

    override fun parse(text: String): List<Profile> = parseDetailed(text).profiles

    /**
     * A line that is not a link this app can build is a dropped node, not a
     * broken subscription: one bad entry must not cost the user the other
     * hundred. Text that does not decode at all is the registry's failure, not
     * a per-node one, so it reports zero dropped rather than a line count.
     */
    override fun parseDetailed(text: String): ParseOutcome {
        val decoded = decode(text) ?: return ParseOutcome(emptyList())
        val lines = decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val profiles = lines.mapNotNull { line -> resolveLink(line) }
        return ParseOutcome(profiles, dropped = lines.size - profiles.size)
    }

    companion object {
        /**
         * Cheap, pure sniff: the text is base64-shaped AND decodes to
         * something containing `://`. No IO.
         */
        fun sniff(text: String): Boolean {
            val compact = text.filterNot { it.isWhitespace() }
            if (compact.isEmpty()) return false
            val isBase64Charset = compact.all {
                it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_'
            }
            if (!isBase64Charset) return false
            val decoded = decode(compact) ?: return false
            return decoded.contains("://")
        }

        /** Tolerant base64 (standard or URL-safe, whitespace and padding optional). */
        fun decode(text: String): String? {
            val compact = text.filterNot { it.isWhitespace() }
            if (compact.isEmpty()) return null
            val normalized = compact.replace('-', '+').replace('_', '/')
            val padded = when (normalized.length % 4) {
                0 -> normalized
                2 -> "$normalized=="
                3 -> "$normalized="
                else -> return null
            }
            return try {
                String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
