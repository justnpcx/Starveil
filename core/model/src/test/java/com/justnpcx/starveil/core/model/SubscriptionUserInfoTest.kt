package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The header is written by other people's servers, so the parser is defensive. */
class SubscriptionUserInfoTest {

    @Test
    fun `a full header gives all four numbers`() {
        val info = parseSubscriptionUserInfo("upload=1000; download=2000; total=10000; expire=1800000000")
        assertEquals(1000L, info?.uploadBytes)
        assertEquals(2000L, info?.downloadBytes)
        assertEquals(10000L, info?.totalBytes)
        assertEquals(1800000000L, info?.expireEpochSeconds)
        assertEquals(3000L, info?.usedBytes)
    }

    @Test
    fun `missing parts stay missing instead of becoming zero`() {
        val info = parseSubscriptionUserInfo("total=10000")
        assertEquals(10000L, info?.totalBytes)
        assertNull(info?.uploadBytes)
        assertNull("no usage stated is not \"0 used\"", info?.usedBytes)
        assertNull(info?.expireEpochSeconds)
    }

    @Test
    fun `a header with nothing usable is null`() {
        assertNull(parseSubscriptionUserInfo(null))
        assertNull(parseSubscriptionUserInfo(""))
        assertNull(parseSubscriptionUserInfo("garbage"))
        assertNull(parseSubscriptionUserInfo("upload=abc; total=-5"))
    }

    @Test
    fun `separators and spacing are tolerated`() {
        val info = parseSubscriptionUserInfo(" upload = 10 ;download=20, total=30 ")
        assertEquals(10L, info?.uploadBytes)
        assertEquals(20L, info?.downloadBytes)
        assertEquals(30L, info?.totalBytes)
    }

    @Test
    fun `days left is rounded up, and an expiry in the past is null`() {
        val now = 1_000_000L
        val inTenDays = SubscriptionUserInfo(expireEpochSeconds = now + 10 * 86_400L)
        assertEquals(10L, inTenDays.daysLeft(now))

        val inOneHour = SubscriptionUserInfo(expireEpochSeconds = now + 3_600L)
        assertEquals("an hour left is still a day", 1L, inOneHour.daysLeft(now))

        val expired = SubscriptionUserInfo(expireEpochSeconds = now - 1L)
        assertNull(expired.daysLeft(now))
        assertNull(SubscriptionUserInfo().daysLeft(now))
    }
}
