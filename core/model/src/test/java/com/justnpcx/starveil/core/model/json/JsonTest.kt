package com.justnpcx.starveil.core.model.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonWriterTest {

    @Test
    fun `escapes quotes and control characters`() {
        assertEquals("\"a\\\"b\"", Json.write("a\"b"))
        assertEquals("\"a\\\\b\"", Json.write("a\\b"))
        assertEquals("\"a\\nb\"", Json.write("a\nb"))
    }

    @Test
    fun `writes nested objects and arrays in insertion order`() {
        val value = linkedMapOf<String, Any?>(
            "a" to 1,
            "b" to listOf(true, null, "x"),
            "c" to linkedMapOf("d" to 2),
        )
        assertEquals("""{"a":1,"b":[true,null,"x"],"c":{"d":2}}""", Json.write(value))
    }

    @Test
    fun `integral doubles render without a decimal point`() {
        assertEquals("443", Json.write(443.0))
        assertEquals("1.5", Json.write(1.5))
    }
}

class JsonParserTest {

    @Test
    fun `parses scalars, objects and arrays`() {
        assertEquals(1L, Json.parse("1"))
        assertEquals(true, Json.parse("true"))
        assertNull(Json.parse("null"))
        assertEquals("hi", Json.parse("\"hi\""))
        assertEquals(
            linkedMapOf<String, Any?>("a" to 1L, "b" to listOf("x", 2L)),
            Json.parse("""{"a":1,"b":["x",2]}"""),
        )
    }

    @Test
    fun `round-trips a nested structure`() {
        val original = linkedMapOf<String, Any?>(
            "kind" to "vless",
            "uuid" to "1111-2222",
            "nested" to linkedMapOf("list" to listOf(1, 2, 3), "flag" to true),
        )
        val text = Json.write(original)
        @Suppress("UNCHECKED_CAST")
        val back = Json.parse(text) as Map<String, Any?>
        assertEquals("vless", back["kind"])
        assertEquals("1111-2222", back["uuid"])
        assertEquals(linkedMapOf<String, Any?>("list" to listOf(1L, 2L, 3L), "flag" to true), back["nested"])
    }

    @Test
    fun `parses unicode escapes and rejects malformed input`() {
        assertEquals("A", Json.parse("\"\\u0041\""))
        listOf("", "{", "[1,", "{\"a\":}", "tru", "\"unterminated", "1 2").forEach { bad ->
            var threw = false
            try {
                Json.parse(bad)
            } catch (e: JsonException) {
                threw = true
            }
            assertTrue("expected JsonException for <$bad>", threw)
        }
    }
}
