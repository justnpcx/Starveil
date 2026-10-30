package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A page's own top-bar actions, published to whoever draws the bar.
 *
 * The bar belongs to the shell, and the pages that have actions in it are
 * features — so the slot lives here, in the shared UI module, rather than in
 * either of them. A page fills it while it is composed and clears it on the way
 * out; the bar draws whatever is in it, which is nothing on almost every page.
 */
class TopBarActionsState {
    /** The actions to draw, or null when the current page has none. */
    var content: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
}

/** The slot the current page fills, or null outside the shell. */
val LocalTopBarActions = staticCompositionLocalOf<TopBarActionsState?> { null }
