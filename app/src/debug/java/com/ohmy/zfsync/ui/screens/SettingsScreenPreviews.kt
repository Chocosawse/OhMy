package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ohmy.zfsync.ui.components.DateStyle
import com.ohmy.zfsync.ui.preview.ThemeChoiceProvider
import com.ohmy.zfsync.ui.theme.OhMyTheme
import com.ohmy.zfsync.ui.theme.ThemeChoice

/** Settings in each theme, with that theme selected, as it looks right after picking it. */
@PreviewLightDark
@Composable
private fun SettingsScreenPreview(@PreviewParameter(ThemeChoiceProvider::class) choice: ThemeChoice) {
    OhMyTheme(choice = choice) {
        SettingsScreen(
            currentTheme = choice,
            onSelectTheme = {},
            carouselStyle = CarouselStyle.FilmStrip,
            onCarouselStyleChange = {},
            confirmDelete = true,
            onConfirmDeleteChange = {},
            downloadRaws = false,
            onDownloadRawsChange = {},
            dateStyle = DateStyle.Imprint,
            onDateStyleChange = {},
            onClose = {},
        )
    }
}
