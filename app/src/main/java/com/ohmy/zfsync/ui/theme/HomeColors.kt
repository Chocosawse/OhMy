package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The home screen's color roles, set for each theme from its color scheme ([homeColors]). A
 * palette with exact values of its own gives them through [AdjustedPalette.homeColors].
 */
@Immutable
data class HomeColors(
    val background: Color,
    val ink: Color,
    /** Quieter text: kicker, SSID, inactive tab, field labels, step numbers. Readable on [background] and [card]. */
    val muted: Color,
    /** The connection card. */
    val card: Color,
    /** A photo cell before its image arrives, and a pending shot's tile. */
    val tile: Color,
    /** Text on [tile]: a pending shot's file number. */
    val tileText: Color,
    /** The progress rule's unfilled part. */
    val track: Color,
    /** A pending tile's progress bar, unfilled. */
    val tileTrack: Color,
    /** The status row's dot while connected. */
    val connected: Color,
    /** The theme's primary: the progress fill, the active tab's underline. */
    val accent: Color,
    /** A filled button's background: [accent], darkened (or lightened) only when [onAccent] can't read on it otherwise. */
    val accentFill: Color,
    /** Text on [accentFill]: [ink] or [background], whichever contrasts more. */
    val onAccent: Color,
)

/** WCAG's minimum contrast for body text. */
const val MIN_TEXT_CONTRAST = 4.5f

/** WCAG contrast ratio between two opaque colors, 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private val ConnectedLight = Color(0xFF2F6B3F)
private val ConnectedDark = Color(0xFF7CC08E)

/** How far from [HomeColors.background] toward [HomeColors.ink] each derived surface sits. */
private const val CARD_MIX = 0.06f
private const val TILE_MIX = 0.09f
private const val TRACK_MIX = 0.12f
private const val TILE_TRACK_MIX = 0.18f
private const val TILE_TEXT_MIX = 0.62f

/**
 * Every role for [scheme]: the page and ink from its surface pair, the in-between surfaces mixed
 * from them (so they follow any theme, light or dark, Material You included), and the accent
 * from its primary.
 */
fun homeColors(scheme: ColorScheme, darkTheme: Boolean): HomeColors {
    val background = scheme.surface
    val ink = scheme.onSurface
    val card = lerp(background, ink, CARD_MIX)
    val tile = lerp(background, ink, TILE_MIX)
    return completeHomeColors(
        background = background,
        ink = ink,
        muted = readableOn(scheme.onSurfaceVariant, ink, listOf(background, card)),
        card = card,
        tile = tile,
        tileText = readableOn(lerp(background, ink, TILE_TEXT_MIX), ink, listOf(tile)),
        track = lerp(background, ink, TRACK_MIX),
        tileTrack = lerp(background, ink, TILE_TRACK_MIX),
        connected = if (darkTheme) ConnectedDark else ConnectedLight,
        accent = scheme.primary,
    )
}

/** Fills in [HomeColors.accentFill] and [HomeColors.onAccent] for the given roles. */
fun completeHomeColors(
    background: Color,
    ink: Color,
    muted: Color,
    card: Color,
    tile: Color,
    tileText: Color,
    track: Color,
    tileTrack: Color,
    connected: Color,
    accent: Color,
): HomeColors {
    val onAccent = if (contrastRatio(ink, accent) >= contrastRatio(background, accent)) ink else background
    return HomeColors(
        background = background,
        ink = ink,
        muted = muted,
        card = card,
        tile = tile,
        tileText = tileText,
        track = track,
        tileTrack = tileTrack,
        connected = connected,
        accent = accent,
        accentFill = fillFor(accent, onAccent),
        onAccent = onAccent,
    )
}

/**
 * [accent], or if [text] doesn't reach [MIN_TEXT_CONTRAST] on it, the accent moved away from the
 * text (darker under light text, lighter under dark) in small steps until it does.
 */
private fun fillFor(accent: Color, text: Color): Color {
    if (contrastRatio(text, accent) >= MIN_TEXT_CONTRAST) return accent
    val away = if (text.luminance() > accent.luminance()) Color.Black else Color.White
    var step = 1
    while (step <= 20) {
        val candidate = lerp(accent, away, step * 0.05f)
        if (contrastRatio(text, candidate) >= MIN_TEXT_CONTRAST) return candidate
        step++
    }
    return away
}

/** [color], or moved toward [ink] in small steps until it reaches [MIN_TEXT_CONTRAST] on every one of [surfaces]. */
private fun readableOn(color: Color, ink: Color, surfaces: List<Color>): Color {
    var step = 0
    while (step <= 20) {
        val candidate = lerp(color, ink, step * 0.05f)
        if (surfaces.all { contrastRatio(candidate, it) >= MIN_TEXT_CONTRAST }) return candidate
        step++
    }
    return ink
}

val LocalHomeColors = staticCompositionLocalOf { homeColors(lightColorScheme(), darkTheme = false) }
