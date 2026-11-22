package com.ohmy.zfsync.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The home screen's thin outlined icons. Drawn here rather than taken from Material's icons,
 * whose core set has no thin-stroke versions of these. Each is sized as it's shown (22dp, the eye
 * 20dp, the chevron 16dp) so its stroke is the width it's drawn at. Tinted by Icon like any other.
 */

private fun icon(name: String, size: Float, strokeWidth: Float, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = size.dp,
        defaultHeight = size.dp,
        viewportWidth = size,
        viewportHeight = size,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build,
    ).build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}

private fun PathBuilder.square(left: Float, top: Float, size: Float) {
    moveTo(left, top)
    horizontalLineTo(left + size)
    verticalLineTo(top + size)
    horizontalLineTo(left)
    close()
}

/** A gear: eight flat teeth around a ring, with the axle's hole in the middle. */
val SettingsGearIcon: ImageVector = icon("SettingsGear", size = 22f, strokeWidth = 1.5f) {
    val c = 11f
    val outer = 9.25f
    val inner = 7f
    val teeth = 8
    // Each tooth spans this share of its slot, at the outer radius; a little wider at its base.
    val toothHalf = PI / teeth * 0.42
    val baseHalf = PI / teeth * 0.62
    for (i in 0 until teeth) {
        val a = 2 * PI * i / teeth - PI / 2
        val points = listOf(
            inner to a - baseHalf,
            outer to a - toothHalf,
            outer to a + toothHalf,
            inner to a + baseHalf,
        )
        points.forEachIndexed { index, (r, angle) ->
            val x = c + (r * cos(angle)).toFloat()
            val y = c + (r * sin(angle)).toFloat()
            if (i == 0 && index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    close()
    circle(c, c, 3f)
}

/** A circle with a check in it: select photos. */
val SelectIcon: ImageVector = icon("Select", size = 22f, strokeWidth = 1.5f) {
    circle(11f, 11f, 8.75f)
    moveTo(7.25f, 11.25f)
    lineTo(9.9f, 13.9f)
    lineTo(14.9f, 8.4f)
}

/** Four squares, two by two: show as a grid. */
val GridLayoutIcon: ImageVector = icon("GridLayout", size = 22f, strokeWidth = 1.5f) {
    square(3.5f, 3.5f, 6f)
    square(12.5f, 3.5f, 6f)
    square(3.5f, 12.5f, 6f)
    square(12.5f, 12.5f, 6f)
}

/** Two rows of a square and a line: show as a list. */
val ListLayoutIcon: ImageVector = icon("ListLayout", size = 22f, strokeWidth = 1.5f) {
    square(3.5f, 4f, 6f)
    moveTo(12.5f, 7f)
    lineTo(18.5f, 7f)
    square(3.5f, 12f, 6f)
    moveTo(12.5f, 15f)
    lineTo(18.5f, 15f)
}

/** A chevron pointing down; turned to point up. */
val ChevronDownIcon: ImageVector = icon("ChevronDown", size = 16f, strokeWidth = 1.6f) {
    moveTo(4f, 6f)
    lineTo(8f, 10f)
    lineTo(12f, 6f)
}

private fun PathBuilder.eye() {
    moveTo(1.75f, 10f)
    curveTo(3.9f, 6.1f, 6.7f, 4.25f, 10f, 4.25f)
    curveTo(13.3f, 4.25f, 16.1f, 6.1f, 18.25f, 10f)
    curveTo(16.1f, 13.9f, 13.3f, 15.75f, 10f, 15.75f)
    curveTo(6.7f, 15.75f, 3.9f, 13.9f, 1.75f, 10f)
    close()
    circle(10f, 10f, 2.5f)
}

/** An open eye: show the password. */
val EyeIcon: ImageVector = icon("Eye", size = 20f, strokeWidth = 1.5f) { eye() }

/** An eye struck through: hide the password. */
val EyeOffIcon: ImageVector = icon("EyeOff", size = 20f, strokeWidth = 1.5f) {
    eye()
    moveTo(3.5f, 3.5f)
    lineTo(16.5f, 16.5f)
}
