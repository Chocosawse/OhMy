package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.chocosawse.design.ColorPalette

/*
 * Nikon's brand yellow on neutral blacks and greys, the colors of the camera's own branding and
 * menus. Yellow is the accent in both modes, always with black on it. It's at its best in dark
 * mode; in light mode yellow is near-invisible as a line or text on a light background, so the
 * background there is a soft grey rather than white, and the yellow is mostly fills.
 */
private val NikonYellow = Color(0xFFFFE100)
private val PaleYellow = Color(0xFFFFF3A6)
private val LightGrey = Color(0xFFF2F1ED)
private val Silver = Color(0xFFD6D3CB)
private val Pewter = Color(0xFF8A877F)
private val Graphite = Color(0xFF4A4843)
private val Charcoal = Color(0xFF1C1C1C)
private val DeepCharcoal = Color(0xFF262626)
private val Black = Color(0xFF111111)
private val Ash = Color(0xFFB9B6AE)

/** Nikon yellow and black. An app palette, listed after cs-design's built-ins and Newspaper. */
object NikonYellowPalette : ColorPalette(
    name = "Nikon Yellow",
    primary = NikonYellow,
    primaryNight = NikonYellow,
    color100 = LightGrey,
    color300 = Silver,
    color500 = Pewter,
    color700 = Graphite,
    color900 = Black,
    // The black that yellow sits on in Nikon's branding, shown in the theme picker's swatches.
    complement = Charcoal,
), AdjustedPalette {
    override fun adjust(scheme: ColorScheme, darkTheme: Boolean): ColorScheme = if (!darkTheme) {
        scheme.copy(
            onPrimary = Black,
            surface = LightGrey,
            surfaceVariant = Silver,
            onSurfaceVariant = Graphite,
            surfaceContainerLowest = LightGrey,
            surfaceContainerLow = LightGrey,
            surfaceContainer = Color(0xFFE9E7E1),
            surfaceContainerHigh = Color(0xFFE9E7E1),
            surfaceContainerHighest = Silver,
            // Selected rows: a pale wash of the yellow.
            secondaryContainer = PaleYellow,
            onSecondaryContainer = Black,
            outline = Pewter,
            outlineVariant = Silver,
        )
    } else {
        scheme.copy(
            onPrimary = Black,
            background = Black,
            surface = Black,
            surfaceVariant = DeepCharcoal,
            onSurfaceVariant = Ash,
            surfaceContainerLowest = Black,
            surfaceContainerLow = Black,
            surfaceContainer = Charcoal,
            surfaceContainerHigh = Charcoal,
            surfaceContainerHighest = DeepCharcoal,
            secondaryContainer = DeepCharcoal,
            onSecondaryContainer = Color.White,
            outline = Pewter,
            outlineVariant = DeepCharcoal,
        )
    }
}
