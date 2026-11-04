package com.justnpcx.starveil.automation

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The entry point Tasker, MacroDroid and `adb shell am start` drive.
 *
 * There is no UI and the activity never reaches the screen: it hands the action
 * to [AutomationRunner], which owns its own scope, and finishes in onCreate.
 * That is also why it is safe to be exported — the contract is start, stop,
 * toggle and nothing else; it reads and writes no data. See
 * docs/23-automation.md.
 */
@AndroidEntryPoint
class AutomationActivity : ComponentActivity() {

    @Inject
    lateinit var runner: AutomationRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner.dispatch(intent?.action)
        finish()
    }
}
