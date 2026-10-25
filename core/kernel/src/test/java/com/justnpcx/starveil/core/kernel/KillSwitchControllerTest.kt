package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.model.KillSwitchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KillSwitchControllerTest {

    @Test
    fun `OFF never blocks`() {
        assertEquals(TrafficPolicy.ALLOW, KillSwitchController.policyFor(KillSwitchMode.OFF, connected = true))
        assertEquals(TrafficPolicy.ALLOW, KillSwitchController.policyFor(KillSwitchMode.OFF, connected = false))
    }

    @Test
    fun `BLOCK_ON_DISCONNECT allows only while connected`() {
        assertEquals(
            TrafficPolicy.ALLOW,
            KillSwitchController.policyFor(KillSwitchMode.BLOCK_ON_DISCONNECT, connected = true),
        )
        assertEquals(
            TrafficPolicy.BLOCK,
            KillSwitchController.policyFor(KillSwitchMode.BLOCK_ON_DISCONNECT, connected = false),
        )
    }

    @Test
    fun `ALWAYS_BLOCK blocks even while connected`() {
        assertEquals(TrafficPolicy.BLOCK, KillSwitchController.policyFor(KillSwitchMode.ALWAYS_BLOCK, connected = true))
        assertEquals(TrafficPolicy.BLOCK, KillSwitchController.policyFor(KillSwitchMode.ALWAYS_BLOCK, connected = false))
    }

    @Test
    fun `mode parses from storage and falls back to OFF`() {
        assertEquals(KillSwitchMode.ALWAYS_BLOCK, KillSwitchMode.fromStorage("ALWAYS_BLOCK"))
        assertEquals(KillSwitchMode.OFF, KillSwitchMode.fromStorage("garbage"))
        assertEquals(KillSwitchMode.OFF, KillSwitchMode.fromStorage(null))
    }
}

class LeakCheckTest {

    @Test
    fun `a leak is when the policy says block but traffic flows`() {
        assertTrue(LeakCheck.isLeaking(KillSwitchMode.BLOCK_ON_DISCONNECT, connected = false, trafficAllowed = true))
        assertTrue(LeakCheck.isLeaking(KillSwitchMode.ALWAYS_BLOCK, connected = true, trafficAllowed = true))
    }

    @Test
    fun `no leak when traffic matches the policy`() {
        assertFalse(LeakCheck.isLeaking(KillSwitchMode.OFF, connected = false, trafficAllowed = true))
        assertFalse(LeakCheck.isLeaking(KillSwitchMode.BLOCK_ON_DISCONNECT, connected = false, trafficAllowed = false))
        assertFalse(LeakCheck.isLeaking(KillSwitchMode.ALWAYS_BLOCK, connected = true, trafficAllowed = false))
    }

    @Test
    fun `a start with no kernel puts the block back when the mode asks for it`() {
        assertTrue(KillSwitchController.shouldBlockOnStart(KillSwitchMode.BLOCK_ON_DISCONNECT, kernelRunning = false))
        assertTrue(KillSwitchController.shouldBlockOnStart(KillSwitchMode.ALWAYS_BLOCK, kernelRunning = false))
        assertFalse(KillSwitchController.shouldBlockOnStart(KillSwitchMode.OFF, kernelRunning = false))
    }

    @Test
    fun `a start with a kernel running leaves it alone`() {
        assertFalse(KillSwitchController.shouldBlockOnStart(KillSwitchMode.BLOCK_ON_DISCONNECT, kernelRunning = true))
        assertFalse(KillSwitchController.shouldBlockOnStart(KillSwitchMode.ALWAYS_BLOCK, kernelRunning = true))
    }
}
