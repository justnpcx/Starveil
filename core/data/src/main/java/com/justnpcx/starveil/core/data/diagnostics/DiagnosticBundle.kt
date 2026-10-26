package com.justnpcx.starveil.core.data.diagnostics

import com.justnpcx.starveil.core.data.geo.DnsSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the diagnostic bundle says, as plain values.
 *
 * The builder takes primitives rather than repositories on purpose: the same
 * function runs in a test with a fixed input, and a test is the only way to
 * prove that the redaction list is complete.
 */
data class DiagnosticInput(
    val appVersion: String,
    val kernelVersion: String,
    val androidSdk: Int,
    val dns: DnsSnapshot,
    /** Epoch millis of the last leak self-check; 0 when it never ran. */
    val leakCheckedAtMs: Long,
    val leakLeaking: Boolean,
    val nodeCount: Int,
    val groupCount: Int,
    val ruleCount: Int,
    val routePreset: String,
    val killSwitchMode: String,
    val localApiEnabled: Boolean,
    val perAppDirectCount: Int,
    val dnsMetricsEnabled: Boolean,
    /** Already-redacted kernel log lines, oldest first. */
    val logLines: List<String>,
    /**
     * Exact strings that must not survive: node servers, subscription URLs,
     * the local API token. The patterns in [DiagnosticBundle] catch the shapes;
     * this list catches this device's own values.
     */
    val secrets: Collection<String>,
)

/**
 * The text a user pastes into a GitHub issue.
 *
 * ## What is deliberately absent
 *
 * Node links, passwords, UUIDs, tokens, subscription URLs, node server
 * addresses and the local API token. Those are removed twice: [scrub] deletes
 * every exact value the caller names, then removes anything shaped like a
 * link, a URL, a credential, a UUID, or an IP address.
 *
 * The bundle is built from counts and versions, never from the profile table,
 * so most of those values are never in the text to begin with. The log section
 * is the one place they can arrive from, which is why it goes through the same
 * scrub.
 *
 * ## What is deliberately present
 *
 * The leak self-check's own limitation is written into the bundle, not just
 * the screen: the app is excluded from its own tunnel, so a "no leak" result
 * cannot prove anything about DNS or IP leakage, and the bundle must not read
 * as a clean bill of health when it travels without the app around it.
 */
object DiagnosticBundle {

    fun build(input: DiagnosticInput, nowMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(nowMs))
        val leak = when {
            input.leakCheckedAtMs == 0L -> "never run"
            input.leakLeaking -> "leak found at " + formatTime(input.leakCheckedAtMs)
            else -> "no leak found at " + formatTime(input.leakCheckedAtMs)
        }
        val text = buildString {
            appendLine("Starveil diagnostic bundle")
            appendLine("generated=$stamp")
            appendLine("app=" + input.appVersion)
            appendLine("kernel=" + input.kernelVersion)
            appendLine("android_sdk=" + input.androidSdk)
            appendLine()
            appendLine("--- configuration summary (counts only) ---")
            appendLine("nodes=" + input.nodeCount)
            appendLine("groups=" + input.groupCount)
            appendLine("rules=" + input.ruleCount)
            appendLine("route_preset=" + input.routePreset)
            appendLine("kill_switch=" + input.killSwitchMode)
            appendLine("local_api=" + if (input.localApiEnabled) "on" else "off")
            appendLine("per_app_direct=" + input.perAppDirectCount)
            appendLine("dns_sampling=" + if (input.dnsMetricsEnabled) "on" else "off")
            appendLine()
            appendLine("--- DNS (GeoIP probe, not tunnel DNS) ---")
            appendLine(
                "samples=" + input.dns.samples +
                    " p50=" + input.dns.p50Ms + "ms" +
                    " p95=" + input.dns.p95Ms + "ms" +
                    " fallback=" + input.dns.fallbackPct + "%" +
                    " cache=" + input.dns.cacheHitPct + "%",
            )
            appendLine("note=these are the app's own DoH lookups for node hostnames; they say nothing about DNS inside the tunnel")
            appendLine()
            appendLine("--- leak self-check ---")
            appendLine("result=" + leak)
            appendLine("note=the app is excluded from its own tunnel, so this cannot prove that DNS or IP does not leak; it only reports what it can see")
            appendLine()
            appendLine("--- recent kernel log (last " + input.logLines.size + " lines, redacted) ---")
            input.logLines.forEach { appendLine(it) }
        }
        return scrub(text, input.secrets)
    }

    private fun formatTime(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))

    /**
     * Removes what must not leave the device.
     *
     * Exact values first (longest first, so a server that is a prefix of a
     * subscription URL does not leave a fragment behind), then the shapes.
     * Every rule is here rather than at the call site: a redaction that can be
     * forgotten at one call site is not a redaction.
     */
    fun scrub(text: String, secrets: Collection<String>): String {
        var out = text
        secrets
            .filter { it.length >= MIN_SECRET_LENGTH }
            .sortedByDescending { it.length }
            .forEach { secret -> out = out.replace(secret, REDACTED) }

        out = LINK.replace(out, "[link redacted]")
        out = URL.replace(out, "[url redacted]")
        out = BEARER.replace(out, "Bearer " + REDACTED)
        out = CREDENTIAL.replace(out) { match ->
            match.groupValues[1] + "=" + REDACTED
        }
        out = UUID.replace(out, "[uuid redacted]")
        out = IPV4.replace(out, "[address redacted]")
        out = IPV6.replace(out, "[address redacted]")
        return out
    }

    private const val REDACTED = "[redacted]"

    /** Short strings would match everywhere; nothing that short is a secret. */
    private const val MIN_SECRET_LENGTH = 4

    private val LINK = Regex(
        "(?i)\\b(?:ss|ssr|vless|vmess|trojan|hysteria2?|hy2|tuic|wireguard|wg)://\\S+",
    )

    private val URL = Regex("(?i)https?://\\S+")

    private val BEARER = Regex("(?i)bearer\\s+\\S+")

    private val CREDENTIAL = Regex(
        "(?i)\\b(password|passwd|pwd|token|secret|uuid|private[_-]?key)\\b\\s*[=:]\\s*\\S+",
    )

    private val UUID = Regex(
        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
    )

    private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    /**
     * Only forms that cannot be a clock time: eight groups, or anything with a
     * double colon. A looser pattern ate every "20:10:44" in the log.
     */
    private val IPV6 = Regex(
        "\\b(?:[0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}\\b|\\b(?:[0-9A-Fa-f]{1,4}:)+:(?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?\\b",
    )
}
