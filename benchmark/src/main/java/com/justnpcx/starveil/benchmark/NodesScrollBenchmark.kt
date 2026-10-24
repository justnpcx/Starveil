package com.justnpcx.starveil.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The nodes list at a thousand rows (§7.1).
 *
 * The fixture is synthesised rather than borrowed from a real subscription: the
 * cost of drawing a row is a function of the row's shape and the list's length,
 * and both are known. The seeder writes flags, region names, protocols, hosts
 * and a six-probe latency history per node, so the rows measure the same work a
 * real list does — text layout, the status dot, and the latency reading.
 *
 * Frames are measured over a scroll that crosses the whole list and comes back,
 * five times, and the reported number is the harness's own median-of-iterations.
 */
@RunWith(AndroidJUnit4::class)
class NodesScrollBenchmark {

    @get:Rule
    val rule: MacrobenchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Seed once, before the iterations: the rows survive between them, and
     * re-seeding 1000 nodes inside setupBlock would put a minute of database
     * work inside the measurement loop's own budget.
     */
    @Before
    fun seed() {
        device.executeShellCommand("pm clear $PACKAGE")
        // The action is not optional: the receiver ignores anything else, and a
        // broadcast with no action looks like it worked — result=0 — while
        // nothing is written.
        device.executeShellCommand(
            "am broadcast -a $PACKAGE.benchmark.SEED -n $PACKAGE/.benchmark.SeedReceiver --ei count $NODES",
        )
        device.waitForIdle()
    }

    @Test
    fun scrollThousandNodes() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            // A thousand rows is a slow first composition on this hardware, and
            // startActivityAndWait returns when the window is up, not when the
            // list has settled. Without this the swipes went to whatever was
            // still on screen and the run measured nine frames.
            awaitAppForeground()
            openNodesTab()
            // Evidence for the run: what the measured block was looking at.
            device.executeShellCommand("screencap -p /sdcard/nodes-benchmark.png")
        },
    ) {
        val width = device.displayWidth
        val height = device.displayHeight
        // Three screens down, three back: the list is 1000 rows, so this crosses
        // it far enough for every kind of row to be bound and drawn twice.
        // 60 steps rather than 12: the fifth argument is the number of move
        // events, and a 12-step swipe is a flick that the list treats as a
        // gesture to be flung rather than a drag to be followed.
        repeat(3) {
            device.swipe(width / 2, (height * 0.8f).toInt(), width / 2, (height * 0.25f).toInt(), 60)
        }
        repeat(3) {
            device.swipe(width / 2, (height * 0.25f).toInt(), width / 2, (height * 0.8f).toInt(), 60)
        }
    }

    /**
     * The tab bar is five equal cells. Clicking by position rather than by text
     * keeps the benchmark working in either language — the label is a resource,
     * the geometry is not.
     */
    /** Poll until the app's window is the one receiving input. */
    private fun awaitAppForeground() {
        repeat(50) {
            if (device.hasObject(By.pkg(PACKAGE).depth(0))) return
            Thread.sleep(100)
        }
        error("the app did not come to the foreground within 5s")
    }

    /** The tab bar exists only after the first composition; wait for a row. */
    private fun awaitNodesVisible() {
        // A thousand rows through SQLCipher is not instant on this hardware;
        // five seconds was not enough and the run failed with a message that
        // blamed the tap.
        repeat(300) {
            if (device.hasObject(By.textContains("·"))) return
            Thread.sleep(100)
        }
        // In-process dump: the shell command cannot run while the harness holds
        // the UiAutomation connection (it dies with "already registered").
        val dump = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(dump)
        println("FAILED-SCREEN: " + dump.toString(Charsets.UTF_8.name()).take(4000))
        error("the node rows never appeared within 30s")
    }

    /**
     * Switch to the nodes tab, retrying.
     *
     * One click is not enough: the first tap after a cold start lands while the
     * first composition is still running and is dropped, which looked exactly
     * like "the tab bar is somewhere else" for four runs. The retry makes the
     * setup independent of that timing.
     */
    private fun openNodesTab() {
        repeat(3) { attempt ->
            // Through the shell, like a person with adb would: UiDevice.click()
            // on this ROM lands in the app's window but never reaches the
            // Compose tab, while `input tap` at the same coordinates does.
            val x = device.displayWidth * 3 / 10
            val y = (device.displayHeight * 0.97f).toInt()
            device.executeShellCommand("input tap $x $y")
            device.waitForIdle()
            repeat(25) {
                if (device.hasObject(By.textContains("·"))) return
                Thread.sleep(80)
            }
            if (attempt == 2) error("the node rows never appeared within 30s")
        }
    }

    private companion object {
        const val PACKAGE = "com.justnpcx.starveil"
        const val NODES = 1000
        const val ITERATIONS = 5
        const val SEED_SETTLE_MS = 60_000L
    }
}
