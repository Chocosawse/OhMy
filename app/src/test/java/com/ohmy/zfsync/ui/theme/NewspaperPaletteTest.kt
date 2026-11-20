package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast for the text roles the Newspaper palette sets or relies on, like cs-design's own
 * check for its built-in palettes (which doesn't cover app palettes).
 */
class NewspaperPaletteTest {

    private val light = NewspaperPalette.adjust(lightColorScheme(), darkTheme = false)
    private val dark = NewspaperPalette.adjust(darkColorScheme(), darkTheme = true)

    @Test
    fun `light text is readable on the paper`() {
        // cs-design maps body text to color900 and the button to primary.
        assertReadable(NewspaperPalette.color900, light.surface, "text")
        assertReadable(light.onSurfaceVariant, light.surface, "secondary text")
        assertReadable(light.error, light.surface, "error")
        assertReadable(light.onPrimary, NewspaperPalette.primary, "button label")
        assertReadable(NewspaperPalette.color900, light.secondaryContainer, "text on a selected row")
    }

    @Test
    fun `dark text is readable on the ink`() {
        // cs-design maps dark body text to white and the button to primaryNight.
        assertReadable(Color.White, dark.surface, "text")
        assertReadable(dark.onSurfaceVariant, dark.surface, "secondary text")
        assertReadable(dark.error, dark.surface, "error")
        assertReadable(dark.onPrimary, NewspaperPalette.primaryNight, "button label")
        assertReadable(Color.White, dark.secondaryContainer, "text on a selected row")
    }

    private fun assertReadable(text: Color, background: Color, role: String) {
        val (lighter, darker) = listOf(text.luminance(), background.luminance()).sortedDescending()
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("$role contrast is $ratio, under 4.5", ratio >= 4.5f)
    }
}
