package com.justnpcx.starveil.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * A Catmull-Rom spline through [points], as cubic Béziers.
 *
 * Every sample is on the curve: the spline is built from the samples, not from
 * a fitted shape that merely passes near them. That is the difference between
 * smoothing a chart and decorating one — a smoothed line that misses its own
 * data cannot be read as the data.
 *
 * Shared by the traffic ribbons and the health trace so the two charts in the
 * app cannot disagree about what "smooth" means.
 */
internal fun catmullRomPath(points: List<Offset>): Path {
    val path = Path()
    if (points.size < 2) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        // The control points sit one sixth of the neighbouring chord away from
        // each endpoint — the standard Catmull-Rom to Bézier conversion.
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    return path
}
