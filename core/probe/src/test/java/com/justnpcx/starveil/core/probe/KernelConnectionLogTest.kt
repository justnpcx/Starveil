package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.kernel.ConnectionEventKind
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.model.ConnectionDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fold that turns the kernel's three events into one row.
 *
 * Two behaviours matter and neither is visible from the screen: an update must
 * replace its row rather than stack a second one, and a close must leave the
 * row with a close time instead of deleting it.
 */
class KernelConnectionLogTest {

    @Test
    fun `an update replaces its row instead of adding one`() {
        val created = KernelConnectionLog.apply(emptyList(), event(id = "a"), nowMs = 1_000)
        val updated = KernelConnectionLog.apply(
            created,
            event(id = "a", kind = ConnectionEventKind.UPDATE, up = 500, down = 2_000),
            nowMs = 2_000,
        )
        assertEquals(1, updated.size)
        assertEquals(500, updated.single().uplinkBytes)
        assertEquals(2_000, updated.single().downlinkBytes)
    }

    @Test
    fun `an update adds its delta to the total`() {
        val created = KernelConnectionLog.apply(emptyList(), event(id = "a", up = 100, down = 200), nowMs = 1)
        val first = KernelConnectionLog.apply(
            created,
            event(id = "a", kind = ConnectionEventKind.UPDATE, deltaUp = 30, deltaDown = 40),
            nowMs = 2,
        )
        val second = KernelConnectionLog.apply(
            first,
            event(id = "a", kind = ConnectionEventKind.UPDATE, deltaUp = 5, deltaDown = 7),
            nowMs = 3,
        )
        assertEquals(135, second.single().uplinkBytes)
        assertEquals(247, second.single().downlinkBytes)
    }

    @Test
    fun `a close keeps the row and marks it closed`() {
        val created = KernelConnectionLog.apply(emptyList(), event(id = "a"), nowMs = 1_000)
        val closed = KernelConnectionLog.apply(
            created,
            event(id = "a", kind = ConnectionEventKind.CLOSE, closedAt = 4_000),
            nowMs = 4_000,
        )
        val row = closed.single()
        assertFalse(row.open)
        assertEquals(4_000, row.closedAtMs)
        assertEquals(3_000, row.durationMs(nowMs = 9_999))
    }

    @Test
    fun `a close without counters keeps the last bytes seen`() {
        val created = KernelConnectionLog.apply(emptyList(), event(id = "a", up = 10, down = 20), nowMs = 1)
        val closed = KernelConnectionLog.apply(
            created,
            event(id = "a", kind = ConnectionEventKind.CLOSE, closedAt = 2_000),
            nowMs = 2_000,
        )
        assertEquals(10, closed.single().uplinkBytes)
        assertEquals(20, closed.single().downlinkBytes)
    }

    @Test
    fun `an unknown connection in an update is still shown`() {
        val rows = KernelConnectionLog.apply(
            emptyList(),
            event(id = "late", kind = ConnectionEventKind.UPDATE, up = 1),
            nowMs = 1_000,
        )
        assertEquals("late", rows.single().id)
    }

    @Test
    fun `the ring stops at its capacity`() {
        val rows = (0 until KernelConnectionLog.RING_SIZE + 10).fold(emptyList<ConnectionDetail>()) { acc, i ->
            KernelConnectionLog.apply(
                acc,
                event(id = "c$i", kind = ConnectionEventKind.CLOSE, closedAt = i.toLong() + 1),
                nowMs = i.toLong(),
            )
        }
        assertEquals(KernelConnectionLog.RING_SIZE, rows.size)
        assertTrue(rows.none { it.id == "c0" })
        assertEquals("c${KernelConnectionLog.RING_SIZE + 9}", rows.first().id)
    }

    @Test
    fun `open rows come before closed ones`() {
        val open = event(id = "open")
        val closed = event(id = "closed", kind = ConnectionEventKind.CLOSE, closedAt = 5_000)
        val rows = KernelConnectionLog.apply(
            KernelConnectionLog.apply(emptyList(), closed, nowMs = 1_000),
            open,
            nowMs = 6_000,
        )
        assertEquals(listOf("open", "closed"), rows.map { it.id })
    }

    private fun event(
        id: String,
        kind: ConnectionEventKind = ConnectionEventKind.NEW,
        up: Long = 0,
        down: Long = 0,
        deltaUp: Long = 0,
        deltaDown: Long = 0,
        closedAt: Long = 0,
    ) = KernelConnection(
        id = id,
        destination = "1.2.3.4:443",
        domain = "example.com",
        rule = "geosite:cn",
        outbound = "direct",
        outboundType = "direct",
        createdAtMs = 1_000,
        event = kind,
        appPackage = "com.example.app",
        uplinkBytes = up,
        downlinkBytes = down,
        uplinkDelta = deltaUp,
        downlinkDelta = deltaDown,
        closedAtMs = closedAt,
        network = "tcp",
        protocol = "tls",
    )
}
