package com.justnpcx.starveil.core.kernel

/**
 * Strips anything that looks like a credential from a kernel log line.
 *
 * Deliberately conservative: it would rather mangle a benign line than leak a
 * UUID, a password, or a subscription URL's query string.
 */
object LogRedactor {

    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val URL_QUERY = Regex("(https?://[^\\s?]+)\\?[^\\s]*")
    private val SECRET_KV = Regex("(?i)(password|passwd|uuid|secret|token|private[_-]?key)\\s*[=:]\\s*\\S+")
    /** sing-box colours its log lines; the viewer shows the raw escape codes. */
    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")

    fun redact(message: String): String {
        var out = message
        out = ANSI.replace(out, "")
        out = UUID.replace(out, "<uuid>")
        out = URL_QUERY.replace(out, "$1?<redacted>")
        out = SECRET_KV.replace(out, "$1=<redacted>")
        return out
    }
}
