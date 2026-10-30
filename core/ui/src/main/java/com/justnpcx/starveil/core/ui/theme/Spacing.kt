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

    /** 16dp — inside a block. */
    val lg = 16.dp

    /**
     * 24dp — the page margin, and the gap between sections.
     *
     * Two roles, one value on purpose: the text column starts 24dp in, and a
     * section starts 24dp below the one above it, so the page's vertical and
     * horizontal rhythm are the same number. A different value for each is how
     * a layout starts looking "nearly aligned".
     */
    val xl = 24.dp

    /** 32dp — between a section and the one after it, when it needs air. */
    val xxl = 32.dp

    /**
     * 48dp — the gap above a page's first section, and the clearance a floating
     * button needs at the bottom of a list.
     */
    val xxxl = 48.dp

    /** The page's left and right margin. Named so screens stop writing 24.dp. */
    val pageMargin = xl

    /** The navigation bar's own height, above the system gesture inset. */
    val navBarHeight = 80.dp

    /** The air a page keeps between its last row and the bar. */
    val navContentInset = 12.dp

    /**
     * Bottom padding for a list on a page that carries the plus disc.
     *
     * The disc is 56dp and sits 12dp above the bar, so 76dp is what a last row
     * needs to come to rest above it rather than behind it.
     */
    val plusClearance = 76.dp

    /** The disc's diameter, its inset from the right edge and its lift. */
    val plusDisc = 56.dp
    val plusMarginEnd = 18.dp
    val plusMarginBottom = 12.dp

    /**
     * Bottom padding for a scrolling list that has a floating button over it.
     *
     * 96dp is not a rhythm value: it is the button's own height plus the gap
     * that keeps it off the last row. A list that uses [xl] here lets the
     * button cover its own final line, which is the defect this constant
     * exists to stop repeating.
     */
    val fabClearance = 96.dp
}
