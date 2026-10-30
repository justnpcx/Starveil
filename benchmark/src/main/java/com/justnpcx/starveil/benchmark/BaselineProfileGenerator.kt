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
        // Tries the direct visible navigation item first (capsule), then drawer for deeper pages.
        val destinations = listOf(
            "Nodes" to "节点",
            "Rules" to "规则",
            "Logs" to "日志",
            "Settings" to "设置",
            "Home" to "首页",
        )
        destinations.forEach { (en, zh) ->
            val direct = device.findObject(By.text(en))
                ?: device.findObject(By.text(zh))
                ?: device.findObject(By.desc(en))
                ?: device.findObject(By.desc(zh))
            if (direct != null) {
                direct.click()
            } else {
                // Open drawer for items not in the floating capsule (e.g. Logs, Settings)
                val menuButton = device.findObject(By.descContains("drawer"))
                    ?: device.findObject(By.descContains("Menu"))
                    ?: device.findObject(By.descContains("menu"))
                menuButton?.click()
                device.waitForIdle()
                val drawerItem = device.findObject(By.text(en)) ?: device.findObject(By.text(zh))
                drawerItem?.click()
            }
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
