package com.justnpcx.starveil.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale.
 *
 * PRD §8.2 requires no bare dp values in the UI, the same way it requires no
 * bare colours: every gap comes from here, so the whole app shares one rhythm
 * and a change lands in one place.
 *
 * Scale: 4 / 8 / 12 / 16 / 24 / 32 dp (a 4dp grid, which is what the existing
 * screens already used informally).
 */
object StarveilSpacing {
    /** 4dp — hairline gaps inside a row. */
    val xs = 4.dp

    /** 8dp — between related items. */
    val sm = 8.dp

    /** 12dp — between sub-groups. */
    val md = 12.dp

    /** 16dp — screen padding, the default. */
    val lg = 16.dp

    /** 24dp — between sections. */
    val xl = 24.dp

    /** 32dp — page-level breathing room. */
    val xxl = 32.dp
}
