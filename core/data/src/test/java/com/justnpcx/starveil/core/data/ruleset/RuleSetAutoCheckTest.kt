package com.justnpcx.starveil.core.data.ruleset

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The weekly throttle.
 *
 * Two things have to hold: a launch inside the window touches no network, and
 * a check that fails still counts as done — otherwise an unreachable mirror
 * becomes one request per app start.
 */
class RuleSetAutoCheckTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val warnings = mutableListOf<String>()

    private fun many(): ByteArray = javaClass.getResourceAsStream("/domain-many.srs")!!.readBytes()

    private fun harness(
        lastCheckedAt: Long,
        nowMs: Long,
        download: suspend (String) -> ByteArray = { many() },
    ): Triple<RuleSetAutoCheck, () -> Long, () -> Int> {
        var recorded = -1L
        var fetches = 0
        val updater = RuleSetUpdater(
            directory = temp.root,
            installer = RuleSetInstaller(temp.root, warnings::add),
            versionOf = { RuleSetVersion("c2".repeat(20), "2026-11-01T00:00:00Z") },
            download = { url -> fetches += 1; download(url) },
            warn = warnings::add,
        )
        val check = RuleSetAutoCheck(
            updater = updater,
            lastCheckedAt = { lastCheckedAt },
            recordCheckedAt = { recorded = it },
            nowMs = { nowMs },
            warn = warnings::add,
        )
        return Triple(check, { recorded }, { fetches })
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `inside the window nothing is fetched`() = runBlocking {
        val (check, _, fetches) = harness(lastCheckedAt = 100 * day, nowMs = 103 * day)

        assertNull(check.runIfDue())
        assertEquals(0, fetches())
    }

    @Test
    fun `a week later the check runs and the timestamp moves`() = runBlocking {
        val (check, recorded, fetches) = harness(lastCheckedAt = 100 * day, nowMs = 108 * day)

        val result = check.runIfDue()

        assertNotNull(result)
        assertTrue(result is RuleSetUpdater.Result.Updated)
        assertEquals(108 * day, recorded())
        assertEquals(3, fetches())
    }

    @Test
    fun `a failed check still counts as checked`() = runBlocking {
        val (check, recorded, _) = harness(
            lastCheckedAt = 100 * day,
            nowMs = 108 * day,
            download = { throw java.io.IOException("offline") },
        )

        val result = check.runIfDue()

        assertTrue(result is RuleSetUpdater.Result.Failed)
        // Recorded anyway: the next attempt is a week away, not the next launch.
        assertEquals(108 * day, recorded())
        assertTrue(warnings.any { it.contains("next attempt in a week") })
    }

    @Test
    fun `a manual check ignores the window`() = runBlocking {
        val (check, recorded, fetches) = harness(lastCheckedAt = 100 * day, nowMs = 101 * day)

        val result = check.runNow()

        assertTrue(result is RuleSetUpdater.Result.Updated)
        assertEquals(3, fetches())
        assertEquals(101 * day, recorded())
    }
}
