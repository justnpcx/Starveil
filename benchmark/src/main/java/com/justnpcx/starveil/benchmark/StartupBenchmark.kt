package com.justnpcx.starveil.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured two ways.
 *
 * The pair is the point. [coldStartupNoAot] is the number before a Baseline
 * Profile exists, and [coldStartupWithBaselineProfile] is the number after —
 * measuring only one of them would leave the profile's contribution to
 * guesswork, and "we added a profile" is not evidence.
 *
 * `startActivityAndWait` measures to *interactive*, not to the first frame: a
 * screen that draws a skeleton and then blocks is not started, and reporting
 * the first frame would call it one.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * The honest worst case: no AOT at all, everything JIT-compiled on the way
     * up. This is what the target has to be met against, because a profile that
     * is not there yet must not be able to make the number look good.
     */
    @Test
    fun coldStartupNoAot() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        // No pressHome() here. It waits for the whole device to go idle, and on
        // this phone that never happens after the first iteration — the run
        // stalls before `am start` is ever issued, perfetto keeps tracing, and
        // four minutes later the harness gives up with "Process crashed".
        //
        // It is also redundant: StartupMode.COLD force-stops the app before
        // every iteration, which is what makes the launch cold.
        startActivityAndWait()
    }

    /**
     * What a user actually has once the Baseline Profile ships.
     *
     * `UseIfAvailable` rather than `Require` for now: before the profile is
     * generated this must still run, so the two numbers are comparable. Tighten
     * it to `Require` once the profile is checked in — at that point a missing
     * profile is a regression, not a stage.
     */
    @Test
    fun coldStartupWithBaselineProfile() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
    ) {
        // No pressHome() here. It waits for the whole device to go idle, and on
        // this phone that never happens after the first iteration — the run
        // stalls before `am start` is ever issued, perfetto keeps tracing, and
        // four minutes later the harness gives up with "Process crashed".
        //
        // It is also redundant: StartupMode.COLD force-stops the app before
        // every iteration, which is what makes the launch cold.
        startActivityAndWait()
    }

    private companion object {
        /**
         * One, not macrobenchmark's default of 10.
         *
         * On this device the harness stalls *between* iterations: the first
         * one completes, and the next setup never issues its `am start` — the
         * run sits there for minutes and then `am instrument` reports "Process
         * crashed". It is not the app being busy (gfxinfo shows the Dashboard
         * rendering nothing while idle) and not the shader-cache broadcast
         * (measured at 0 s). One iteration finishes every time, so the number
         * is collected by running the whole thing repeatedly instead of by
         * asking for ten iterations in one go.
         */
        const val ITERATIONS = 1
    }
}
