package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.ui.theme.DateImprintFontFamily
import com.ohmy.zfsync.ui.theme.MonoFontFamily
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The viewer's floating info: small translucent black boxes with sharp corners, like captions
 * pasted onto a print. A photo's name and gear top-left, its exposure bottom-left, and when it was
 * taken running down the top-right edge.
 */

/** The boxes' fill: dark enough to read over any photo, light enough to stay part of it. */
private val InfoBoxColor = Color.Black.copy(alpha = 0.72f)

/** The two left-hand boxes (title and exposure), larger, so a little lighter on the photo. */
private val LightInfoBoxColor = Color.Black.copy(alpha = 0.52f)

/** Contrast the info text keeps against its box, even with the box over a white sky. */
private const val INFO_TEXT_CONTRAST = 4.5f

/**
 * The theme's primary color, lightened toward white only as far as it takes to read on the boxes:
 * a light primary (e.g. Material You's in dark mode) is used as is, a dark one (Newspaper's ink)
 * becomes a light tint of itself.
 */
@Composable
private fun infoTextColor(box: Color): Color {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary, box) {
        // The worst case: the translucent box over a pure white part of the photo.
        val background = box.compositeOver(Color.White)
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
 * Top-left: the file name in a small tab (tagged [rawTag] when [hasRaw]), over the camera and lens
 * in a larger serif box, e.g. "DSC_3785.JPG +RAW" above "NIKON Z f • Zuiko 90mm f/2". Without
 * camera or lens, just the tab.
 */
@Composable
fun PhotoTitleCard(filename: String, exposure: ExposureInfo?, hasRaw: Boolean, rawTag: String, modifier: Modifier = Modifier) {
    val color = infoTextColor(LightInfoBoxColor)
    Column(modifier = modifier.fitWidth()) {
        Text(
            // "DSC_3785.JPG +RAW": the shot's RAW is here too.
            if (hasRaw) {
                buildAnnotatedString {
                    append(filename)
                    append(' ')
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rawTag) }
                }
            } else {
                AnnotatedString(filename)
            },
            style = SmallMono,
            color = color,
            maxLines = 1,
            // Tighter than the box below, so the file name sits a touch left of the gear line: a
            // tab pasted on slightly askew, like a label on a print.
            modifier = Modifier.background(LightInfoBoxColor).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        exposure?.cameraAndLens()?.let { gear ->
            Text(
                gear,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = color,
                maxLines = 1,
                modifier = Modifier.background(LightInfoBoxColor).padding(horizontal = 12.dp, vertical = 6.dp),
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
            it.focalLengthMm?.let { mm -> labels.focalLength to withSmallUnit(formatFocalLength(mm), "mm") },
            it.aperture?.let { f -> labels.aperture to AnnotatedString(formatAperture(f)) },
            it.exposureTimeSeconds?.let { s -> labels.shutter to withSmallUnit(formatShutterSpeed(s), "s") },
            it.iso?.let { iso -> labels.iso to AnnotatedString(iso.toString()) },
        )
    }.orEmpty()
    if (readings.isEmpty()) return
    val color = infoTextColor(LightInfoBoxColor)
    Row(
        modifier = modifier
            .fitWidth()
            .background(LightInfoBoxColor)
            // 5 + 14 (label) + 24 (value) + 5 = 48dp, the FAB's height, so the two line up exactly.
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for ((label, value) in readings) {
            Column {
                Text(label, style = SmallMono.copy(fontSize = 10.sp, lineHeight = 14.sp), color = color.copy(alpha = 0.7f), maxLines = 1)
                Text(
                    value,
                    style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The exposure card's column labels, from string resources. */
data class ExposureLabels(val focalLength: String, val aperture: String, val shutter: String, val iso: String)

/**
 * [text] with its trailing [unit] set small and light, so a big readout shows its number: "1/2000"
 * with a small "s", rather than a mono "1/2000 s" spread over two extra character widths.
 */
private fun withSmallUnit(text: String, unit: String): AnnotatedString = buildAnnotatedString {
    append(text.removeSuffix(unit).trimEnd())
    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)) { append(unit) }
}

/** The imprint's lit segments, and the glow they cast on the film. */
private val ImprintOrange = Color(0xFFFF7A1A)
private val ImprintGlow = Color(0xCCFF5500)

/** How strongly the imprint shows: a little faded, as a real one is on the print. */
private const val IMPRINT_OPACITY = 0.7f

/** The imprint's size: fixed, not relative to the photo, so it's never large on a big screen. */
private val IMPRINT_SIZE = 14.sp

/** Its inset from the photo's corner. */
private val ImprintInset = 12.dp

/** Space kept clear at the top for the close and info buttons (8 + 48 + 8dp), below the bars. */
private val ImprintTopClearance = 64.dp

/**
 * A film camera's date imprint, set vertically (reading down) at the photo's top-right corner:
 * orange seven-segment digits with a slight glow, e.g. "'26 10 24  16:36". Placed from the photo
 * as fitted (ContentScale.Fit) into this box, from its [imageSize], but kept inside the visible
 * area -- clear of the system bars and the top buttons, within the screen -- so it's never cut
 * off, whatever the photo's shape. A fixed size, so it isn't drawn in the photo's zoom layer (the
 * info hides while zoomed anyway). [alpha] is read in the draw phase.
 */
// statusBarsIgnoringVisibility: room for the status bar even while zooming has hidden it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateImprint(text: String, imageSize: Size, alpha: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.union(WindowInsets.statusBarsIgnoringVisibility)
    val safeTop = insets.getTop(density)
    val safeEnd = insets.getRight(density, layoutDirection)
    val style = remember(density) {
        val digitPx = with(density) { IMPRINT_SIZE.toPx() }
        TextStyle(
            fontFamily = DateImprintFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = IMPRINT_SIZE,
            color = ImprintOrange,
            // A tight glow: enough to read as lit segments without smearing them.
            shadow = Shadow(ImprintGlow, blurRadius = digitPx * 0.25f),
        )
    }
    val styled = remember(text) {
        buildAnnotatedString {
            // DSEG has no apostrophe: that one character from the mono face, still lit orange.
            for (char in text) {
                if (char == '\'') {
                    withStyle(SpanStyle(fontFamily = MonoFontFamily)) { append(char) }
                } else {
                    append(char)
                }
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val fit = min(boxWidth / imageSize.width, boxHeight / imageSize.height)
        val photoRight = (boxWidth + imageSize.width * fit) / 2f
        val photoTop = (boxHeight - imageSize.height * fit) / 2f
        val inset = with(density) { ImprintInset.toPx() }
        // The photo's top-right corner, pulled inside the visible area.
        val right = min(photoRight, boxWidth - safeEnd) - inset
        val top = maxOf(photoTop, safeTop + with(density) { ImprintTopClearance.toPx() }) + inset
        Text(
            styled,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints())
                    // Turned a quarter clockwise, it takes the text's height as its width.
                    val turnedWidth = placeable.height
                    val turnedHeight = placeable.width
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x = right - turnedWidth
                        val y = top
                        // Rotating about its centre: put that centre where the turned box's is.
                        placeable.placeWithLayer(
                            x = (x + turnedWidth / 2f - placeable.width / 2f).roundToInt(),
                            y = (y + turnedHeight / 2f - placeable.height / 2f).roundToInt(),
                        ) {
                            rotationZ = 90f
                            this.alpha = alpha() * IMPRINT_OPACITY
                        }
                    }
                },
        )
    }
}

/**
 * How the viewer shows when a photo was taken: imprinted on the photo like a film camera's date
 * back, or as a vertical mono caption at the screen's top-right like the other captions. [key] is
 * how the choice is saved, so it must not change.
 */
enum class DateStyle(val key: String) {
    Imprint("imprint"),
    Caption("caption"),
    ;

    companion object {
        /** Unknown or missing keys fall back to [Imprint]. */
        fun fromKey(key: String?): DateStyle = entries.firstOrNull { it.key == key } ?: Imprint
    }
}

/** The caption date style: when the photo was taken, set vertically, reading down the screen's edge. */
@Composable
fun CaptureTimeTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SmallMono,
        color = infoTextColor(InfoBoxColor),
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
