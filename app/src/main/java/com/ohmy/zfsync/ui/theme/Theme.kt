package com.ohmy.zfsync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chocosawse.design.AppTheme
import com.chocosawse.design.ColorPalette

/** The color theme in effect: the system's colors, or one of cs-design's palettes. */
sealed interface ThemeChoice {
    /** Material You (wallpaper-based) colors on Android 12+, Material's baseline before that. */
    data object System : ThemeChoice

    data class Palette(val palette: ColorPalette) : ThemeChoice

    companion object {
        /** The app's own palettes, offered after the library's. */
        private val appPalettes: List<ColorPalette> = listOf(NewspaperPalette, HoneyGoldPalette)

        /** Every palette on offer: the library's built-ins (new ones appear automatically), then the app's. */
        private val palettes: List<ColorPalette> get() = ColorPalette.builtIns + appPalettes

        /** Everything the theme picker offers, in order. */
        val all: List<ThemeChoice> get() = listOf(System) + palettes.map(::Palette)

        /** Names palettes used to have, mapped to their current ones, so a saved choice survives a rename. */
        private val renamedPalettes = mapOf("Nikon Yellow" to "Honey Gold")

        /** Unknown or missing names (e.g. a palette a later library version dropped) fall back to [System]. */
        fun fromPaletteName(name: String?): ThemeChoice {
            val current = renamedPalettes[name] ?: name
            return palettes.firstOrNull { it.name == current }?.let(::Palette) ?: System
        }
    }
}

val ThemeChoice.paletteName: String? get() = (this as? ThemeChoice.Palette)?.palette?.name

/**
 * A third, quieter text level below [ColorScheme.onSurfaceVariant] (secondary text), for
 * supporting detail. Material 3 has no such role, so it's derived and follows every theme.
 */
val ColorScheme.onSurfaceTertiary: Color get() = onSurfaceVariant.copy(alpha = 0.7f)

@Composable
fun systemColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
}

@Composable
fun OhMyTheme(
    choice: ThemeChoice = ThemeChoice.System,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    when (choice) {
        is ThemeChoice.Palette -> AppTheme(activeToken = choice.palette, darkTheme = darkTheme) {
            val adjusted = choice.palette as? AdjustedPalette
            val scheme = MaterialTheme.colorScheme.let { adjusted?.adjust(it, darkTheme) ?: it }
            val home = remember(scheme, darkTheme) { adjusted?.homeColors(scheme, darkTheme) ?: homeColors(scheme, darkTheme) }
            WithAppTypography(scheme, home, content)
        }
        ThemeChoice.System -> MaterialTheme(colorScheme = systemColorScheme(darkTheme)) {
            val scheme = MaterialTheme.colorScheme
            val home = remember(scheme, darkTheme) { homeColors(scheme, darkTheme) }
            WithAppTypography(scheme, home, content)
        }
    }
}

/** The enclosing theme's shapes, the given [colorScheme] and [homeColors], and the app's own [AppTypography]. */
@Composable
private fun WithAppTypography(colorScheme: ColorScheme, homeColors: HomeColors, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MaterialTheme.shapes,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalHomeColors provides homeColors, content = content)
    }
}
