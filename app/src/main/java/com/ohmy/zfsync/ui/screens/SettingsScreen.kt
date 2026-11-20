package com.ohmy.zfsync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding
import com.ohmy.zfsync.ui.theme.ThemeChoice
import com.ohmy.zfsync.ui.theme.paletteName
import com.ohmy.zfsync.ui.theme.systemColorScheme

/**
 * Full-screen settings: the color theme and gallery options. Choosing a theme applies it app-wide
 * straight away (this screen recolors too), so the list doubles as a live preview; back or the
 * arrow closes it.
 */
@Composable
fun SettingsScreen(
    currentTheme: ThemeChoice,
    onSelectTheme: (ThemeChoice) -> Unit,
    filmStripCarousel: Boolean,
    onFilmStripCarouselChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.theme_close))
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header-theme") { SettingsSectionHeader(stringResource(R.string.settings_section_theme)) }
                items(ThemeChoice.all, key = { it.paletteName ?: "system" }) { choice ->
                    ThemeRow(choice = choice, selected = choice == currentTheme, onClick = { onSelectTheme(choice) })
                }
                item(key = "header-gallery") { SettingsSectionHeader(stringResource(R.string.settings_section_gallery)) }
                item(key = "film-strip") {
                    SwitchRow(
                        title = stringResource(R.string.settings_film_strip),
                        summary = stringResource(R.string.settings_film_strip_summary),
                        checked = filmStripCarousel,
                        onCheckedChange = onFilmStripCarouselChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = 24.dp, bottom = 8.dp),
    )
}

/** A setting toggled by tapping anywhere on the row; the switch shows the state. */
@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeRow(choice: ThemeChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // No start or vertical padding: the swatches sit flush against the screen's left edge
            // and touch the swatches in the rows above and below.
            .padding(end = ScreenHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Swatches(previewColors(choice))
        Text(
            text = when (choice) {
                ThemeChoice.System -> stringResource(R.string.theme_system)
                is ThemeChoice.Palette -> choice.palette.name
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // The whole row is the click target; the radio button only shows the state.
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Representative colors for the current light/dark mode, so themes can be told apart at a glance:
 * accent, then a supporting shade, then primary. The primary color is always last, i.e. the
 * rightmost square.
 */
@Composable
private fun previewColors(choice: ThemeChoice): List<Color> {
    val dark = isSystemInDarkTheme()
    val (accent, supporting, primary) = when (choice) {
        ThemeChoice.System -> systemColorScheme(dark).let { Triple(it.tertiary, it.primaryContainer, it.primary) }
        is ThemeChoice.Palette -> choice.palette.let {
            if (dark) Triple(it.complement, it.color900, it.primaryNight) else Triple(it.complement, it.color100, it.primary)
        }
    }
    return listOf(accent, supporting, primary)
}

private val SwatchHeight = 72.dp

/** Widths of the accent, supporting and primary swatches: primary, on the right, is the full square. */
private val SwatchWidths = listOf(18.dp, 56.dp, 72.dp)

/** The colors as blocks side by side, with no gaps between them. */
@Composable
private fun Swatches(colors: List<Color>) {
    Row {
        colors.zip(SwatchWidths).forEach { (color, width) ->
            Box(modifier = Modifier.size(width = width, height = SwatchHeight).background(color))
        }
    }
}
