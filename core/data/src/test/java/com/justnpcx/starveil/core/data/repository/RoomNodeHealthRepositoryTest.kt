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

    override fun observeAll(): Flow<List<LatencySampleEntity>> = flow {
        emit(rows.sortedBy { it.atMs })
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

    var batchCalls = 0
        private set

    override suspend fun recordSample(
        entity: LatencySampleEntity,
        cutoffMs: Long,
        keep: Int,
    ) {
        batchCalls++
        super.recordSample(entity, cutoffMs, keep)
    }

    fun all(): List<LatencySampleEntity> = rows.toList()
}

class RoomNodeHealthRepositoryTest {

    private val dao = FakeLatencySampleDao()
    private val repo = RoomNodeHealthRepository(dao)

    /**
     * A synthetic instant, far from the wall clock. Used for the store-level
     * tests, which must not depend on when they are run.
     */
    private val now = 1_000_000_000L

    @Test
    fun `a recorded probe comes back grouped under its node`() = runBlocking {
        val realNow = System.currentTimeMillis()
        repo.record(profileId = 7L, latencyMs = 42L, atMs = realNow)

        val sample = repo.observeAll().first().getValue(7L).single()
        assertEquals(42L, sample.latencyMs)
        assertEquals(realNow, sample.atMs)
        assertTrue(sample.reachable)
    }

    @Test
    fun `a failed probe is kept, not skipped`() = runBlocking {
        // Loss has to be visible in the curve, so a timeout is stored.
        repo.record(profileId = 7L, latencyMs = -1L, atMs = System.currentTimeMillis())

        val sample = repo.observeAll().first().getValue(7L).single()
        assertEquals(-1L, sample.latencyMs)
        assertFalse(sample.reachable)
    }

    @Test
    fun `a recorded probe takes the single-transaction path`() = runBlocking {
        // The repository must not go back to three separate DAO calls; the
        // fake inherits the composite and counts it.
        repo.record(profileId = 7L, latencyMs = 10L, atMs = now)

        assertEquals(1, dao.batchCalls)
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
        val realNow = System.currentTimeMillis()
        repo.record(profileId = 1L, latencyMs = 10L, atMs = realNow)
        repo.record(profileId = 2L, latencyMs = 20L, atMs = realNow)

        repo.forget(1L)

        assertEquals(listOf(20L), repo.observeAll().first().getValue(2L).map { it.latencyMs })
    }

    @Test
    fun `a re-probe at the same instant replaces rather than duplicates`() = runBlocking {
        repo.record(profileId = 7L, latencyMs = 10L, atMs = now)
        repo.record(profileId = 7L, latencyMs = 99L, atMs = now)

        assertEquals(listOf(99L), dao.all().map { it.latencyMs })
    }

    @Test
    fun `the read window drops what has aged out, keeping the edge`() {
        val byNode = windowSamples(
            rows = listOf(
                LatencySampleEntity(1L, now - HEALTH_WINDOW_MS - 1, 10L),
                // Exactly at the edge is inside: the window is 30 minutes
                // wide, and excluding the boundary would make it 29:59.999.
                LatencySampleEntity(1L, now - HEALTH_WINDOW_MS, 20L),
                LatencySampleEntity(1L, now, 30L),
                LatencySampleEntity(2L, now, 40L),
            ),
            nowMs = now,
        )

        assertEquals(listOf(20L, 30L), byNode.getValue(1L).map { it.latencyMs })
        assertEquals(listOf(40L), byNode.getValue(2L).map { it.latencyMs })
    }

    @Test
    fun `a node with nothing in the window is absent, not empty`() {
        // An empty list would draw a node as "collecting"; absent says "no
        // history", which is what it is.
        val byNode = windowSamples(
            rows = listOf(LatencySampleEntity(1L, now - HEALTH_WINDOW_MS - 1, 10L)),
            nowMs = now,
        )

        assertTrue(byNode.isEmpty())
    }

    @Test
    fun `each node's samples come back oldest first`() {
        // The EMA and "the latest probe" are both order-dependent, so the
        // grouping must not disturb the query's ordering.
        val byNode = windowSamples(
            rows = listOf(
                LatencySampleEntity(1L, 300L, 30L),
                LatencySampleEntity(2L, 100L, 10L),
                LatencySampleEntity(1L, 100L, 10L),
                LatencySampleEntity(1L, 200L, 20L),
            ),
            nowMs = 1_000L,
        )

        assertEquals(listOf(10L, 20L, 30L), byNode.getValue(1L).map { it.latencyMs })
    }

    @Test
    fun `a node whose window is empty keeps its last stored curve`() {
        // The app was closed for longer than the window: the rows are still on
        // disk, the window hides them, and the card used to say "collecting"
        // for the seconds it took to refill. The last samples are the last
        // thing actually measured, so they are what the first frame draws.
        val now = 1_000_000L
        val stale = (0 until 6).map {
            LatencySampleEntity(1L, now - HEALTH_WINDOW_MS - 60_000L + it * 1_000L, 20L + it)
        }
        val window = windowSamples(stale, nowMs = now)
        assertTrue(window.isEmpty())

        val carried = carryForward(stale, window)

        assertEquals((0 until 6).map { 20L + it }, carried.getValue(1L).map { it.latencyMs })
    }

    @Test
    fun `a curve that is already current is left alone`() {
        val now = 1_000_000L
        val rows = (0 until 6).map { LatencySampleEntity(1L, now - it * 1_000L, 30L + it) }
        val window = windowSamples(rows, nowMs = now)

        val carried = carryForward(rows, window)

        assertEquals(window.getValue(1L), carried.getValue(1L))
    }
}
