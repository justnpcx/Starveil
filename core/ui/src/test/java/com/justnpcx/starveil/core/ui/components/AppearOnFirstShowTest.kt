package com.justnpcx.starveil.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §5.2's entrance animation.
 *
 * The fade itself is not worth asserting — what breaks is the timing around it,
 * so the clock is driven by hand and the assertions are about *when* a row
 * becomes visible.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearOnFirstShowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a new row appears once its stagger and entrance have run`() {
        val seen = mutableSetOf<Any>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppearOnFirstShow(key = "row", index = 0, seen = seen) { Text("row") }
        }

        rule.mainClock.advanceTimeBy(400)

        rule.onNodeWithText("row").assertIsDisplayed()
    }

    @Test
    fun `a row deep in the list does not wait for its raw index`() {
        // Uncapped, index 50 would leave the row blank for 1500ms of stagger
        // before the 220ms entrance had even started — which is what scrolling
        // to node 50 would feel like.
        val seen = mutableSetOf<Any>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppearOnFirstShow(key = "deep", index = 50, seen = seen) { Text("deep") }
        }

        rule.mainClock.advanceTimeBy(500)

        rule.onNodeWithText("deep").assertIsDisplayed()
    }

    @Test
    fun `a row that has already appeared is shown immediately`() {
        // Scrolling away and back must not replay the entrance. One frame is
        // all a row gets here, so any stagger at all would leave it invisible.
        val seen = mutableSetOf<Any>("row")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppearOnFirstShow(key = "row", index = 3, seen = seen) { Text("again") }
        }

        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithText("again").assertIsDisplayed()
    }
}
