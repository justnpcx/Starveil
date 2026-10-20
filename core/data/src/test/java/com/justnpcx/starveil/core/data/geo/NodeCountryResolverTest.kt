package com.justnpcx.starveil.core.data.geo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How long a failed lookup is remembered.
 *
 * The old rule was "forever": a host that failed once was never asked again, so
 * a node whose first lookup happened while the phone was offline kept no flag
 * until the app was restarted. The clock is injected here, so the boundary is
 * pinned rather than slept through.
 */
class NodeCountryResolverTest {

    private var now = 1_000_000L
    private var resolves = 0
    private var address: String? = null
    private var country: String? = "US"

    private val resolver = NodeCountryResolver(
        resolveHost = { resolves++; address },
        lookupCountry = { country },
        nowMs = { now },
    )

    @Test
    fun `a failure is not retried inside the cooldown`() = runBlocking {
        address = null

        assertNull(resolver.countryOf("a.example"))
        now += 59_000
        assertNull(resolver.countryOf("a.example"))

        assertEquals("the lookup was repeated inside the cooldown", 1, resolves)
    }

    @Test
    fun `a failure is retried once the cooldown has passed`() = runBlocking {
        address = null
        resolver.countryOf("a.example")

        now += 60_000
        address = "203.0.113.9"
        country = "JP"

        assertEquals("JP", resolver.countryOf("a.example"))
        assertEquals(2, resolves)
    }

    @Test
    fun `an address with no country is treated as a failure`() = runBlocking {
        address = "203.0.113.9"
        country = null

        assertNull(resolver.countryOf("a.example"))
        now += 59_000
        assertNull(resolver.countryOf("a.example"))
        assertEquals(1, resolves)

        now += 1_000
        country = "SG"
        assertEquals("SG", resolver.countryOf("a.example"))
        assertEquals(2, resolves)
    }

    @Test
    fun `a success is cached for the process, not for the cooldown`() = runBlocking {
        address = "203.0.113.9"
        country = "US"

        assertEquals("US", resolver.countryOf("a.example"))
        now += 10_000_000
        assertEquals("US", resolver.countryOf("a.example"))

        assertEquals(1, resolves)
    }

    @Test
    fun `hosts are cached independently`() = runBlocking {
        address = "203.0.113.9"
        country = "US"
        assertEquals("US", resolver.countryOf("a.example"))

        country = "DE"
        assertEquals("DE", resolver.countryOf("b.example"))
        assertEquals(2, resolves)
    }
}
