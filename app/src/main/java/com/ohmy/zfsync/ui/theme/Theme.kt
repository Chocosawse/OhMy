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
        /** Everything the theme picker offers, in order. Palettes come from the library, so new ones appear automatically. */
        val all: List<ThemeChoice> get() = listOf(System) + ColorPalette.builtIns.map(::Palette)

        /** Unknown or missing names (e.g. a palette a later library version dropped) fall back to [System]. */
        fun fromPaletteName(name: String?): ThemeChoice =
            ColorPalette.builtIns.firstOrNull { it.name == name }?.let(::Palette) ?: System
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
        is ThemeChoice.Palette -> AppTheme(activeToken = choice.palette, darkTheme = darkTheme, content = content)
        ThemeChoice.System -> MaterialTheme(colorScheme = systemColorScheme(darkTheme), content = content)
    }
}
