package com.justnpcx.starveil.core.kernel.ruleset

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsRuleSetLookupTest {

    private fun lookup(vararg rules: SrsRule) =
        SrsRuleSetLookup(mapOf("ads" to SrsRuleSet(1, rules.toList())))

    @Test
    fun `a suffix with a dot covers children only, without a dot covers the apex too`() {
        val legacy = lookup(SrsRule(domainSuffixes = listOf(".example.com")))
        assertTrue(legacy.matchesDomain("ads", "a.example.com"))
        assertFalse(legacy.matchesDomain("ads", "example.com"))

        val modern = lookup(SrsRule(domainSuffixes = listOf("example.com")))
        assertTrue(modern.matchesDomain("ads", "example.com"))
        assertTrue(modern.matchesDomain("ads", "a.example.com"))
        assertFalse(modern.matchesDomain("ads", "notexample.com"))
    }

    @Test
    fun `exact names and keywords match, and case does not matter`() {
        val set = lookup(SrsRule(exactDomains = listOf("DoubleClick.net"), domainKeywords = listOf("adsrv")))
        assertTrue(set.matchesDomain("ads", "doubleclick.net"))
        assertTrue(set.matchesDomain("ads", "x.adsrv.example"))
        assertFalse(set.matchesDomain("ads", "doubleclick.net.example"))
    }

    @Test
    fun `a v4 range contains what it should and nothing beside it`() {
        // 223.5.0.0 - 223.5.255.255
        val set = lookup(
            SrsRule(
                ipRanges = listOf(
                    IpRange(
                        fromHigh = 0L,
                        fromLow = 0xDF050000L,
                        toHigh = 0L,
                        toLow = 0xDF05FFFFL,
                        isV6 = false,
                    ),
                ),
            ),
        )
        assertTrue(set.matchesIp("ads", "223.5.5.5"))
        assertFalse(set.matchesIp("ads", "8.8.8.8"))
        assertFalse(set.matchesIp("ads", "223.6.0.1"))
    }

    @Test
    fun `a v6 address is parsed and compared against v6 ranges only`() {
        val set = lookup(
            SrsRule(
                ipRanges = listOf(
                    IpRange(
                        fromHigh = 0x20010DB800000000L,
                        fromLow = 0L,
                        toHigh = 0x20010DB8FFFFFFFFL,
                        toLow = -1L,
                        isV6 = true,
                    ),
                ),
            ),
        )
        assertTrue(set.matchesIp("ads", "2001:db8::1"))
        assertFalse(set.matchesIp("ads", "2001:db9::1"))
        // A v4 address never matches a v6 range, whatever the numbers say.
        assertFalse(set.matchesIp("ads", "0.0.0.0"))
    }

    @Test
    fun `an inverted rule flips its own answer`() {
        val set = lookup(SrsRule(exactDomains = listOf("keep.example"), invert = true))
        assertTrue(set.matchesDomain("ads", "other.example"))
        assertFalse(set.matchesDomain("ads", "keep.example"))
    }

    @Test
    fun `a tag that was never loaded matches nothing`() {
        val set = lookup(SrsRule(exactDomains = listOf("a.example")))
        assertFalse(set.matchesDomain("geosite-cn", "a.example"))
        assertFalse(set.matchesIp("geoip-cn", "1.1.1.1"))
    }
}

