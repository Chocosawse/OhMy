package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.ui.theme.MonoFontFamily

/*
 * The viewer's floating info: small translucent black boxes with sharp corners, like captions
 * pasted onto a print. A photo's name and gear top-left, its exposure bottom-left, and when it was
 * taken running down the top-right edge.
 */

/** The boxes' fill: dark enough to read over any photo, light enough to stay part of it. */
private val InfoBoxColor = Color.Black.copy(alpha = 0.72f)

/** Contrast the info text keeps against its box, even with the box over a white sky. */
private const val INFO_TEXT_CONTRAST = 4.5f

/**
 * The theme's primary color, lightened toward white only as far as it takes to read on the boxes:
 * a light primary (e.g. Material You's in dark mode) is used as is, a dark one (Newspaper's ink)
 * becomes a light tint of itself.
 */
@Composable
private fun infoTextColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary) {
        // The worst case: the translucent box over a pure white part of the photo.
        val background = InfoBoxColor.compositeOver(Color.White)
        (0..10).asSequence()
            .map { lerp(primary, Color.White, it / 10f) }
            .firstOrNull { contrast(it, background) >= INFO_TEXT_CONTRAST } ?: Color.White
    }
}

private fun contrast(a: Color, b: Color): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + 0.05f) / (darker + 0.05f)
}

private val SmallMono = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)

/**
 * Top-left: the file name in a small tab, over the camera and lens in a larger serif box, e.g.
 * "DSC_3785.JPG" above "NIKON Z f • Zuiko 90mm f/2". Without camera or lens, just the tab.
 */
@Composable
fun PhotoTitleCard(filename: String, exposure: ExposureInfo?, modifier: Modifier = Modifier) {
    val color = infoTextColor()
    Column(modifier = modifier.fitWidth()) {
        Text(
            filename,
            style = SmallMono,
            color = color,
            maxLines = 1,
            modifier = Modifier.background(InfoBoxColor).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        exposure?.cameraAndLens()?.let { gear ->
            Text(
                gear,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = color,
                maxLines = 1,
                modifier = Modifier.background(InfoBoxColor).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Bottom-left: focal length, aperture, shutter speed and ISO as big mono values under small
 * labels, e.g. "90mm  f/3.2  1/60 s  1600". Only the known ones; nothing at all without any.
 */
@Composable
fun ExposureCard(exposure: ExposureInfo?, labels: ExposureLabels, modifier: Modifier = Modifier) {
    val readings = exposure?.let {
        listOfNotNull(
            it.focalLengthMm?.let { mm -> labels.focalLength to formatFocalLength(mm) },
            it.aperture?.let { f -> labels.aperture to formatAperture(f) },
            it.exposureTimeSeconds?.let { s -> labels.shutter to formatShutterSpeed(s) },
            it.iso?.let { iso -> labels.iso to iso.toString() },
        )
    }.orEmpty()
    if (readings.isEmpty()) return
    val color = infoTextColor()
    Row(
        modifier = modifier
            .fitWidth()
            .background(InfoBoxColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        for ((label, value) in readings) {
            Column {
                Text(label, style = SmallMono.copy(fontSize = 11.sp), color = color.copy(alpha = 0.7f), maxLines = 1)
                Text(
                    value,
                    style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The exposure card's column labels, from string resources. */
data class ExposureLabels(val focalLength: String, val aperture: String, val shutter: String, val iso: String)

/** Top-right: when the photo was taken, set vertically, reading down the screen's edge. */
@Composable
fun CaptureTimeTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SmallMono,
        color = infoTextColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .rotatedClockwise()
            .background(InfoBoxColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Lays the content out on its side, turned a quarter clockwise: measured with the incoming height
 * as its width, and taking up its rotated (swapped) size, so it fits where it's put.
 */
private fun Modifier.rotatedClockwise(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        // Rotating about its centre: move that centre to the centre of the swapped box.
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = 90f }
    }
}

/**
 * Shrinks the content uniformly (in the draw layer) when it's wider than there's room for, rather
 * than wrapping or clipping it: a long lens name, or the exposure card on a narrow phone, stays on
 * one line at a slightly smaller size.
 */
private fun Modifier.fitWidth(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    if (placeable.width <= constraints.maxWidth) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val scale = constraints.maxWidth.toFloat() / placeable.width
        val height = (placeable.height * scale).toInt()
        layout(constraints.maxWidth, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
