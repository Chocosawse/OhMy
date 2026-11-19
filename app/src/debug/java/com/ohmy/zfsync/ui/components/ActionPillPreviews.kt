package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.ui.theme.OhMyTheme

/** The select-mode pill: count label, Share, Delete, Cancel and Menu. */
@PreviewLightDark
@Composable
private fun SelectionActionPillPreview() {
    OhMyTheme {
        ActionPill(
            label = "4 selected",
            onShare = {},
            onDelete = {},
            onCancel = {},
            onMenu = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The gallery's pill, white on the viewer's black backdrop. */
@Preview
@Composable
private fun ViewerActionPillPreview() {
    OhMyTheme {
        ActionPill(
            onShare = {},
            onDelete = {},
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White,
            modifier = Modifier.background(Color.Black).padding(16.dp),
        )
    }
}
