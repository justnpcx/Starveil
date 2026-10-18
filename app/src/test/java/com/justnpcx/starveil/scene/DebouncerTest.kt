package com.justnpcx.starveil.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The debounce: several events in the window, one action.
 *
 * Virtual time, because the real window is five seconds and a test that waits
 * for it is a test nobody runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncerTest {

    @Test
    fun `a burst of events produces one action`() = runTest {
        var runs = 0
        val debouncer = Debouncer(this, windowMs = 5_000)

        debouncer.submit { runs++ }
        advanceTimeBy(1_000)
        debouncer.submit { runs++ }
        advanceTimeBy(1_000)
        debouncer.submit { runs++ }
        runCurrent()

        // Still inside the window: nothing has run.
        assertEquals(0, runs)

        advanceTimeBy(5_001)
        assertEquals(1, runs)
    }

    @Test
    fun `events further apart than the window each run`() = runTest {
        var runs = 0
        val debouncer = Debouncer(this, windowMs = 5_000)

        debouncer.submit { runs++ }
        advanceTimeBy(6_000)
        debouncer.submit { runs++ }
        advanceTimeBy(6_000)

        assertEquals(2, runs)
    }

    @Test
    fun `the action sees the state at the end of the window`() = runTest {
        // The point of "latest wins": the action must not be the first event's
        // closure running late, it must be the last one's.
        val seen = mutableListOf<Int>()
        var state = 1
        val debouncer = Debouncer(this, windowMs = 5_000)

        debouncer.submit { seen += state }
        state = 2
        debouncer.submit { seen += state }
        state = 3
        debouncer.submit { seen += state }
        advanceTimeBy(5_001)

        assertEquals(listOf(3), seen)
    }
}
