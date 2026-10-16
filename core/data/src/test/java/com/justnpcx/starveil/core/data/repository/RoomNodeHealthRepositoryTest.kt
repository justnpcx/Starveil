package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.LatencySampleDao
import com.justnpcx.starveil.core.data.entity.LatencySampleEntity
import com.justnpcx.starveil.core.model.HEALTH_WINDOW_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory stand-in for the Room DAO, mirroring the queries' semantics.
 *
 * The repository only depends on the interface, so the record → prune →
 * observe path is exercised here without an emulator. Pruning is the point of
 * these tests, and pruning is exactly what a fake that "accepted writes and
 * kept none of them" would hide.
 */
private class FakeLatencySampleDao : LatencySampleDao {
    private val rows = mutableListOf<LatencySampleEntity>()

    override fun observeSince(sinceMs: Long): Flow<List<LatencySampleEntity>> = flow {
        emit(rows.filter { it.atMs >= sinceMs }.sortedBy { it.atMs })
    }

    override suspend fun insert(entity: LatencySampleEntity) {
        // The real insert is REPLACE on (profileId, atMs).
        rows.removeAll { it.profileId == entity.profileId && it.atMs == entity.atMs }
        rows += entity
    }

    override suspend fun deleteOlderThan(cutoffMs: Long) {
        rows.removeAll { it.atMs < cutoffMs }
    }

    override suspend fun deleteForProfile(profileId: Long) {
        rows.removeAll { it.profileId == profileId }
    }

    override suspend fun trimToCap(profileId: Long, keep: Int) {
        val mine = rows.filter { it.profileId == profileId }.sortedByDescending { it.atMs }
        val drop = mine.drop(keep).map { it.atMs }.toSet()
        rows.removeAll { it.profileId == profileId && it.atMs in drop }
    }

    fun all(): List<LatencySampleEntity> = rows.toList()
}

class RoomNodeHealthRepositoryTest {

    private val dao = FakeLatencySampleDao()
    private val repo = RoomNodeHealthRepository(dao)

    private val now = 1_000_000_000L

    @Test
    fun `a recorded probe comes back as a domain sample`() = runBlocking {
        repo.record(profileId = 7L, latencyMs = 42L, atMs = now)

        val samples = repo.observeSince(now - HEALTH_WINDOW_MS).first()
        assertEquals(1, samples.size)
        assertEquals(42L, samples.single().latencyMs)
        assertEquals(now, samples.single().atMs)
        assertTrue(samples.single().reachable)
    }

    @Test
    fun `a failed probe is kept, not skipped`() = runBlocking {
        // Loss has to be visible in the curve, so a timeout is stored.
        repo.record(profileId = 7L, latencyMs = -1L, atMs = now)

        val sample = repo.observeSince(now - HEALTH_WINDOW_MS).first().single()
        assertEquals(-1L, sample.latencyMs)
        assertFalse(sample.reachable)
    }

    @Test
    fun `recording prunes samples older than the window`() = runBlocking {
        repo.record(profileId = 7L, latencyMs = 10L, atMs = now - HEALTH_WINDOW_MS - 1)
        repo.record(profileId = 7L, latencyMs = 20L, atMs = now)

        // The stale one is gone from the store entirely, not merely filtered
        // out of the query — otherwise the table grows forever.
        //
        // This also pins that pruning keys off the *sample's* time: `now` here
        // is far from the wall clock, so a cutoff taken from
        // System.currentTimeMillis() would have deleted both rows.
        assertEquals(listOf(20L), dao.all().map { it.latencyMs })
    }

    @Test
    fun `the per-node cap bounds a backwards clock`() = runBlocking {
        // A clock that jumps back makes age-pruning's cutoff land in the past,
        // so nothing ages out. The count cap is what still holds.
        repeat(200) { index ->
            repo.record(profileId = 7L, latencyMs = index.toLong(), atMs = 1_000L + index)
        }

        val kept = dao.all().filter { it.profileId == 7L }
        assertEquals(120, kept.size)
        // The newest survive.
        assertEquals(199L, kept.maxOf { it.latencyMs })
    }

    @Test
    fun `the cap is per node, not global`() = runBlocking {
        repeat(150) { index ->
            repo.record(profileId = 1L, latencyMs = index.toLong(), atMs = 1_000L + index)
        }
        repo.record(profileId = 2L, latencyMs = 5L, atMs = 2_000L)

        assertEquals(120, dao.all().count { it.profileId == 1L })
        assertEquals(1, dao.all().count { it.profileId == 2L })
    }

    @Test
    fun `forget drops one node's history and leaves the others`() = runBlocking {
        repo.record(profileId = 1L, latencyMs = 10L, atMs = now)
        repo.record(profileId = 2L, latencyMs = 20L, atMs = now)

        repo.forget(1L)

        val samples = repo.observeSince(now - HEALTH_WINDOW_MS).first()
        assertEquals(listOf(20L), samples.map { it.latencyMs })
    }

    @Test
    fun `a re-probe at the same instant replaces rather than duplicates`() = runBlocking {
        repo.record(profileId = 7L, latencyMs = 10L, atMs = now)
        repo.record(profileId = 7L, latencyMs = 99L, atMs = now)

        assertEquals(listOf(99L), dao.all().map { it.latencyMs })
    }
}
