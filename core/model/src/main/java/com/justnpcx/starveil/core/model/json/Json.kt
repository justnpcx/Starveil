package com.justnpcx.starveil.core.model.json

/**
 * A tiny, dependency-free JSON writer + parser.
 *
 * Pure Kotlin so it runs in plain JVM unit tests (`org.json` is an android.jar
 * stub that throws there) and so `:core:model` keeps its zero-dependency
 * promise. Deliberately small: it only needs to round-trip the app's own
 * config/settings blobs and Clash-free subscription data.
 *
 * Writer: object keys keep insertion order, so output is deterministic.
 * Parser: strict JSON (RFC 8259 subset) — objects, arrays, strings, numbers,
 * booleans and null.
 */
object Json {

    // ------------------------------------------------------------- writing

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

    // ------------------------------------------------------------- parsing

    /** Parses a JSON document. Throws [JsonException] on malformed input. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Trailing content at offset ${parser.offset}", parser.offset)
        return value
    }

    /** Parses and requires the top-level value to be an object. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> {
        val value = parse(text)
        return value as? Map<String, Any?>
            ?: throw JsonException("Expected a JSON object at the top level")
    }

    private class Parser(private val text: String) {
        var offset = 0
            private set

        fun atEnd(): Boolean = offset >= text.length

        fun skipWhitespace() {
            while (offset < text.length && text[offset].isWhitespace()) offset++
        }

        fun readValue(): Any? {
            if (atEnd()) throw JsonException("Unexpected end of input", offset)
            return when (val c = text[offset]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else ->
                    if (c == '-' || c in '0'..'9') readNumber()
                    else throw JsonException("Unexpected character '$c' at offset $offset", offset)
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                offset++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> offset++
                    '}' -> {
                        offset++
                        return map
                    }

                    else -> throw JsonException("Expected ',' or '}' at offset $offset", offset)
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                offset++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> offset++
                    ']' -> {
                        offset++
                        return list
                    }

                    else -> throw JsonException("Expected ',' or ']' at offset $offset", offset)
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string", offset)
                when (val c = text[offset++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated escape", offset)
                        when (val esc = text[offset++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (offset + 4 > text.length) throw JsonException("Bad \\u escape", offset)
                                val hex = text.substring(offset, offset + 4)
                                sb.append(hex.toInt(16).toChar())
                                offset += 4
                            }

                            else -> throw JsonException("Bad escape '\\$esc'", offset)
                        }
                    }

                    else -> sb.append(c)
                }
            }
        }

        private fun readBoolean(): Boolean =
            when {
                text.startsWith("true", offset) -> {
                    offset += 4
                    true
                }

                text.startsWith("false", offset) -> {
                    offset += 5
                    false
                }

                else -> throw JsonException("Bad literal at offset $offset", offset)
            }

        private fun readNull(): Any? {
            if (!text.startsWith("null", offset)) throw JsonException("Bad literal at offset $offset", offset)
            offset += 4
            return null
        }

        private fun readNumber(): Any {
            val start = offset
            if (peek() == '-') offset++
            while (!atEnd() && (text[offset].isDigit() || text[offset] in ".eE+-")) offset++
            val raw = text.substring(start, offset)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()
                ?: throw JsonException("Bad number '$raw'", offset)
        }

        private fun peek(): Char =
            if (atEnd()) throw JsonException("Unexpected end of input", offset) else text[offset]

        private fun expect(c: Char) {
            if (atEnd() || text[offset] != c) {
                throw JsonException("Expected '$c' at offset $offset", offset)
            }
            offset++
        }
    }
}

/** Thrown when a JSON document is malformed. */
class JsonException(
    message: String,
    /** Byte offset the parser was at, when the failure has one. */
    val offset: Int? = null,
) : IllegalArgumentException(message)
