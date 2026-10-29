package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * A secondary page's bar: the page colour behind the status bar, so the system
 * bar and the page never meet at a visible seam.
 *
 * Material's default container is the surface role, which in this palette is
 * the card white — one step above the page, and exactly the strip this avoids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarveilTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = StarveilColors.bg,
            scrolledContainerColor = StarveilColors.bg,
        ),
    )
}
