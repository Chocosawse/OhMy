package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * An app palette that finishes the color scheme cs-design builds from it: cs-design maps a
 * palette's few shades onto a handful of roles (and light surfaces to pure white), so a palette
 * with a look of its own sets the rest here, on top. OhMyTheme applies it.
 */
interface AdjustedPalette {
    fun adjust(scheme: ColorScheme, darkTheme: Boolean): ColorScheme

    /** Exact home screen colors for the (adjusted) [scheme], or null to derive them as for any theme (see homeColors). */
    fun homeColors(scheme: ColorScheme, darkTheme: Boolean): HomeColors? = null
}
