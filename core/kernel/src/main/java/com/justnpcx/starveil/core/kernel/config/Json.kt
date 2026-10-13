package com.justnpcx.starveil.core.kernel.config

/**
 * A tiny, dependency-free JSON writer.
 *
 * Why hand-rolled: the config builder must be a pure function that runs in
 * plain JVM unit tests. `org.json` is an android.jar stub (throws in unit
 * tests) and a serialization library would be a new build dependency. The
 * writer only needs to emit the shapes sing-box configs use:
 * object / array / string / number / boolean / null.
 *
 * Deterministic: object keys are emitted in the order given, so snapshots
 * are stable.
 */
internal object Json {

    fun write(value: Any?): String = buildString { writeValue(this, value) }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long -> sb.append(value.toString())
            is Float, is Double -> sb.append(formatNumber((value as Number).toDouble()))
            is Map<*, *> -> writeObject(sb, value)
            is Iterable<*> -> writeArray(sb, value)
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeObject(sb: StringBuilder, map: Map<*, *>) {
        sb.append('{')
        var first = true
        for ((key, value) in map) {
            if (key == null) continue
            if (!first) sb.append(',')
            first = false
            writeString(sb, key.toString())
            sb.append(':')
            writeValue(sb, value)
        }
        sb.append('}')
    }

    private fun writeArray(sb: StringBuilder, values: Iterable<*>) {
        sb.append('[')
        var first = true
        for (value in values) {
            if (!first) sb.append(',')
            first = false
            writeValue(sb, value)
        }
        sb.append(']')
    }

    private fun formatNumber(d: Double): String =
        if (d.isFinite() && d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else ->
                    if (c < ' ') sb.append("\\u%04x".format(c.code))
                    else sb.append(c)
            }
        }
        sb.append('"')
    }
}
