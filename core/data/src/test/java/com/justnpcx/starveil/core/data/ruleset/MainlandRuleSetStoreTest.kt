package com.justnpcx.starveil.core.data.ruleset

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainlandRuleSetStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val warnings = mutableListOf<String>()

    private fun store(download: suspend (String) -> ByteArray) =
        FileMainlandRuleSetStore(temp.root, download, warnings::add)

    @Test
    fun `both files are fetched once and reused`() = runBlocking {
        val fetched = mutableListOf<String>()
        val store = store { url ->
            fetched += url
            url.toByteArray()
        }

        val first = store.ensure()
        assertNotNull(first)
        assertEquals("one fetch per rule set", 2, fetched.size)

        // Second call must not touch the network: this runs on every connect,
        // and re-downloading on each one would be a startup cost with no point.
        fetched.clear()
        val second = store.ensure()
        assertEquals(first, second)
        assertTrue("no refetch when the files are present", fetched.isEmpty())
    }

    @Test
    fun `a failure falls back to the second url`() = runBlocking {
        val tried = mutableListOf<String>()
        val store = store { url ->
            tried += url
            if (url.contains("jsdelivr")) throw IOException("blocked") else "ok".toByteArray()
        }

        assertNotNull(store.ensure())
        // jsDelivr is the one that works from the mainland, so it is tried
        // first; GitHub raw is what saves us when it is unreachable.
        assertTrue(tried.any { it.contains("jsdelivr") })
        assertTrue(tried.any { it.contains("raw.githubusercontent.com") })
    }

    @Test
    fun `every url failing is a null, not a throw`() = runBlocking {
        val store = store { throw IOException("offline") }

        // "No preset" is a working state — the caller builds a config without
        // it — so this must not be an exception the caller has to catch.
        assertNull(store.ensure())
        assertEquals("one warning per attempted fetch", 4, warnings.size)
    }

    @Test
    fun `half a preset is no preset`() = runBlocking {
        // geosite succeeds, geoip does not.
        val store = store { url ->
            if (url.contains("geosite")) "ok".toByteArray() else throw IOException("offline")
        }

        // Routing domestic names direct without also catching their addresses
        // would send that traffic abroad, which is worse than no preset.
        assertNull(store.ensure())
    }

    @Test
    fun `a partial file is never mistaken for a complete one`() = runBlocking {
        val dir = temp.root
        dir.mkdirs()
        // A zero-length file is what an interrupted download leaves behind.
        File(dir, "geosite-cn.srs").writeBytes(ByteArray(0))

        val store = store { "ok".toByteArray() }
        val result = store.ensure()

        assertNotNull("an empty file is refetched, not trusted", result)
        assertTrue(File(result!!.geositeCnPath).length() > 0)
    }

    @Test
    fun `warnings never carry the url`() = runBlocking {
        store { throw IOException("offline") }.ensure()

        // Same rule as a subscription URL: the path is a CDN today, but the
        // shape is identical and the rule is absolute.
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.none { it.contains("https://") })
    }
}
