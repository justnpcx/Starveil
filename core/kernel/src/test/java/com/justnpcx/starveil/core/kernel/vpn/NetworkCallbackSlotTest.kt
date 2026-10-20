package com.justnpcx.starveil.core.kernel.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lifetime rule for the service's default-network callback.
 *
 * The leak this guards against is invisible in a normal run: the callback works
 * fine, and the service it holds onto is simply never collected. So the rule is
 * tested here rather than trusted — exactly one registration, exactly one
 * unregistration, and both idempotent because `onRevoke()` and `onDestroy()`
 * both call release.
 */
class NetworkCallbackSlotTest {

    private var registrations = 0
    private var unregistrations = 0
    private var registerResult = true

    private val slot = NetworkCallbackSlot(
        register = { registrations++; registerResult },
        unregister = { unregistrations++ },
    )

    @Test
    fun `acquire registers once and a second acquire is a no-op`() {
        assertTrue(slot.acquire())
        assertFalse(slot.acquire())
        assertEquals(1, registrations)
    }

    @Test
    fun `release unregisters once and a second release is a no-op`() {
        slot.acquire()

        assertTrue(slot.release())
        assertFalse(slot.release())
        assertEquals(1, unregistrations)
        assertFalse(slot.isActive)
    }

    @Test
    fun `releasing something that was never registered does not touch the platform`() {
        assertFalse(slot.release())
        assertEquals(0, unregistrations)
    }

    @Test
    fun `a released slot can be acquired again`() {
        slot.acquire()
        slot.release()

        assertTrue(slot.acquire())
        assertEquals(2, registrations)
        assertEquals(1, unregistrations)
    }

    @Test
    fun `a failed registration leaves the slot free to retry`() {
        registerResult = false

        assertFalse(slot.acquire())
        assertFalse(slot.isActive)

        registerResult = true
        assertTrue(slot.acquire())
        assertEquals(2, registrations)
    }
}
