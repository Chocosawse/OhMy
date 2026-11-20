package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.theme.OhMyTheme

// The viewer is always black, so one preview covers light and dark. Coil 2 doesn't load images in
// previews, so the photo itself shows empty; the controls are what this previews.
@Preview
@Composable
private fun PhotoViewerScreenPreview() {
    OhMyTheme {
        PhotoViewerScreen(
            photos = FakeData.photos.asReversed(),
            initialIndex = 2,
            onDelete = {},
            onClose = {},
        )
    }
}

/** Two downloads in flight: placeholders at the front of the carousel. */
@Preview
@Composable
private fun PhotoViewerScreenDownloadingPreview() {
    OhMyTheme {
        PhotoViewerScreen(
            photos = FakeData.States.connected.photosNewestFirst(),
            initialIndex = 0,
            onDelete = {},
            onClose = {},
            inFlightCount = (FakeData.downloadingTwo as DownloadStatus.Downloading).count,
        )
    }
}

/** The v2 film strip carousel, with a download in flight at its front. */
@Preview
@Composable
private fun PhotoViewerScreenFilmStripPreview() {
    OhMyTheme {
        PhotoViewerScreen(
            photos = FakeData.States.connected.photosNewestFirst(),
            initialIndex = 2,
            onDelete = {},
            onClose = {},
            inFlightCount = 1,
            carouselStyle = CarouselStyle.FilmStrip,
        )
    }
}
