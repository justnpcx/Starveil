package com.justnpcx.starveil.latency

import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.WEIGHT_FULL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background sweep's decisions: which nodes it probes, which it leaves
 * alone, and how many sockets it holds at once.
 *
 * The handshake itself belongs to [NodeProbe] and is faked here.
 */
class NodeHealthBatchProbeTest {

    private val now = 1_000_000L

    private fun node(id: Long, type: ProfileType = ProfileType.VLESS) = Profile(
        id = id,
        name = "node-$id",
        type = type,
        server = "n$id.example",
        port = 443,
    )

    private fun probe(
        profiles: List<Profile>,
        health: BatchHealth = BatchHealth(),
        results: (Profile) -> ProbeResult = { ProbeResult.Reached(50) },
    ) = NodeHealthBatchProbe(
        probe = BatchProbe(results),
        profiles = BatchProfiles(profiles),
        health = health,
    )

    @Test
    fun `every node is probed, not only the selected one`() = runBlocking {
        val health = BatchHealth()
        val count = probe(listOf(node(1), node(2), node(3)), health).run(now)

        // The whole point of the sweep: three nodes used to mean one curve.
        assertEquals(3, count)
        assertEquals(setOf(1L, 2L, 3L), health.recorded.map { it.profileId }.toSet())
        assertTrue(health.recorded.all { it.weightTenths == WEIGHT_FULL })
    }

    @Test
    fun `a wireguard endpoint is not probed`() = runBlocking {
        val health = BatchHealth()
        val count = probe(
            listOf(node(1), node(2, ProfileType.WIREGUARD)),
            health,
        ).run(now)

        // UDP only: a TCP handshake could only fail, and recording that would
        // be a lie about a node that works.
        assertEquals(1, count)
        assertEquals(listOf(1L), health.recorded.map { it.profileId })
    }

    @Test
    fun `a node in time-out is skipped until it expires`() = runBlocking {
        val health = BatchHealth()
        // Three full failures a minute ago: still parked.
        health.samples[2L] = (0..2).map {
            LatencySample(atMs = now - 60_000 + it, latencyMs = LATENCY_UNREACHABLE)
        }

        val count = probe(listOf(node(1), node(2)), health).run(now)

        assertEquals(1, count)
        assertEquals(listOf(1L), health.recorded.map { it.profileId })
    }

    @Test
    fun `a parked node is probed again once the time-out has passed`() = runBlocking {
        val health = BatchHealth()
        // The window is 30 minutes; a cooldown that started 31 minutes ago has
        // expired, and the samples are outside the window as well.
        health.samples[2L] = (0..2).map {
            LatencySample(atMs = now - 31 * 60_000 + it, latencyMs = LATENCY_UNREACHABLE)
        }

        val count = probe(listOf(node(1), node(2)), health).run(now)

        assertEquals(2, count)
    }

    @Test
    fun `a probe that does not come back is recorded as loss`() = runBlocking {
        val health = BatchHealth()
        probe(listOf(node(1)), health) { ProbeResult.TimedOut }.run(now)

        assertEquals(LATENCY_UNREACHABLE, health.recorded.single().latencyMs)
    }

    @Test
    fun `no more than four sockets are open at once`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val health = BatchHealth()
        val nodes = (1L..12L).map { node(it) }

        val probe = NodeHealthBatchProbe(
            probe = object : NodeProbe {
                override suspend fun probe(server: String, port: Int, timeoutMs: Int): ProbeResult {
                    val now = inFlight.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    delay(5)
                    inFlight.decrementAndGet()
                    return ProbeResult.Reached(10)
                }
            },
            profiles = BatchProfiles(nodes),
            health = health,
        )
        probe.run(now)

        assertEquals(12, health.recorded.size)
        assertTrue("peak was ${peak.get()}", peak.get() <= NodeHealthBatchProbe.CONCURRENCY)
    }

    @Test
    fun `nothing to probe is not an error`() = runBlocking {
        assertEquals(0, probe(emptyList()).run(now))
    }
}

private class BatchProbe(private val results: (Profile) -> ProbeResult) : NodeProbe {
    override suspend fun probe(server: String, port: Int, timeoutMs: Int): ProbeResult =
        // The fake is keyed by server so a test can vary the answer per node.
        results(Profile(id = 0, name = "", type = ProfileType.VLESS, server = server, port = port))
}

private class BatchProfiles(private val all: List<Profile>) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> = flowOf(all)
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Profile? = all.firstOrNull { it.id == id }
    override suspend fun upsert(profile: Profile): Long = profile.id
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteByGroup(groupId: Long) = Unit
    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = Unit
}

private class BatchHealth : NodeHealthRepository {
    data class Recorded(
        val profileId: Long,
        val latencyMs: Long,
        val atMs: Long,
        val weightTenths: Int,
    )

    val recorded = mutableListOf<Recorded>()
    val samples = mutableMapOf<Long, List<LatencySample>>()

    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = flowOf(samples)

    override suspend fun record(
        profileId: Long,
        latencyMs: Long,
        atMs: Long,
        weightTenths: Int,
    ) {
        recorded += Recorded(profileId, latencyMs, atMs, weightTenths)
    }

    override suspend fun forget(profileId: Long) = Unit
}
