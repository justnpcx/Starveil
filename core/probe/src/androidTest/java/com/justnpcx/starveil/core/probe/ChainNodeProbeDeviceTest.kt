package com.justnpcx.starveil.core.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnpcx.starveil.core.data.geo.DohResolver
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real libbox, real sockets, no mocked detour. Host is supplied by the runner. */
@RunWith(AndroidJUnit4::class)
class ChainNodeProbeDeviceTest {
    @Test
    fun loopbackExitIsReachableOnlyThroughTheFront() = runBlocking {
        val host = InstrumentationRegistry.getArguments().getString("chain_host")
            ?: error("Pass -Pandroid.testInstrumentationRunnerArguments.chain_host=<host>")
        val front = node(host, FRONT_PORT, "front")
        // The exit listens on the front server's own loopback. The Android
        // device cannot reach it directly; success therefore proves detour.
        val exit = node("127.0.0.1", EXIT_PORT, "exit")
        val direct = TcpNodeProbe(DohResolver())
        assertTrue("the loopback exit must not be directly reachable", direct.probe(
            exit.server,
            exit.port,
            1_000,
        ) !is ProbeResult.Reached)
        assertTrue("the front itself must be reachable", direct.probe(
            front.server,
            front.port,
            3_000,
        ) is ProbeResult.Reached)

        val probe = SingBoxChainNodeProbe(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            configBuilder = ConfigBuilder(ProtocolRegistry.default()),
        )
        val result = probe.probe(exit, front, timeoutMs = 10_000)

        assertTrue("expected the complete front -> loopback-exit chain, got $result",
            result is ProbeResult.Reached)
    }

    private fun node(server: String, port: Int, name: String) = Profile(
        name = name,
        type = ProfileType.SHADOWSOCKS,
        server = server,
        port = port,
        settings = ShadowsocksSettings(method = "aes-128-gcm", password = "chain-test-only"),
    )

    private companion object {
        const val FRONT_PORT = 18381
        const val EXIT_PORT = 18382
    }
}
