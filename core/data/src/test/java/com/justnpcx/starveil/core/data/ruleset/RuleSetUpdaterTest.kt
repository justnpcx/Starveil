package com.justnpcx.starveil.core.data.ruleset

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One check, end to end: what gets fetched, what gets installed, and what a
 * second check does when the commit has not moved.
 */
class RuleSetUpdaterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val warnings = mutableListOf<String>()

    private fun many(): ByteArray = javaClass.getResourceAsStream("/domain-many.srs")!!.readBytes()

    private fun updater(
        head: RuleSetVersion? = RuleSetVersion("c1".repeat(20), "2026-11-01T00:00:00Z"),
        download: suspend (String) -> ByteArray = { many() },
    ) = RuleSetUpdater(
        directory = temp.root,
        installer = RuleSetInstaller(temp.root, warnings::add),
        versionOf = { head },
        download = download,
        warn = warnings::add,
    )

    @Test
    fun `a check installs every core file and records the version`() = runBlocking {
        val fetched = mutableListOf<String>()

        val result = updater(download = { url -> fetched += url; many() }).check()

        assertTrue(result is RuleSetUpdater.Result.Updated)
        assertEquals(3, (result as RuleSetUpdater.Result.Updated).files)
        assertEquals(3, fetched.size)
        // Addressed at the discovered commit, not at a branch.
        assertTrue(fetched.all { it.contains("c1c1c1c1") })
        assertEquals("2026-11-01", RuleSetInstaller(temp.root, warnings::add).readManifest()!!.version.date)
    }

    @Test
    fun `a second check at the same commit fetches nothing`() = runBlocking {
        updater().check()
        var fetched = 0

        val result = updater(download = { fetched += 1; many() }).check()

        assertTrue(result is RuleSetUpdater.Result.UpToDate)
        assertEquals("nothing refetched", 0, fetched)
    }

    @Test
    fun `one failed download installs nothing`() = runBlocking {
        val result = updater(download = { url ->
            if (url.contains("geoip")) throw java.io.IOException("offline")
            many()
        }).check()

        assertTrue(result is RuleSetUpdater.Result.Failed)
        assertNull(File(temp.root, "geosite-cn.srs").takeIf { it.isFile })
        assertNull(RuleSetInstaller(temp.root, warnings::add).readManifest())
    }

    @Test
    fun `a check with no version source installs nothing`() = runBlocking {
        var fetched = 0
        val result = updater(head = null, download = { fetched += 1; many() }).check()

        assertTrue(result is RuleSetUpdater.Result.Failed)
        assertEquals(0, fetched)
    }
}
