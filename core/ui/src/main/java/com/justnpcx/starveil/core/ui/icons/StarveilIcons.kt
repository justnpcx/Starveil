package com.justnpcx.starveil.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icons, as strokes.
 *
 * One set, one weight. The previous shell mixed filled Material glyphs for the
 * tab bar with outlined ones for the toolbars, and the difference is visible at
 * a glance: a filled shape at 24dp is a blob, an outline is a drawing. This
 * design is a drawing.
 *
 * Stroke width is 1.5 at a 24-unit viewport — 1.5dp on screen — with round caps
 * and joins. The round join matters more than it looks: at this weight a mitre
 * on a right angle reads as a spike, and the whole set starts to feel sharp.
 *
 * The paths are drawn for this set rather than traced from Material's filled
 * glyphs: an outline is not a filled glyph with the fill removed, and converting
 * one into the other produces the doubled lines that give auto-traced icons
 * away.
 */
object StarveilIcons {

    /** Home: a ring, because the dashboard is the connection. */
    val Dashboard: ImageVector by lazy {
        strokeIcon("Line.Ring") {
            iconPath {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                close()
            }
            iconPath {
                moveTo(12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                close()
            }
        }
    }

    /** Nodes: a list of rows. */
    val Nodes: ImageVector by lazy {
        strokeIcon("Line.Nodes") {
            iconPath { row(6f) }
            iconPath { row(12f) }
            iconPath { row(18f) }
        }
    }

    /** Rules: a path that turns, with a terminal at each end. */
    val Route: ImageVector by lazy {
        strokeIcon("Line.Route") {
            iconPath {
                moveTo(6f, 6f)
                verticalLineTo(9f)
                curveTo(6f, 10.1f, 6.9f, 11f, 8f, 11f)
                horizontalLineTo(16f)
                curveTo(17.1f, 11f, 18f, 11.9f, 18f, 13f)
                verticalLineTo(18f)
            }
            iconPath { terminal(6f, 5f) }
            iconPath { terminal(18f, 19f) }
        }
    }

    /** Logs: a terminal prompt. */
    val Terminal: ImageVector by lazy {
        strokeIcon("Line.Terminal") {
            iconPath {
                moveTo(4f, 5f)
                horizontalLineTo(20f)
                curveTo(20.55f, 5f, 21f, 5.45f, 21f, 6f)
                verticalLineTo(18f)
                curveTo(21f, 18.55f, 20.55f, 19f, 20f, 19f)
                horizontalLineTo(4f)
                curveTo(3.45f, 19f, 3f, 18.55f, 3f, 18f)
                verticalLineTo(6f)
                curveTo(3f, 5.45f, 3.45f, 5f, 4f, 5f)
                close()
            }
            iconPath {
                moveTo(7.5f, 10f)
                lineTo(10f, 12.5f)
                lineTo(7.5f, 15f)
            }
            iconPath {
                moveTo(13f, 15f)
                horizontalLineTo(16.5f)
            }
        }
    }

    /** Settings: sliders, not a gear — a gear at this weight is a smudge. */
    val Settings: ImageVector by lazy {
        strokeIcon("Line.Settings") {
            iconPath {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
            }
            iconPath {
                moveTo(4f, 17f)
                horizontalLineTo(20f)
            }
            iconPath { knob(9f, 7f) }
            iconPath { knob(15f, 17f) }
        }
    }

    /** Search: the magnifier. */
    val Search: ImageVector by lazy {
        strokeIcon("Line.Search") {
            iconPath {
                moveTo(10.5f, 4f)
                curveTo(14.09f, 4f, 17f, 6.91f, 17f, 10.5f)
                curveTo(17f, 14.09f, 14.09f, 17f, 10.5f, 17f)
                curveTo(6.91f, 17f, 4f, 14.09f, 4f, 10.5f)
                curveTo(4f, 6.91f, 6.91f, 4f, 10.5f, 4f)
                close()
            }
            iconPath {
                moveTo(15.5f, 15.5f)
                lineTo(20f, 20f)
            }
        }
    }

    /** Close: two crossing lines. */
    val Close: ImageVector by lazy {
        strokeIcon("Line.Close") {
            iconPath {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
            iconPath {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    /** Add: a plus. */
    val Add: ImageVector by lazy {
        strokeIcon("Line.Add") {
            iconPath {
                moveTo(12f, 5f)
                verticalLineTo(19f)
            }
            iconPath {
                moveTo(5f, 12f)
                horizontalLineTo(19f)
            }
        }
    }

    /** Refresh: a circular arrow. */
    val Refresh: ImageVector by lazy {
        strokeIcon("Line.Refresh") {
            iconPath {
                moveTo(19f, 12f)
                curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
                curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
                curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
                curveTo(14.4f, 5f, 16.5f, 6.2f, 17.7f, 8f)
            }
            iconPath {
                moveTo(17.7f, 4f)
                lineTo(17.7f, 8f)
                lineTo(13.7f, 8f)
            }
        }
    }

    /** More: three dots. */
    val More: ImageVector by lazy {
        strokeIcon("Line.More") {
            iconPath { dot(12f, 5f) }
            iconPath { dot(12f, 12f) }
            iconPath { dot(12f, 19f) }
        }
    }

    /**
     * More: a two-by-two grid.
     *
     * The destination is a page of other destinations, so the glyph is a set of
     * places rather than the overflow dots — dots mean "the rest of this
     * screen", and the hub is not that.
     */
    val Grid: ImageVector by lazy {
        strokeIcon("Line.Grid") {
            iconPath { roundRect(4.5f, 4.5f, 6f, 6f) }
            iconPath { roundRect(13.5f, 4.5f, 6f, 6f) }
            iconPath { roundRect(4.5f, 13.5f, 6f, 6f) }
            iconPath { roundRect(13.5f, 13.5f, 6f, 6f) }
        }
    }

    /** Logs: three lines with their bullets, not a terminal prompt. */
    val List: ImageVector by lazy {
        strokeIcon("Line.List") {
            iconPath { dot(5f, 6.5f) }
            iconPath { moveTo(9f, 6.5f); horizontalLineTo(20f) }
            iconPath { dot(5f, 12f) }
            iconPath { moveTo(9f, 12f); horizontalLineTo(20f) }
            iconPath { dot(5f, 17.5f) }
            iconPath { moveTo(9f, 17.5f); horizontalLineTo(20f) }
        }
    }

    /** Check: the mark inside a switch's on-state thumb. */
    val Check: ImageVector by lazy {
        strokeIcon("Line.Check") {
            iconPath {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 6.5f)
            }
        }
    }

    /** Back: a chevron. */
    val Back: ImageVector by lazy {
        strokeIcon("Line.Back") {
            iconPath {
                moveTo(15f, 5f)
                lineTo(8f, 12f)
                lineTo(15f, 19f)
            }
        }
    }

    /** Up: the move-up chevron. */
    val Up: ImageVector by lazy {
        strokeIcon("Line.Up") {
            iconPath {
                moveTo(6f, 15f)
                lineTo(12f, 9f)
                lineTo(18f, 15f)
            }
        }
    }

    /** Down: the move-down chevron. */
    val Down: ImageVector by lazy {
        strokeIcon("Line.Down") {
            iconPath {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    /** Moon: shown while the app is light, to switch it to dark. */
    val Moon: ImageVector by lazy {
        strokeIcon("Line.Moon") {
            iconPath {
                moveTo(20.5f, 14.8f)
                curveTo(19.2f, 15.9f, 17.6f, 16.5f, 15.8f, 16.5f)
                curveTo(11.4f, 16.5f, 7.9f, 13f, 7.9f, 8.6f)
                curveTo(7.9f, 6.8f, 8.5f, 5.2f, 9.6f, 3.9f)
                curveTo(5.9f, 5.1f, 3.2f, 8.5f, 3.2f, 12.6f)
                curveTo(3.2f, 17.5f, 7.2f, 21.5f, 12.1f, 21.5f)
                curveTo(16.1f, 21.5f, 19.5f, 18.7f, 20.5f, 14.8f)
                close()
            }
        }
    }

    /** Sun: shown while the app is dark, to switch it to light. */
    val Sun: ImageVector by lazy {
        strokeIcon("Line.Sun") {
            iconPath {
                moveTo(16.5f, 12f)
                curveTo(16.5f, 14.49f, 14.49f, 16.5f, 12f, 16.5f)
                curveTo(9.51f, 16.5f, 7.5f, 14.49f, 7.5f, 12f)
                curveTo(7.5f, 9.51f, 9.51f, 7.5f, 12f, 7.5f)
                curveTo(14.49f, 7.5f, 16.5f, 9.51f, 16.5f, 12f)
                close()
            }
            iconPath { moveTo(12f, 2.5f); verticalLineTo(5f) }
            iconPath { moveTo(12f, 19f); verticalLineTo(21.5f) }
            iconPath { moveTo(2.5f, 12f); horizontalLineTo(5f) }
            iconPath { moveTo(19f, 12f); horizontalLineTo(21.5f) }
            iconPath { moveTo(5.3f, 5.3f); lineTo(7f, 7f) }
            iconPath { moveTo(17f, 17f); lineTo(18.7f, 18.7f) }
            iconPath { moveTo(18.7f, 5.3f); lineTo(17f, 7f) }
            iconPath { moveTo(7f, 17f); lineTo(5.3f, 18.7f) }
        }
    }

    /** External: opens in the system browser. */
    val External: ImageVector by lazy {
        strokeIcon("Line.External") {
            iconPath {
                moveTo(14f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
            }
            iconPath {
                moveTo(20f, 4f)
                lineTo(11f, 13f)
            }
            iconPath {
                moveTo(18f, 14f)
                verticalLineTo(19f)
                curveTo(18f, 19.55f, 17.55f, 20f, 17f, 20f)
                horizontalLineTo(5f)
                curveTo(4.45f, 20f, 4f, 19.55f, 4f, 19f)
                verticalLineTo(7f)
                curveTo(4f, 6.45f, 4.45f, 6f, 5f, 6f)
                horizontalLineTo(10f)
            }
        }
    }

    /** Chevron: the disclosure marker on a row that opens something. */
    val Chevron: ImageVector by lazy {
        strokeIcon("Line.Chevron") {
            iconPath {
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }
        }
    }

    /**
     * Home: a house.
     *
     * The dashboard used to be a ring with a ring inside it — a target, which
     * reads as "aim" rather than "start here", and which was one of two
     * bullseye-shaped marks in the old navigation.
     */
    val Home: ImageVector by lazy {
        strokeIcon("Line.Home") {
            iconPath {
                moveTo(3.5f, 11f)
                lineTo(12f, 3.5f)
                lineTo(20.5f, 11f)
            }
            iconPath {
                moveTo(6f, 9.5f)
                verticalLineTo(20f)
                horizontalLineTo(18f)
                verticalLineTo(9.5f)
            }
            iconPath {
                moveTo(10f, 20f)
                verticalLineTo(14.5f)
                horizontalLineTo(14f)
                verticalLineTo(20f)
            }
        }
    }

    /** Scenes: stacked layers, which is what a scene is — a set, not a route. */
    val Scenes: ImageVector by lazy {
        strokeIcon("Line.Scenes") {
            iconPath {
                moveTo(12f, 3f)
                lineTo(21f, 7.5f)
                lineTo(12f, 12f)
                lineTo(3f, 7.5f)
                close()
            }
            iconPath {
                moveTo(3.5f, 12.5f)
                lineTo(12f, 17f)
                lineTo(20.5f, 12.5f)
            }
            iconPath {
                moveTo(3.5f, 16.5f)
                lineTo(12f, 21f)
                lineTo(20.5f, 16.5f)
            }
        }
    }

    /** Diagnostics: a pulse, so it cannot be mistaken for the log's terminal. */
    val Activity: ImageVector by lazy {
        strokeIcon("Line.Activity") {
            iconPath {
                moveTo(3f, 12.5f)
                horizontalLineTo(6.5f)
                lineTo(9.5f, 4.5f)
                lineTo(14f, 19.5f)
                lineTo(16.5f, 12.5f)
                horizontalLineTo(21f)
            }
        }
    }

    /** Scan: four corner brackets and a reading line. */
    val Scan: ImageVector by lazy {
        strokeIcon("Line.Scan") {
            iconPath {
                moveTo(9f, 3.5f)
                horizontalLineTo(6f)
                lineTo(3.5f, 6f)
                verticalLineTo(9f)
            }
            iconPath {
                moveTo(15f, 3.5f)
                horizontalLineTo(18f)
                lineTo(20.5f, 6f)
                verticalLineTo(9f)
            }
            iconPath {
                moveTo(20.5f, 15f)
                verticalLineTo(18f)
                lineTo(18f, 20.5f)
                horizontalLineTo(15f)
            }
            iconPath {
                moveTo(9f, 20.5f)
                horizontalLineTo(6f)
                lineTo(3.5f, 18f)
                verticalLineTo(15f)
            }
            iconPath {
                moveTo(7f, 12f)
                horizontalLineTo(17f)
            }
        }
    }

    /** Clipboard: the paste target. */
    val Clipboard: ImageVector by lazy {
        strokeIcon("Line.Clipboard") {
            iconPath {
                moveTo(9f, 5f)
                horizontalLineTo(6f)
                lineTo(4f, 7f)
                verticalLineTo(20f)
                horizontalLineTo(20f)
                verticalLineTo(7f)
                lineTo(18f, 5f)
                horizontalLineTo(15f)
            }
            iconPath {
                moveTo(9f, 2.5f)
                horizontalLineTo(15f)
                verticalLineTo(6f)
                horizontalLineTo(9f)
                close()
            }
            iconPath {
                moveTo(8f, 12f)
                horizontalLineTo(16f)
            }
            iconPath {
                moveTo(8f, 16f)
                horizontalLineTo(13f)
            }
        }
    }

    /** Pencil: the manual form. */
    val Pencil: ImageVector by lazy {
        strokeIcon("Line.Pencil") {
            iconPath {
                moveTo(4f, 20f)
                lineTo(5f, 16f)
                lineTo(16f, 5f)
                lineTo(19f, 8f)
                lineTo(8f, 19f)
                close()
            }
            iconPath {
                moveTo(13.5f, 7.5f)
                lineTo(16.5f, 10.5f)
            }
        }
    }

    /** Link: a pasted address. */
    val Link: ImageVector by lazy {
        strokeIcon("Line.Link") {
            iconPath {
                moveTo(10.5f, 13.5f)
                lineTo(13.5f, 10.5f)
            }
            iconPath {
                moveTo(9f, 8f)
                lineTo(11f, 6f)
                curveTo(12.7f, 4.3f, 15.3f, 4.3f, 17f, 6f)
                curveTo(18.7f, 7.7f, 18.7f, 10.3f, 17f, 12f)
                lineTo(15f, 14f)
            }
            iconPath {
                moveTo(15f, 16f)
                lineTo(13f, 18f)
                curveTo(11.3f, 19.7f, 8.7f, 19.7f, 7f, 18f)
                curveTo(5.3f, 16.3f, 5.3f, 13.7f, 7f, 12f)
                lineTo(9f, 10f)
            }
        }
    }

    /**
     * Star, hollow: the favourite mark.
     *
     * Drawn as an outline on purpose. The filled star is the brand mark and
     * means "this is the node in use"; a second filled star on the same row
     * said the same thing twice in two different ways.
     */
    val StarOutline: ImageVector by lazy {
        strokeIcon("Line.Star") {
            iconPath {
                moveTo(12f, 3.6f)
                lineTo(14.6f, 9.1f)
                lineTo(20.6f, 9.9f)
                lineTo(16.3f, 14f)
                lineTo(17.3f, 20f)
                lineTo(12f, 17.2f)
                lineTo(6.7f, 20f)
                lineTo(7.7f, 14f)
                lineTo(3.4f, 9.9f)
                lineTo(9.4f, 9.1f)
                close()
            }
        }
    }
}

/** One row of the nodes glyph: a dot and a line. */
private fun PathBuilder.row(y: Float) {
    moveTo(4f, y)
    horizontalLineTo(5.5f)
    moveTo(9f, y)
    horizontalLineTo(20f)
}

/** A small hollow circle: the end of a route. */
private fun PathBuilder.terminal(x: Float, y: Float) {
    moveTo(x - 1.5f, y)
    curveTo(x - 1.5f, y - 0.83f, x - 0.83f, y - 1.5f, x, y - 1.5f)
    curveTo(x + 0.83f, y - 1.5f, x + 1.5f, y - 0.83f, x + 1.5f, y)
    curveTo(x + 1.5f, y + 0.83f, x + 0.83f, y + 1.5f, x, y + 1.5f)
    curveTo(x - 0.83f, y + 1.5f, x - 1.5f, y + 0.83f, x - 1.5f, y)
    close()
}

/** A filled dot on a slider rail. */
private fun PathBuilder.knob(x: Float, y: Float) {
    moveTo(x - 1.75f, y)
    curveTo(x - 1.75f, y - 0.97f, x - 0.97f, y - 1.75f, x, y - 1.75f)
    curveTo(x + 0.97f, y - 1.75f, x + 1.75f, y - 0.97f, x + 1.75f, y)
    curveTo(x + 1.75f, y + 0.97f, x + 0.97f, y + 1.75f, x, y + 1.75f)
    curveTo(x - 0.97f, y + 1.75f, x - 1.75f, y + 0.97f, x - 1.75f, y)
    close()
}

/** A square with softened corners, for the grid cells. */
private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float = 1.6f) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    quadTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r)
    quadTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h)
    quadTo(x, y + h, x, y + h - r)
    lineTo(x, y + r)
    quadTo(x, y, x + r, y)
    close()
}

/** A tiny dot, for the overflow glyph. */
private fun PathBuilder.dot(x: Float, y: Float) {
    moveTo(x - 0.9f, y)
    curveTo(x - 0.9f, y - 0.5f, x - 0.5f, y - 0.9f, x, y - 0.9f)
    curveTo(x + 0.5f, y - 0.9f, x + 0.9f, y - 0.5f, x + 0.9f, y)
    curveTo(x + 0.9f, y + 0.5f, x + 0.5f, y + 0.9f, x, y + 0.9f)
    curveTo(x - 0.5f, y + 0.9f, x - 0.9f, y + 0.5f, x - 0.9f, y)
    close()
}

/**
 * A 24dp icon drawn as strokes.
 *
 * The viewport is 24 units on a 24dp box, so 1.5f here is 1.5dp on screen — the
 * weight the design calls for.
 */
private fun strokeIcon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(content).build()

/** One stroked path; caps and joins are set here so no path has to remember. */
private fun ImageVector.Builder.iconPath(block: PathBuilder.() -> Unit): ImageVector.Builder =
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )