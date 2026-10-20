package com.justnpcx.starveil.core.kernel

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The serialisation the kernel bridge relies on.
 *
 * The transitions themselves need libbox, so they cannot run here; what can be
 * pinned is the property they are built on — two of them never overlap, however
 * many are started at once, and a failed one does not keep the gate.
 */
class LifecycleGateTest {

    @Test
    fun `fifty concurrent transitions never overlap`() = runBlocking {
        val gate = LifecycleGate()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val completed = AtomicInteger()

        (1..50).map {
            async(Dispatchers.Default) {
                gate.exclusive {
                    val now = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    // Long enough that an unserialised implementation would
                    // certainly be caught holding two at once.
                    delay(2)
                    completed.incrementAndGet()
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals("two transitions ran at the same time", 1, peak.get())
        assertEquals(50, completed.get())
    }

    @Test
    fun `a failed transition releases the gate`() = runBlocking {
        val gate = LifecycleGate()

        runCatching { gate.exclusive<Unit> { error("start failed") } }

        val next = gate.exclusive { "next" }
        assertEquals("the gate stayed locked after a failed transition", "next", next)
    }
}
