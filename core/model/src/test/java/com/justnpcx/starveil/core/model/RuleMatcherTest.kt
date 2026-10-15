package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The matcher's storage form. It shares one database column with whatever
 * earlier versions wrote there, so decoding has to be tolerant rather than
 * strict.
 */
class RuleMatcherTest {

    @Test
    fun `encode and decode round-trip every kind`() {
        MatcherKind.entries.forEach { kind ->
            val matcher = RuleMatcher(kind, "example.com")
            assertEquals(matcher, RuleMatcher.decode(matcher.encode()))
        }
    }

    @Test
    fun `a bare value decodes as a domain suffix`() {
        assertEquals(
            RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"),
            RuleMatcher.decode("example.com"),
        )
    }

    @Test
    fun `an unknown prefix is kept, not dropped`() {
        // Old rows can hold a v2ray-style "geosite:cn". It must decode to
        // something rather than crash or lose the value.
        val decoded = RuleMatcher.decode("geosite:cn")
        assertEquals(MatcherKind.DOMAIN_SUFFIX, decoded.kind)
        assertEquals("geosite:cn", decoded.value)
    }

    @Test
    fun `an ip cidr keeps its slash`() {
        val matcher = RuleMatcher(MatcherKind.IP_CIDR, "10.0.0.0/8")
        assertEquals("ip_cidr:10.0.0.0/8", matcher.encode())
        assertEquals(matcher, RuleMatcher.decode("ip_cidr:10.0.0.0/8"))
    }
}
