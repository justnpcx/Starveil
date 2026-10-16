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
        pressHome()
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
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        /**
         * Macrobenchmark's own default. Cold start is noisy enough that a
         * smaller run reports the device's mood rather than the app's cost.
         */
        const val ITERATIONS = 10
    }
}
