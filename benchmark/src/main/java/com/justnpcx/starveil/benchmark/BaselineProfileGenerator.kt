package com.justnpcx.starveil.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes `app/src/main/baseline-prof.txt`: the classes and methods Android
 * should AOT-compile so a cold start does not pay to JIT them first.
 *
 * What goes in is decided by what this block actually touches, which is why it
 * walks the real screens rather than naming classes by hand — a hand-written
 * list drifts from the code the moment a screen is renamed, and the failure is
 * silent (the profile still applies, it just stops covering anything).
 *
 * The startup path is the reason the profile exists, so it runs first and is
 * given time to settle before the tabs are visited.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        startActivityAndWait()
        device.waitForIdle()

        // Every destination, in the order the bottom bar offers them. Home is
        // the start destination and is already on screen.
        listOf("Nodes", "Rules", "Logs", "Settings", "Home").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.wait(Until.hasObject(By.text(tab)), TIMEOUT_MS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
