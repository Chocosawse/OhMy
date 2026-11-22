package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeColorsTest {

    /** The accents the themes use: Newspaper, Viva Magenta, Emerald, Classic Blue, Honey Gold. */
    private val accents = listOf(0xFF181614, 0xFFB92546, 0xFF009270, 0xFF0C4A7D, 0xFFD79F1B).map { Color(it) }

    private val newspaper = NewspaperPalette.adjust(lightColorScheme(), darkTheme = false)

    private fun assertReadable(text: Color, on: Color, what: String) {
        val ratio = contrastRatio(text, on)
        assertTrue("$what: $ratio:1", ratio >= MIN_TEXT_CONTRAST)
    }

    private fun assertAllReadable(colors: HomeColors, theme: String) {
        assertReadable(colors.ink, colors.background, "$theme ink")
        assertReadable(colors.muted, colors.background, "$theme muted")
        assertReadable(colors.muted, colors.card, "$theme muted on the card")
        assertReadable(colors.ink, colors.card, "$theme ink on the card")
        assertReadable(colors.tileText, colors.tile, "$theme tile text")
        assertReadable(colors.onAccent, colors.accentFill, "$theme button label")
    }

    @Test
    fun `Newspaper uses its exact tones`() {
        val colors = NewspaperPalette.homeColors(newspaper, darkTheme = false)!!
        assertEquals(Color(0xFFF4F0E9), colors.background)
        assertEquals(Color(0xFF181614), colors.ink)
        assertEquals(Color(0xFF6A635A), colors.muted)
        assertEquals(Color(0xFFEAE4D9), colors.card)
        assertEquals(Color(0xFFE3DDD1), colors.tile)
        assertEquals(Color(0xFF5E584F), colors.tileText)
        assertEquals(Color(0xFFDDD6CA), colors.track)
        assertEquals(Color(0xFFCFC7B9), colors.tileTrack)
        assertEquals(Color(0xFF2F6B3F), colors.connected)
        // Its accent is the ink, so the button's label is the paper.
        assertEquals(colors.background, colors.onAccent)
        assertAllReadable(colors, "Newspaper")
    }

    @Test
    fun `every accent gets a readable button on every kind of page`() {
        val schemes = listOf(
            "Newspaper light" to newspaper,
            "Newspaper dark" to NewspaperPalette.adjust(darkColorScheme(), darkTheme = true),
            "Honey Gold light" to HoneyGoldPalette.adjust(lightColorScheme(), darkTheme = false),
            "Honey Gold dark" to HoneyGoldPalette.adjust(darkColorScheme(), darkTheme = true),
            "Material light" to lightColorScheme(),
            "Material dark" to darkColorScheme(),
        )
        for ((name, scheme) in schemes) {
            for (accent in accents) {
                val dark = name.endsWith("dark")
                assertAllReadable(homeColors(scheme.copy(primary = accent), dark), "$name, accent $accent")
            }
        }
    }

    @Test
    fun `an accent text reads on is left alone, and one it can't is darkened for the fill only`() {
        val page = Color(0xFFF4F0E9)
        val ink = Color(0xFF181614)
        fun roles(accent: Color) = completeHomeColors(page, ink, ink, page, page, ink, page, page, ink, accent)

        val magenta = roles(Color(0xFFB92546))
        assertEquals(magenta.accent, magenta.accentFill)
        assertEquals(page, magenta.onAccent)

        // Neither the page nor the ink reaches 4.5:1 on this mid green.
        val green = roles(Color(0xFF3F8060))
        assertEquals(Color(0xFF3F8060), green.accent)
        assertTrue(green.accentFill != green.accent)
        assertReadable(green.onAccent, green.accentFill, "darkened fill")
    }
}
