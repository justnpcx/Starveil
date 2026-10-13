package com.justnpcx.starveil.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The three Material Symbols the app shell needs that are NOT in the small
 * `material-icons-core` artifact, inlined as vector assets.
 *
 * Why not `material-icons-extended`: it bundles thousands of glyphs (tens of
 * MB) so that we can draw exactly three. The nav bar's other two icons
 * (`Icons.AutoMirrored.Filled.List`, `Icons.Filled.Settings`) do ship in
 * `material-icons-core`, which the app now depends on directly.
 *
 * Path data is copied verbatim from the Apache-2.0 AndroidX icon sources
 * (`material-icons-extended` 1.7.8, `filled` set), so rendering is identical
 * to the previous `Icons.Filled.*` calls.
 */
object StarveilIcons {

    /** Dashboard — the four-tile cockpit glyph. */
    val Dashboard: ImageVector by lazy {
        buildIcon("Filled.Dashboard") {
            iconPath {
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(8.0f)
                lineTo(11.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(3.0f, 21.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-6.0f)
                lineTo(3.0f, 15.0f)
                verticalLineToRelative(6.0f)
                close()
                moveTo(13.0f, 21.0f)
                horizontalLineToRelative(8.0f)
                lineTo(21.0f, 11.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(13.0f, 3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(8.0f)
                lineTo(21.0f, 3.0f)
                horizontalLineToRelative(-8.0f)
                close()
            }
        }
    }

    /** Route — the routing-rules glyph. */
    val Route: ImageVector by lazy {
        buildIcon("Filled.Route") {
            iconPath {
                moveTo(19.0f, 15.18f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(8.82f)
                curveTo(8.16f, 8.4f, 9.0f, 7.3f, 9.0f, 6.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveTo(3.0f, 4.34f, 3.0f, 6.0f)
                curveToRelative(0.0f, 1.3f, 0.84f, 2.4f, 2.0f, 2.82f)
                verticalLineTo(17.0f)
                curveToRelative(0.0f, 2.21f, 1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(8.18f)
                curveToRelative(-1.16f, 0.41f, -2.0f, 1.51f, -2.0f, 2.82f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                curveTo(21.0f, 16.7f, 20.16f, 15.6f, 19.0f, 15.18f)
                close()
            }
        }
    }

    /** Terminal — the logs glyph. */
    val Terminal: ImageVector by lazy {
        buildIcon("Filled.Terminal") {
            iconPath {
                moveTo(20.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.89f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(22.0f, 4.9f, 21.11f, 4.0f, 20.0f, 4.0f)
                close()
                moveTo(20.0f, 18.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(18.0f, 17.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(7.5f, 17.0f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(8.67f, 13.0f)
                lineToRelative(-2.59f, -2.59f)
                lineTo(7.5f, 9.0f)
                lineToRelative(4.0f, 4.0f)
                lineTo(7.5f, 17.0f)
                close()
            }
        }
    }
}

/** Builds a 24dp/24-unit icon, matching the AndroidX `materialIcon` defaults. */
private fun buildIcon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(content).build()

/** A filled path, matching the AndroidX `materialPath` defaults. */
private fun ImageVector.Builder.iconPath(block: PathBuilder.() -> Unit): ImageVector.Builder =
    path(fill = SolidColor(Color.Black), pathBuilder = block)
