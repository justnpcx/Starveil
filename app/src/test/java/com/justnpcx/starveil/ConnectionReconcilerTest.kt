package com.justnpcx.starveil

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app concludes when the process and the phone disagree.
 *
 * The case that matters is the third one: a tunnel the system kept alive across
 * a process death. Nothing reads it, so the phone is offline while the UI says
 * "not connected" — and the next connect fails on the cache database the dead
 * daemon still holds.
 */
class ConnectionReconcilerTest {

    @Test
    fun `nothing connected is left alone`() = runBlocking {
        val kernel = FakeKernel(tunnel = false, initialState = KernelState.Stopped)

        assertEquals(ConnectionReconciler.Outcome.Consistent, ConnectionReconciler(kernel).run())
        assertEquals(0, kernel.releases)
        assertEquals(0, kernel.stops)
    }

    @Test
    fun `a tunnel with a kernel is a live connection and is not touched`() = runBlocking {
        // This process being new is not a reason to break a working tunnel.
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Running)

        assertEquals(ConnectionReconciler.Outcome.Connected, ConnectionReconciler(kernel).run())
        assertEquals(0, kernel.releases)
        assertEquals(0, kernel.stops)
    }

    @Test
    fun `a tunnel with no kernel is released`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped)

        assertEquals(
            ConnectionReconciler.Outcome.OrphanReleased,
            ConnectionReconciler(kernel).run(),
        )
        assertEquals(1, kernel.releases)
        assertFalse(kernel.tunnel)
    }

    @Test
    fun `a kernel with no tunnel is stopped`() = runBlocking {
        // It cannot be forwarding anything, and the next connect would try to
        // start a second one.
        val kernel = FakeKernel(tunnel = false, initialState = KernelState.Running)

        assertEquals(
            ConnectionReconciler.Outcome.KernelWithoutTunnel,
            ConnectionReconciler(kernel).run(),
        )
        assertEquals(1, kernel.stops)
    }

    @Test
    fun `an orphan that could not be released is not reported as released`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped, releaseSucceeds = false)

        assertEquals(ConnectionReconciler.Outcome.Consistent, ConnectionReconciler(kernel).run())
        assertTrue("the tunnel is still there, so the state must say so", kernel.tunnel)
    }

    @Test
    fun `running twice does nothing the second time`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped)
        val reconciler = ConnectionReconciler(kernel)

        reconciler.run()
        assertEquals(ConnectionReconciler.Outcome.Consistent, reconciler.run())
        assertEquals(1, kernel.releases)
    }

    @Test
    fun `a released orphan is restored when the user asked for it`() = runBlocking {
        // A phone that reboots with always-on VPN: the tunnel came back, the
        // process did not. Releasing it is the honest state; bringing it back
        // is what the user asked for before the reboot.
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped)
        val reconciler = ConnectionReconciler(kernel)

        val outcome = reconciler.run()

        assertEquals(ConnectionReconciler.Outcome.OrphanReleased, outcome)
        assertTrue(reconciler.shouldRestore(outcome, autoReconnect = true, hasTarget = true))
    }

    @Test
    fun `nothing is restored when auto-reconnect is off`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped)
        val reconciler = ConnectionReconciler(kernel)

        assertFalse(reconciler.shouldRestore(reconciler.run(), autoReconnect = false, hasTarget = true))
    }

    @Test
    fun `nothing is restored without a target`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Stopped)
        val reconciler = ConnectionReconciler(kernel)

        assertFalse(reconciler.shouldRestore(reconciler.run(), autoReconnect = true, hasTarget = false))
    }

    @Test
    fun `a live connection is never restored again`() = runBlocking {
        val kernel = FakeKernel(tunnel = true, initialState = KernelState.Running)
        val reconciler = ConnectionReconciler(kernel)

        assertFalse(reconciler.shouldRestore(reconciler.run(), autoReconnect = true, hasTarget = true))
    }
}


/** The four things the reconciler asks of a bridge, and nothing else. */
private class FakeKernel(
    var tunnel: Boolean,
    initialState: KernelState,
    private val releaseSucceeds: Boolean = true,
) : KernelBridge {

    var releases = 0
        private set
    var stops = 0
        private set

    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<KernelState> = mutableState

    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()
    override val blocking: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun tunnelPresent(): Boolean = tunnel

    override suspend fun releaseOrphanTunnel(): Boolean {
        releases++
        if (!releaseSucceeds) return false
        tunnel = false
        mutableState.value = KernelState.Stopped
        return true
    }

    override suspend fun start(config: String): Result<Unit> {
        mutableState.value = KernelState.Running
        tunnel = true
        return Result.success(Unit)
    }

    override suspend fun block(): Result<Unit> = Result.success(Unit)

    override suspend fun stop(): Result<Unit> {
        stops++
        mutableState.value = KernelState.Stopped
        tunnel = false
        return Result.success(Unit)
    }

    override suspend fun queryStats(): TrafficStats = TrafficStats()

    override suspend fun version(): String = "test"
}