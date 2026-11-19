package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ohmy.zfsync.ui.preview.ThemeChoiceProvider
import com.ohmy.zfsync.ui.theme.OhMyTheme
import com.ohmy.zfsync.ui.theme.ThemeChoice

/** The picker in each theme, with that theme selected, as it looks right after picking it. */
@PreviewLightDark
@Composable
private fun ThemePickerScreenPreview(@PreviewParameter(ThemeChoiceProvider::class) choice: ThemeChoice) {
    OhMyTheme(choice = choice) {
        ThemePickerScreen(current = choice, onSelect = {}, onClose = {})
    }
}
