package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.theme.OhMyTheme

// The viewer is always black, so one preview covers light and dark. Coil 2 doesn't load images in
// previews, so the photo itself shows empty; the controls are what this previews.
@Preview
@Composable
private fun PhotoViewerScreenPreview() {
    OhMyTheme {
        PhotoViewerScreen(
            initialPhotos = FakeData.photos.asReversed(),
            initialIndex = 2,
            onDelete = {},
            onClose = {},
        )
    }
}
