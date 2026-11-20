package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
            // Same side padding as the box below, so the two lines of text share a left edge.
            modifier = Modifier.background(LightInfoBoxColor).padding(horizontal = 12.dp, vertical = 4.dp),
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

/** The imprint's digit height and inset, as fractions of the photo's shorter side. */
private const val IMPRINT_SIZE = 0.042f
private const val IMPRINT_INSET = 0.045f

/**
 * A film camera's date imprint in the photo's bottom-right corner: orange seven-segment digits
 * with a slight glow, e.g. "'26 10 24  16:36". Sized and placed relative to the photo as fitted
 * (ContentScale.Fit) into this box, from its [imageSize], so it stays put on the photo; drawn in
 * the photo's own layer, it zooms and pans with it. [alpha] is read in the draw phase.
 */
@Composable
fun DateImprint(text: String, imageSize: Size, alpha: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val fit = min(boxWidth / imageSize.width, boxHeight / imageSize.height)
        val photoWidth = imageSize.width * fit
        val photoHeight = imageSize.height * fit
        val shortSide = min(photoWidth, photoHeight)
        val digitPx = shortSide * IMPRINT_SIZE
        val insetPx = shortSide * IMPRINT_INSET
        val right = (boxWidth + photoWidth) / 2f - insetPx
        val bottom = (boxHeight + photoHeight) / 2f - insetPx
        val density = LocalDensity.current
        val style = remember(digitPx, density) {
            TextStyle(
                fontFamily = DateImprintFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { digitPx.toSp() },
                color = ImprintOrange,
                shadow = Shadow(ImprintGlow, blurRadius = digitPx * 0.45f),
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
        Text(
            styled,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((right - placeable.width).roundToInt(), (bottom - placeable.height).roundToInt())
                    }
                }
                .graphicsLayer { this.alpha = alpha() * IMPRINT_OPACITY },
        )
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
