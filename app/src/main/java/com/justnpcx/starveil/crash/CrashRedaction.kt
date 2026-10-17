package com.justnpcx.starveil.crash

/**
 * Removes anything from a crash report that identifies a node or a credential.
 *
 * This app's exceptions are full of exactly the strings that must not leave the
 * device: a dial failure names the node's address, a parse failure quotes the
 * link it could not parse, and a config error can carry a UUID. Sentry would
 * happily store all of it, and the report is not something the user can review
 * before it goes.
 *
 * So the rule is by shape, not by key: whatever looks like a share link, a URL,
 * a UUID, an IP address or a long key is replaced, wherever it appears. That
 * over-redacts occasionally — a URL in a message that would have been useful —
 * and that is the correct direction to be wrong in.
 *
 * Pure and free of the SDK, so the rules can be tested without a DSN, a network
 * or a crash.
 */
object CrashRedaction {

    private val RULES: List<Pair<Regex, String>> = listOf(
        // Share links first: they embed a UUID, an address and often a name, and
        // replacing the link as a whole is the only way to be sure of all three.
        Regex("""(?i)\b(?:vless|vmess|trojan|ss|ssr|hysteria2?|hy2|tuic|wireguard|socks5?)://[^\s"']+""") to
            "[redacted link]",
        // A subscription URL is an ordinary https URL with a token in it, which
        // is indistinguishable from any other URL — so every URL goes.
        Regex("""\bhttps?://[^\s"']+""") to "[redacted url]",
        Regex(
            """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""",
        ) to "[redacted uuid]",
        Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""") to "[redacted address]",
        // Bracketed first: Go writes an IPv6 endpoint as `[addr]:port`, and the
        // generic rule below would otherwise match only the part inside.
        Regex("""\[[0-9a-fA-F:]{4,}\]""") to "[redacted address]",
        // At least three colons, so a clock time or a date is not mistaken for
        // an address. `{0,4}` allows the empty groups of a compressed address
        // such as `2001:db8::1`.
        Regex("""\b[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{0,4}){3,7}\b""") to "[redacted address]",
        // A base64 or hex key. Nothing in a stack trace is this long without
        // punctuation, so the false-positive rate is low and the cost of one is
        // a slightly less readable frame.
        Regex("""\b[A-Za-z0-9+/]{40,}={0,2}""") to "[redacted secret]",
    )

    fun scrub(text: String): String = RULES.fold(text) { scrubbed, (pattern, replacement) ->
        pattern.replace(scrubbed, replacement)
    }
}
