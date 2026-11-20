package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast for the text roles the Nikon Yellow palette sets or relies on. */
class NikonYellowPaletteTest {

    private val light = NikonYellowPalette.adjust(lightColorScheme(), darkTheme = false)
    private val dark = NikonYellowPalette.adjust(darkColorScheme(), darkTheme = true)

    @Test
    fun `light text is readable`() {
        // cs-design maps body text to color900.
        assertReadable(NikonYellowPalette.color900, light.surface, "text")
        assertReadable(light.onSurfaceVariant, light.surface, "secondary text")
        assertReadable(light.onPrimary, NikonYellowPalette.primary, "label on yellow")
        assertReadable(light.onSecondaryContainer, light.secondaryContainer, "text on a selected row")
    }

    @Test
    fun `dark text is readable`() {
        // cs-design maps dark body text to white.
        assertReadable(Color.White, dark.surface, "text")
        assertReadable(dark.onSurfaceVariant, dark.surface, "secondary text")
        assertReadable(dark.onPrimary, NikonYellowPalette.primaryNight, "label on yellow")
        assertReadable(NikonYellowPalette.primaryNight, dark.surface, "yellow on black")
        assertReadable(dark.onSecondaryContainer, dark.secondaryContainer, "text on a selected row")
    }

    private fun assertReadable(text: Color, background: Color, role: String) {
        val (lighter, darker) = listOf(text.luminance(), background.luminance()).sortedDescending()
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("$role contrast is $ratio, under 4.5", ratio >= 4.5f)
    }
}
