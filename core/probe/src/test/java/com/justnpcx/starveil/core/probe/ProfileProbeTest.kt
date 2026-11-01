package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileProbeTest {
    private val front = node(2, "front")
    private val exit = node(1, "exit").copy(frontNodeKey = front.nodeKey)

    @Test
    fun `a chained node is measured through its real detour`() = runTest {
        val direct = RecordingDirect(ProbeResult.Reached(20))
        val chain = RecordingChain(ProbeResult.Reached(145))

        val result = probeProfile(exit, 3_000, direct, chain) { listOf(exit, front) }

        assertEquals(ProbeResult.Reached(145), result)
        assertEquals(0, direct.calls)
        assertEquals(exit to front, chain.calledWith)
    }

    @Test
    fun `a dangling front follows the emitter and measures direct`() = runTest {
        val direct = RecordingDirect(ProbeResult.Reached(20))
        val chain = RecordingChain(ProbeResult.Reached(145))

        val result = probeProfile(exit, 3_000, direct, chain) { listOf(exit) }

        assertEquals(ProbeResult.Reached(20), result)
        assertEquals(1, direct.calls)
        assertEquals(0, chain.calls)
    }

    @Test
    fun `a WireGuard chain uses the real endpoint instead of a TCP handshake`() = runTest {
        val wgExit = exit.copy(type = ProfileType.WIREGUARD)
        val direct = RecordingDirect(ProbeResult.ConnectionFailed)
        val chain = RecordingChain(ProbeResult.Reached(160))

        val result = probeProfile(wgExit, 3_000, direct, chain) { listOf(wgExit, front) }

        assertEquals(ProbeResult.Reached(160), result)
        assertEquals(0, direct.calls)
        assertEquals(1, chain.calls)
    }

    private fun node(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
    )
}

private class RecordingDirect(private val result: ProbeResult) : NodeProbe {
    var calls = 0
    override suspend fun probe(server: String, port: Int, timeoutMs: Int): ProbeResult {
        calls++
        return result
    }
}

private class RecordingChain(private val result: ProbeResult) : ChainNodeProbe {
    var calls = 0
    var calledWith: Pair<Profile, Profile>? = null
    override suspend fun probe(exit: Profile, front: Profile, timeoutMs: Int): ProbeResult {
        calls++
        calledWith = exit to front
        return result
    }
}
