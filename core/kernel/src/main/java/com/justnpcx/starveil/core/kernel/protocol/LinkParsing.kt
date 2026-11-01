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

    /**
     * Splits Xray's early-data hint out of a WS path.
     *
     * Xray writes ?ed=2560 on the end of the path and, when it dials, sends that
     * many bytes of the first frame alongside the upgrade request in the
     * Sec-WebSocket-Protocol header. sing-box models the same thing as two
     * fields on the transport, so the number has to leave the path here — a path
     * still carrying the query is a path the server matches literally.
     *
     * Only ed is lifted: a path with any other query is left exactly as it was,
     * because there is nothing in the model to put those parameters in.
     */
    fun splitEarlyData(path: String?): Pair<String?, Int?> {
        if (path == null) return null to null
        val q = path.indexOf('?')
        if (q < 0) return path to null
        val params = parseQuery(path.substring(q + 1))
        val ed = params["ed"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return path to null
        return path.substring(0, q).ifEmpty { "/" } to ed
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

    /**
     * The inverse of [percentDecode]: a space becomes `%20`, never `+`.
     *
     * Escapes everything outside the unreserved set rather than only the
     * characters that must be escaped. A link is read back by other clients
     * too, and one of them treating `+` as a space — which is exactly what
     * [percentDecode] refuses to do — is how a password silently changes.
     */
    fun percentEncode(s: String): String {
        val out = StringBuilder(s.length)
        for (byte in s.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val c = value.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in UNRESERVED) {
                out.append(c)
            } else {
                out.append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
        return out.toString()
    }

    /** Standard base64 with padding — the shape a v2rayN `vmess://` blob takes. */
    fun base64Encode(s: String): String =
        Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    /** URL-safe base64 without padding — the shape SIP002 uses for userinfo. */
    fun base64EncodeUrl(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

    private const val UNRESERVED = "-._~"
    private const val HEX = "0123456789ABCDEF"
}
