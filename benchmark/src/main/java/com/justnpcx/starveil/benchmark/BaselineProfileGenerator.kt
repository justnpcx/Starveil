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

        // Every destination, in both English and Chinese locales.
        //
        // Three of them are on the bar; the logs and the settings are two rows
        // of the hub behind its fourth item, so each is reached by opening the
        // hub and coming back.
        fun tap(en: String, zh: String) {
            val node = device.findObject(By.text(en))
                ?: device.findObject(By.text(zh))
                ?: device.findObject(By.desc(en))
                ?: device.findObject(By.desc(zh))
            node?.click()
            device.waitForIdle()
        }

        tap("Nodes", "节点")
        tap("Rules", "规则")
        tap("More", "更多")
        tap("Logs", "日志")
        device.pressBack()
        device.waitForIdle()
        tap("Settings", "设置")
        device.pressBack()
        device.waitForIdle()
        tap("Home", "首页")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
