package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.chocosawse.design.ColorPalette

/*
 * A deep honey gold on neutral blacks and greys. Gold is the accent in both modes, always with
 * black on it. It's at its richest in dark mode; in light mode a gold line or text is still low in
 * contrast on a light background, so the background there is a soft grey rather than white, and
 * the gold is mostly fills.
 */
private val HoneyGold = Color(0xFFD9A11E)
private val PaleHoney = Color(0xFFF6E2B0)
private val LightGrey = Color(0xFFF2F1ED)
private val Silver = Color(0xFFD6D3CB)
private val Pewter = Color(0xFF8A877F)
private val Graphite = Color(0xFF4A4843)
private val Charcoal = Color(0xFF1C1C1C)
private val DeepCharcoal = Color(0xFF262626)
private val Black = Color(0xFF111111)
private val Ash = Color(0xFFB9B6AE)

/** Honey gold and black. An app palette, listed after cs-design's built-ins and Newspaper. */
object HoneyGoldPalette : ColorPalette(
    name = "Honey Gold",
    primary = HoneyGold,
    primaryNight = HoneyGold,
    color100 = LightGrey,
    color300 = Silver,
    color500 = Pewter,
    color700 = Graphite,
    color900 = Black,
    // The black the gold sits on, shown in the theme picker's swatches.
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
            // Selected rows: a pale wash of the gold.
            secondaryContainer = PaleHoney,
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
