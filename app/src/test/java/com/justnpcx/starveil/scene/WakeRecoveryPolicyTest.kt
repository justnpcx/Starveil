package com.justnpcx.starveil.scene

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wake policy, without a phone to wake. */
class WakeRecoveryPolicyTest {

    @Test
    fun `two dead probes on a running tunnel rebuild it`() {
        assertTrue(
            shouldRebuildAfterWake(
                firstProbeOk = false,
                secondProbeOk = false,
                kernelRunning = true,
                autoReconnect = true,
            ),
        )
    }

    @Test
    fun `one good probe is enough to leave the tunnel alone`() {
        assertFalse(shouldRebuildAfterWake(false, true, kernelRunning = true, autoReconnect = true))
        assertFalse(shouldRebuildAfterWake(true, false, kernelRunning = true, autoReconnect = true))
        assertFalse(shouldRebuildAfterWake(true, true, kernelRunning = true, autoReconnect = true))
    }

    @Test
    fun `nothing to rebuild when the kernel is not running`() {
        assertFalse(shouldRebuildAfterWake(false, false, kernelRunning = false, autoReconnect = true))
    }

    @Test
    fun `automatic reconnection off means hands off`() {
        assertFalse(shouldRebuildAfterWake(false, false, kernelRunning = true, autoReconnect = false))
    }
}
