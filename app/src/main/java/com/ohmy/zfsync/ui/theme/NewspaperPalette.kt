package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.chocosawse.design.ColorPalette

/*
 * Newsprint: warm off-white paper, near-black ink, warm greys between. The accent is the ink
 * itself (a black button with paper-colored text), and errors are a muted terracotta rather than
 * Material's red. Dark mode inverts it: ink-black paper, with paper as the accent.
 */
private val Paper = Color(0xFFF5F2EC)
private val PaperShade = Color(0xFFEAE5DC)
private val Rule = Color(0xFFD8D2C7)
private val Pencil = Color(0xFF8C867C)
private val Graphite = Color(0xFF55514B)
private val Ink = Color(0xFF141414)
private val InkLight = Color(0xFF1F1E1C)
private val InkShade = Color(0xFF2A2926)
private val Terracotta = Color(0xFFA3523A)
private val TerracottaNight = Color(0xFFE39A7F)

/**
 * The off-white, black-accent palette behind the app's editorial look. An app palette rather than
 * one of cs-design's built-ins; [ThemeChoice.all] lists it after them.
 */
object NewspaperPalette : ColorPalette(
    name = "Newspaper",
    primary = Ink,
    primaryNight = Paper,
    color100 = Paper,
    color300 = Rule,
    color500 = Pencil,
    color700 = Graphite,
    color900 = Ink,
    complement = Terracotta,
) {
    /**
     * cs-design maps every palette's light surface to pure white, and its dark surface a step
     * lighter than the background. Newsprint is one flat sheet, so surfaces take the paper color,
     * and the roles cs-design leaves at Material's baseline (rules, fills, dialogs, errors) are
     * set to match instead of Material's cool lavender greys and red.
     */
    fun adjust(scheme: ColorScheme, darkTheme: Boolean): ColorScheme = if (!darkTheme) {
        scheme.copy(
            surface = Paper,
            surfaceVariant = PaperShade,
            onSurfaceVariant = Graphite,
            surfaceContainerLowest = Paper,
            surfaceContainerLow = Paper,
            surfaceContainer = PaperShade,
            surfaceContainerHigh = PaperShade,
            surfaceContainerHighest = Rule,
            // Selected rows: a shade darker than the paper they sit on.
            secondaryContainer = PaperShade,
            onSecondaryContainer = Ink,
            outline = Pencil,
            outlineVariant = Rule,
            error = Terracotta,
            onError = Paper,
            onPrimary = Paper,
        )
    } else {
        scheme.copy(
            background = Ink,
            surface = Ink,
            surfaceVariant = InkShade,
            onSurfaceVariant = Rule,
            surfaceContainerLowest = Ink,
            surfaceContainerLow = Ink,
            surfaceContainer = InkLight,
            surfaceContainerHigh = InkLight,
            surfaceContainerHighest = InkShade,
            secondaryContainer = InkShade,
            onSecondaryContainer = Paper,
            outline = Pencil,
            outlineVariant = InkShade,
            error = TerracottaNight,
            onError = Ink,
            onPrimary = Ink,
        )
    }
}
