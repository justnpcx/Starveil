package com.justnpcx.starveil.core.data.ruleset

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rule-set download: what is trusted, what is thrown away, and what the
 * caller is told.
 *
 * The hashes here are of the fixtures, not of the real artifacts — the store
 * takes its pinned set as a parameter so a test can pin its own bytes. What is
 * being tested is the rule ("bytes must hash to the pin"), not the value of any
 * particular pin.
 */
class MainlandRuleSetStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val warnings = mutableListOf<String>()

    private val geositeBody = "geosite-cn-fixture"
    private val geoipBody = "geoip-cn-fixture"
    private val adsBody = "ads-fixture"

    private fun pinned(name: String, body: String) = PinnedRuleSet(
        name = name,
        sha256 = FileMainlandRuleSetStore.sha256(body.toByteArray()),
        urls = listOf("https://cdn.test/$name"),
    )

    private fun store(download: suspend (String) -> ByteArray) = FileMainlandRuleSetStore(
        directory = temp.root,
        download = download,
        warn = warnings::add,
        geositeCn = pinned("geosite-cn.srs", geositeBody),
        geoipCn = pinned("geoip-cn.srs", geoipBody),
        ads = pinned("geosite-ads.srs", adsBody),
    )

    /** The happy path: a mirror serving exactly what was pinned. */
    private fun honest(url: String): ByteArray = when {
        url.endsWith("geosite-cn.srs") -> geositeBody.toByteArray()
        url.endsWith("geoip-cn.srs") -> geoipBody.toByteArray()
        else -> adsBody.toByteArray()
    }

    @Test
    fun `both files are fetched once and reused`() = runBlocking {
        val fetched = mutableListOf<String>()
        val store = store { url ->
            fetched += url
            honest(url)
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
        assertNull(store.failure.value)
    }

    @Test
    fun `a failure falls back to the second url`() = runBlocking {
        val tried = mutableListOf<String>()
        val store = FileMainlandRuleSetStore(
            directory = temp.root,
            download = { url ->
                tried += url
                if (url.contains("jsdelivr")) throw IOException("blocked") else honest(url)
            },
            warn = warnings::add,
            geositeCn = PinnedRuleSet(
                "geosite-cn.srs",
                FileMainlandRuleSetStore.sha256(geositeBody.toByteArray()),
                listOf("https://cdn.jsdelivr.net/x/geosite-cn.srs", "https://raw.test/geosite-cn.srs"),
            ),
            geoipCn = PinnedRuleSet(
                "geoip-cn.srs",
                FileMainlandRuleSetStore.sha256(geoipBody.toByteArray()),
                listOf("https://cdn.jsdelivr.net/x/geoip-cn.srs", "https://raw.test/geoip-cn.srs"),
            ),
        )

        assertNotNull(store.ensure())
        // jsDelivr is the one that works from the mainland, so it is tried
        // first; GitHub raw is what saves us when it is unreachable.
        assertTrue(tried.any { it.contains("jsdelivr") })
        assertTrue(tried.any { it.contains("raw.test") })
    }

    @Test
    fun `a tampered file is rejected and never written`() = runBlocking {
        val store = store { url -> "tampered".toByteArray() }

        assertNull(store.ensure())

        // The one thing that must not happen: a body that failed its hash
        // reaching the path sing-box reads.
        assertFalse(File(temp.root, "geosite-cn.srs").exists())
        assertFalse(File(temp.root, "geoip-cn.srs").exists())
        assertEquals(RuleSetFailure.Reason.HASH_MISMATCH, store.failure.value?.reason)
    }

    @Test
    fun `one bad file is not cleared by the other one succeeding`() = runBlocking {
        val store = store { url ->
            if (url.endsWith("geosite-cn.srs")) "tampered".toByteArray() else honest(url)
        }

        assertNull(store.ensure())
        // The second file succeeding used to wipe the first one's failure,
        // which is the only case where the user needs to be told anything.
        assertEquals(RuleSetFailure.Reason.HASH_MISMATCH, store.failure.value?.reason)
    }

    @Test
    fun `an empty body is a failed download, not a trusted empty file`() = runBlocking {
        val store = store { ByteArray(0) }

        assertNull(store.ensure())
        assertFalse(File(temp.root, "geosite-cn.srs").exists())
        assertEquals(RuleSetFailure.Reason.DOWNLOAD_FAILED, store.failure.value?.reason)
    }

    @Test
    fun `a file on disk that does not match its hash is discarded and refetched`() = runBlocking {
        temp.root.mkdirs()
        File(temp.root, "geosite-cn.srs").writeBytes("something else".toByteArray())

        val store = store { url -> honest(url) }
        val result = store.ensure()

        assertNotNull("the bad copy is replaced, not trusted", result)
        assertEquals(geositeBody, File(result!!.geositeCnPath).readText())
    }

    @Test
    fun `every url failing is a null, not a throw`() = runBlocking {
        val store = store { throw IOException("offline") }

        // "No preset" is a working state — the caller builds a config without
        // it — so this must not be an exception the caller has to catch.
        assertNull(store.ensure())
        assertEquals(RuleSetFailure.Reason.DOWNLOAD_FAILED, store.failure.value?.reason)
    }

    @Test
    fun `half a preset is no preset`() = runBlocking {
        // geosite succeeds, geoip does not.
        val store = store { url ->
            if (url.contains("geosite")) honest(url) else throw IOException("offline")
        }

        // Routing domestic names direct without also catching their addresses
        // would send that traffic abroad, which is worse than no preset.
        assertNull(store.ensure())
        assertNotNull(store.failure.value)
    }

    @Test
    fun `a zero-length file is refetched, not trusted`() = runBlocking {
        val dir = temp.root
        dir.mkdirs()
        File(dir, "geosite-cn.srs").writeBytes(ByteArray(0))

        val store = store { url -> honest(url) }
        val result = store.ensure()

        assertNotNull(result)
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
