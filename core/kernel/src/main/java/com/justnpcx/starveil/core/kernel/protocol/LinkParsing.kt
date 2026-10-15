package com.justnpcx.starveil.core.kernel.protocol

import com.justnpcx.starveil.core.model.ProfileValidation
import java.util.Base64

/**
 * Shared helpers for parsing proxy share links (`vless://`, `trojan://`, `ss://`).
 *
 * Deliberately hand-rolled rather than `java.net.URI`: userinfo in these links
 * is often base64 (which contains `=` and `/`) and would be mis-split by URI.
 * Everything here is pure and never logs its input — the raw link carries
 * credentials (see README "安全红线").
 */
internal object LinkParsing {

    /** Splits `body#fragment` into the body and the (decoded) fragment, if any. */
    fun splitFragment(body: String): Pair<String, String?> {
        val hash = body.indexOf('#')
        if (hash < 0) return body to null
        val name = percentDecode(body.substring(hash + 1)).takeIf { it.isNotBlank() }
        return body.substring(0, hash) to name
    }

    /** Splits `body?query` into the body and the raw query string, if any. */
    fun splitQuery(body: String): Pair<String, String?> {
        val q = body.indexOf('?')
        if (q < 0) return body to null
        return body.substring(0, q) to body.substring(q + 1)
    }

    fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) out[percentDecode(pair)] = ""
            else out[percentDecode(pair.substring(0, eq))] = percentDecode(pair.substring(eq + 1))
        }
        return out
    }

    /** Parses `host:port`, tolerating bracketed IPv6 (`[::1]:443`). */
    fun splitHostPort(s: String): Pair<String, Int>? {
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {
            val close = s.indexOf(']')
            if (close < 0) return null
            val host = s.substring(1, close)
            val rest = s.substring(close + 1)
            if (!rest.startsWith(":")) return null
            val port = rest.substring(1).toIntOrNull() ?: return null
            return if (valid(host, port)) host to port else null
        }
        val colon = s.lastIndexOf(':')
        if (colon <= 0) return null
        val host = s.substring(0, colon)
        val port = s.substring(colon + 1).toIntOrNull() ?: return null
        return if (valid(host, port)) host to port else null
    }

    // Delegated, not reimplemented: the editor validates with the same rule, so
    // a node cannot import cleanly and then fail to be re-entered by hand.
    private fun valid(host: String, port: Int): Boolean = ProfileValidation.valid(host, port)

    /** Percent-decoding that does NOT treat `+` as space (unlike URLDecoder). */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 3 <= s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.write(v)
                    i += 3
                    continue
                }
            }
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    /** Base64 (standard or URL-safe, padding optional). Returns null on bad input. */
    fun base64Decode(s: String): String? {
        if (s.isBlank()) return null
        val normalized = s.replace('-', '+').replace('_', '/')
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

    /** `true` for "1"/"true"/"yes". */
    fun asBool(value: String?): Boolean =
        value != null && value.lowercase() in setOf("1", "true", "yes")

    fun asAlpn(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
