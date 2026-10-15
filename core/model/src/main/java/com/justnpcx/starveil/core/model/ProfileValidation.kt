package com.justnpcx.starveil.core.model

/**
 * The one host/port rule.
 *
 * Both the link importer and the node editor go through here, so a node typed
 * by hand and the same node pasted as a link are accepted or rejected on
 * exactly the same terms. Two copies of this rule would eventually disagree,
 * and the symptom would be a node that imports fine but cannot be re-entered.
 */
object ProfileValidation {

    const val MIN_PORT = 1
    const val MAX_PORT = 65535

    fun validHost(host: String): Boolean = host.isNotBlank()

    fun validPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

    fun valid(host: String, port: Int): Boolean = validHost(host) && validPort(port)

    /**
     * A port field as typed: null when it is not a number, or is out of range.
     * Rejects `"0"` and `"70000"` rather than letting them reach the kernel,
     * where the config would fail to build with a much worse message.
     */
    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::validPort)
}
