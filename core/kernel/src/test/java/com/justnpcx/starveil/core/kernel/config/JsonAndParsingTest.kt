package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.protocol.LinkParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {

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

class LinkParsingTest {

    @Test
    fun `percent decodes utf8 and leaves plus untouched`() {
        assertEquals("a b", LinkParsing.percentDecode("a%20b"))
        assertEquals("中文", LinkParsing.percentDecode("%E4%B8%AD%E6%96%87"))
        assertEquals("a+b", LinkParsing.percentDecode("a+b"))
        assertEquals("100%", LinkParsing.percentDecode("100%25"))
    }

    @Test
    fun `base64 tolerates missing padding and urlsafe alphabet`() {
        assertEquals("hello", LinkParsing.base64Decode("aGVsbG8"))
        assertEquals("a?b>", LinkParsing.base64Decode("YT9iPg"))
        assertEquals("sub?a", LinkParsing.base64Decode("c3ViP2E"))
    }

    @Test
    fun `host port handles ipv6 and rejects garbage`() {
        assertEquals("::1" to 443, LinkParsing.splitHostPort("[::1]:443"))
        assertEquals("example.com" to 443, LinkParsing.splitHostPort("example.com:443"))
        assertNull(LinkParsing.splitHostPort("host"))
        assertNull(LinkParsing.splitHostPort("host:0"))
        assertNull(LinkParsing.splitHostPort("host:70000"))
        assertNull(LinkParsing.splitHostPort(":443"))
    }

    @Test
    fun `query parsing decodes keys and values`() {
        val q = LinkParsing.parseQuery("security=tls&sni=a%2Eb&flag")
        assertEquals("tls", q["security"])
        assertEquals("a.b", q["sni"])
        assertEquals("", q["flag"])
    }
}
