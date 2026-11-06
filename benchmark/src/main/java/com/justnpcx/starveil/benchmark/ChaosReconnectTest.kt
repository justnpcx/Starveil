package com.justnpcx.starveil.benchmark

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * M4's drop-and-recover path, fifty times, on a device that keeps its own books.
 *
 * The stimulus is a Wi-Fi radio drop rather than a stopped node: stopping the
 * Shadowsocks server leaves the kernel Running, and a kernel that never noticed
 * anything is not a reconnect test. Turning the radio off is also the only
 * stimulus this TV can produce — it has no cellular radio to switch to — and
 * it costs the harness its own adb link for the duration of each round, which
 * is why every round is orchestrated inside the instrumentation process and the
 * raw data is written to external storage before anything is asserted.
 *
 * What is measured, per round:
 *
 *  - self-heal: drop command → Dashboard says CONNECTED and the VpnService and
 *    its notification agree. The gate is 1.2 s, the task book's number; the
 *    decomposition against the kernel's own transition is in the state CSV.
 *  - consistency: those three surfaces must agree after every round.
 *  - leak: a probe thread opens a fresh TCP connection to a public address
 *    every 150 ms for the whole run. A successful connection whose route for
 *    *this process's uid* is not tun0 is counted as one plaintext escape.
 *    This is a flow-level count, not a packet capture: the lab has no external
 *    endpoint and no root, and the report says so.
 *
 * A round that has not recovered in 10 s is a failure by the task book's rule:
 * the run stops, logcat and the partial CSV are kept, and the assertion names
 * the round. It does not retry the round and it does not lower the gate.
 */
@RunWith(AndroidJUnit4::class)
class ChaosReconnectTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val outDir: File
        get() = File(context.getExternalFilesDir(null), "chaos").apply { mkdirs() }

    private val rounds = mutableListOf<Round>()
    private val leaks = AtomicInteger()
    private val leakLines = mutableListOf<String>()
    private val probing = AtomicBoolean(false)
    private var probeThread: Thread? = null
    private val uid = Process.myUid()
    private val deviceLock = Any()

    /** `-e rounds N` is for a smoke run; the acceptance run is the default 50. */
    private val roundsToRun: Int =
        InstrumentationRegistry.getArguments().getString("rounds")?.toIntOrNull() ?: ROUNDS

    @Before
    fun setUp() {
        // The panel is the only thing that can keep the app in the foreground
        // for ten minutes on this box; the TV's own screen timeout is 10 min
        // and its screensaver is not part of the measurement.
        shell("svc power stayon true")
        shell("settings put system screen_off_timeout 2147483647")
        shell("settings put global stay_on_while_plugged_in 7")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("am start -n $APP/.MainActivity")
        awaitApp()

        if (!uiConnected()) {
            shell(
                "am start -n $APP/.automation.AutomationActivity -a $APP.action.START",
            )
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (SystemClock.elapsedRealtime() < deadline && !uiConnected()) {
                Thread.sleep(200)
            }
        }
        if (!uiConnected()) {
            fail("the tunnel was not connected before the run; seed the lab node first")
        }

        shell(
            "am broadcast -a $STATE_ACTION_START -n $APP/.benchmark.ChaosStateReceiver",
        )
        startProbe()
    }

    @After
    fun tearDown() {
        stopProbe()
        runCatching {
            shell("cmd wifi set-wifi-enabled enabled")
            shell(
                "am broadcast -a $STATE_ACTION_STOP -n $APP/.benchmark.ChaosStateReceiver",
            )
        }
    }

    @Test
    fun fiftyDrops() {
        android.util.Log.i(ROUND_TAG, "begin")
        val csv = File(outDir, "chaos-rounds.csv")
        csv.writeText(
            "round,startMs,enableMs,connectedMs,selfHealMs,appRecoveryMs,consistent,service,notification,leaksSoFar,note\n",
        )
        var inconsistent = 0
        var overGate = 0

        repeat(roundsToRun) { index ->
            val roundNo = index + 1
            val start = SystemClock.elapsedRealtime()
            mark("round$roundNo:start")
            shell("cmd wifi set-wifi-enabled disabled")
            Thread.sleep(DROP_HOLD_MS)
            val enable = SystemClock.elapsedRealtime()
            mark("round$roundNo:wifi_enable")
            shell("cmd wifi set-wifi-enabled enabled")

            var connectedAt = 0L
            var service = false
            var notification = false
            val deadline = start + ROUND_ABORT_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                val connected = uiConnected()
                service = serviceRunning()
                notification = notificationPresent()
                if (connected && service && notification) {
                    connectedAt = SystemClock.elapsedRealtime()
                    break
                }
                Thread.sleep(POLL_MS)
            }

            if (connectedAt == 0L) {
                csv.appendText(
                    "$roundNo,$start,$enable,0,0,false,$service,$notification,${leaks.get()},timeout\n",
                )
                android.util.Log.e(ROUND_TAG, "timeout round=$roundNo elapsed=${SystemClock.elapsedRealtime() - start}")
                dumpLogcat("round$roundNo")
                stopProbe()
                shell(
                    "am broadcast -a $STATE_ACTION_STOP -n $APP/.benchmark.ChaosStateReceiver",
                )
                fail("round $roundNo did not recover within ${ROUND_ABORT_MS / 1000}s; CSV kept at $csv")
            }

            // The three surfaces are read again after the poll loop: a surface
            // that was true one poll ago and false now is exactly the
            // disagreement this assertion exists for.
            val uiNow = uiConnected()
            val serviceNow = serviceRunning()
            val notificationNow = notificationPresent()
            val consistent = uiNow == serviceNow && serviceNow == notificationNow
            if (!consistent) inconsistent++
            val selfHeal = connectedAt - start
            // The outage itself is not the app's latency. Both numbers are
            // written down; the gate is the task book's, on selfHeal, and the
            // recovery-after-network number is what a reader needs to tell a
            // slow radio from a slow app.
            val appRecovery = connectedAt - enable
            if (selfHeal > GATE_MS) overGate++

            csv.appendText(
                "$roundNo,$start,$enable,$connectedAt,$selfHeal,$appRecovery,$consistent," +
                    "$serviceNow,$notificationNow,${leaks.get()},\n",
            )
            android.util.Log.i(
                ROUND_TAG,
                "round=$roundNo,start=$start,enable=$enable,connected=$connectedAt," +
                    "selfHeal=$selfHeal,appRecovery=$appRecovery,consistent=$consistent,service=$serviceNow," +
                    "notification=$notificationNow,leaks=${leaks.get()}",
            )
            rounds += Round(roundNo, selfHeal, appRecovery, consistent, serviceNow, notificationNow)

            // A short settle so the next drop starts from a tunnel that is not
            // still rebuilding; it is not part of the self-heal number.
            Thread.sleep(SETTLE_MS)
        }

        stopProbe()
        shell(
            "am broadcast -a $STATE_ACTION_STOP -n $APP/.benchmark.ChaosStateReceiver",
        )
        writeSummary(inconsistent, overGate)

        assertEquals("state inconsistencies across $roundsToRun rounds", 0, inconsistent)
        assertEquals("plaintext escapes during $roundsToRun rounds", 0, leaks.get())
        assertTrue(
            "rounds over ${GATE_MS}ms: ${rounds.filter { it.selfHealMs > GATE_MS }.map { it.index }}",
            overGate == 0,
        )
    }

    private fun writeSummary(inconsistent: Int, overGate: Int) {
        val sorted = rounds.map { it.selfHealMs }.sorted()
        fun pct(p: Double): Long = if (sorted.isEmpty()) 0 else sorted[((sorted.size - 1) * p).toInt()]
        val summary = File(outDir, "chaos-summary.csv")
        summary.writeText(
            buildString {
                appendLine("metric,value")
                appendLine("rounds,${rounds.size}")
                appendLine("gateMs,$GATE_MS")
                appendLine("overGate,$overGate")
                appendLine("inconsistent,$inconsistent")
                appendLine("leaks,${leaks.get()}")
                appendLine("p50Ms,${pct(0.50)}")
                appendLine("p95Ms,${pct(0.95)}")
                appendLine("maxMs,${sorted.lastOrNull() ?: 0}")
                val recovery = rounds.map { it.appRecoveryMs }.sorted()
                appendLine("appRecoveryP50Ms,${recovery.getOrElse(((recovery.size - 1) * 0.50).toInt()) { 0 }}")
                appendLine("appRecoveryP95Ms,${recovery.getOrElse(((recovery.size - 1) * 0.95).toInt()) { 0 }}")
            },
        )
        android.util.Log.i(
            SUMMARY_TAG,
            "rounds=${rounds.size},overGate=$overGate,inconsistent=$inconsistent," +
                "leaks=${leaks.get()},p50=${pct(0.50)},p95=${pct(0.95)},max=${sorted.lastOrNull() ?: 0}",
        )
        File(outDir, "chaos-leaks.txt").writeText(
            if (leakLines.isEmpty()) "no plaintext escape observed\n"
            else leakLines.joinToString("\n") + "\n",
        )
    }

    /**
     * One fresh connection per attempt, and the route checked for this
     * process's own uid — not the shell's. The shell is a different uid and
     * may be excluded from the tunnel, which would turn every probe into a
     * false leak.
     */
    private fun startProbe() {
        probing.set(true)
        probeThread = Thread {
            while (probing.get()) {
                val reached = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(PROBE_HOST, PROBE_PORT), PROBE_TIMEOUT_MS)
                        true
                    }
                } catch (_: Exception) {
                    false
                }
                if (reached) {
                    val route = runCatching {
                        shell("ip route get $PROBE_HOST uid $uid")
                    }.getOrDefault("")
                    // An empty/denied route is not evidence of a leak. Only a
                    // route that names a physical interface counts.
                    if (route.contains("dev wlan0") || route.contains("dev rmnet") ||
                        route.contains("dev eth")
                    ) {
                        leaks.incrementAndGet()
                        synchronized(leakLines) {
                            leakLines += "${SystemClock.elapsedRealtime()},$route"
                        }
                    }
                }
                Thread.sleep(PROBE_INTERVAL_MS)
            }
        }.apply {
            name = "chaos-leak-probe"
            isDaemon = true
            start()
        }
    }

    private fun stopProbe() {
        probing.set(false)
        probeThread?.join(2_000)
        probeThread = null
    }

    private fun mark(label: String) {
        shell(
            "am broadcast -a $STATE_ACTION_MARK -n $APP/.benchmark.ChaosStateReceiver --es label $label",
        )
    }

    /**
     * UiDevice is not documented as thread-safe, and the leak probe calls the
     * shell from its own thread while the round loop polls the UI. One lock
     * keeps the two off each other; the probe's 150 ms cadence is far below
     * anything the extra serialisation could hide.
     */
    private fun shell(command: String): String =
        synchronized(deviceLock) { device.executeShellCommand(command) }

    private fun hasText(text: String): Boolean =
        synchronized(deviceLock) { device.hasObject(By.text(text)) }

    private fun dumpLogcat(tag: String) {
        runCatching {
            val log = shell("logcat -d -t 1200")
            File(outDir, "logcat-$tag.txt").writeText(log)
        }
    }

    private fun awaitApp() {
        repeat(50) {
            if (synchronized(deviceLock) { device.hasObject(By.pkg(APP).depth(0)) }) return
            Thread.sleep(100)
        }
        fail("the app did not come to the foreground")
    }

    private fun uiConnected(): Boolean =
        hasText("CONNECTED") || hasText("已连接")

    private fun serviceRunning(): Boolean =
        shell("dumpsys activity services $APP")
            .contains("StarveilVpnService")

    /**
     * The foreground service's notification is the third surface. `dumpsys
     * notification` prints the package on every record, so a package match is
     * enough to know the record exists; the run's consistency check is about
     * existence, not about the text on it.
     */
    private fun notificationPresent(): Boolean =
        shell("dumpsys notification --noredact")
            .contains("pkg=$APP")

    private data class Round(
        val index: Int,
        val selfHealMs: Long,
        val appRecoveryMs: Long,
        val consistent: Boolean,
        val service: Boolean,
        val notification: Boolean,
    )

    private companion object {
        const val APP = "com.justnpcx.starveil"
        const val ROUND_TAG = "StarveilChaosRound"
        const val SUMMARY_TAG = "StarveilChaosSummary"
        const val STATE_ACTION_START = "com.justnpcx.starveil.benchmark.CHAOS_START"
        const val STATE_ACTION_MARK = "com.justnpcx.starveil.benchmark.CHAOS_MARK"
        const val STATE_ACTION_STOP = "com.justnpcx.starveil.benchmark.CHAOS_STOP"
        const val ROUNDS = 50
        const val GATE_MS = 1_200L
        const val ROUND_ABORT_MS = 10_000L
        const val DROP_HOLD_MS = 2_500L
        const val SETTLE_MS = 1_500L
        const val POLL_MS = 100L
        const val PROBE_HOST = "1.1.1.1"
        const val PROBE_PORT = 443
        const val PROBE_TIMEOUT_MS = 500
        const val PROBE_INTERVAL_MS = 150L
    }
}
