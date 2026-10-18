package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.LeakCheck
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import com.justnpcx.starveil.core.model.LeakCheckRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Whether the tunnel the Kill Switch blocks with is standing right now.
 *
 * An interface so the check is testable without a device.
 */
fun interface TunnelProbe {
    fun isUp(): Boolean
}

/**
 * The leak self-check.
 *
 * It cannot be a connection test. This app is excluded from its own tunnel, so
 * its own sockets always leave by the physical interface — a probe would
 * report a leak every single time, whatever the Kill Switch was doing.
 *
 * What it *can* observe is whether the tunnel is standing, and that is exactly
 * the failure worth catching: if the policy says "block" while nothing is
 * holding traffic back, traffic is going out unproxied. That is a leak.
 *
 * "Blocked" is now a running kernel with a block-only config, so the question
 * "is the tunnel forwarding?" is asked of [KernelBridge.blocking] rather than
 * inferred from the kernel being absent. A tun that is up and refusing is
 * exactly as blocking as no tun at all, and this is the difference between
 * reporting a working Kill Switch as a leak and not.
 *
 * The reverse is not a proof — a standing tunnel that forwards is a different
 * failure this cannot see. So the record says "found a leak", never "is safe".
 */
@Singleton
class LeakCheckRunner @Inject constructor(
    private val preferences: PreferencesRepository,
    private val kernel: KernelBridge,
    private val tunnel: TunnelProbe,
) {

    suspend fun run(): LeakCheckRecord {
        val mode = preferences.killSwitchMode.first()
        // "Forwarding", not merely "running": a blocking kernel runs too.
        val forwarding = kernel.state.value == KernelState.Running && !kernel.blocking.value
        // Unproxied traffic can leave when nothing is holding the tun up. A
        // blocking kernel holds it up, so this stays false while blocked.
        val trafficAllowed = !tunnel.isUp()
        val record = LeakCheckRecord(
            checkedAtEpochMs = System.currentTimeMillis(),
            leaking = LeakCheck.isLeaking(mode, forwarding, trafficAllowed),
        )
        preferences.recordLeakCheck(record)
        return record
    }
}

/**
 * The real probe: is our VpnService holding a tun?
 *
 * A point-in-time read of a field another thread owns, so it can be stale by
 * the time the caller acts on it: `current` is set in `onStartCommand` and
 * cleared in `onDestroy`, while this runs on whatever coroutine asked. The
 * field is volatile, so the read is not torn — but the *state* can change
 * between the read and the decision.
 *
 * That is acceptable here and only here, because of what the verdict is
 * allowed to say: "a leak was found" when the policy says block and nothing
 * is holding the tunnel. A stale `null` can produce a false alarm (the
 * service was starting), never a false "safe" — and [LeakCheckRunner] never
 * reports safe. If this ever grows into a gate that *permits* something, it
 * needs to be re-read under the service's own lock, not sampled.
 */
class VpnServiceTunnelProbe @Inject constructor() : TunnelProbe {
    override fun isUp(): Boolean = StarveilVpnService.current != null
}
