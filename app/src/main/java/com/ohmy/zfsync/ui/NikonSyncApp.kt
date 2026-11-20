package com.ohmy.zfsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.ui.components.PhotoTransitionState
import com.ohmy.zfsync.ui.screens.CarouselStyle
import com.ohmy.zfsync.ui.screens.ConnectScreen
import com.ohmy.zfsync.ui.screens.PhotoViewerScreen
import com.ohmy.zfsync.ui.screens.SettingsScreen
import com.ohmy.zfsync.ui.screens.SyncScreen
import com.ohmy.zfsync.ui.theme.overshootExitSpec
import com.ohmy.zfsync.ui.theme.overshootSpec

/**
 * Insets the home and sync screens pad by: top and sides only. Their photo list runs to the
 * bottom edge, behind the transparent navigation bar, and pads its own content for the bottom
 * inset so its last row can still scroll clear of the bar.
 */
private val ScreenInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun NikonSyncApp(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val themeChoice by viewModel.themeChoice.collectAsState()
    val filmStripCarousel by viewModel.filmStripCarousel.collectAsState()
    // Saveable: rotation recreates the activity, and an open gallery or theme picker must survive it.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Links a photo's list row and the viewer, for the photo's move between them. Already open
    // when the viewer is restored (e.g. after rotation), so it doesn't replay.
    val photoTransition = remember { PhotoTransitionState(initiallyOpen = viewerIndex != null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            val connected = state.connectionState as? ConnectionState.Connected
            // The screen the viewer opens from stays composed under it, keeping its scroll
            // position, so the photo can return to its row. Only while the viewer is fully open
            // (opaque, and not on its way out) is it left unplaced: then it isn't drawn, touched
            // or read by accessibility, at no cost.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val covered = viewerIndex != null &&
                                photoTransition.progress.value >= 1f &&
                                !photoTransition.closing
                            if (!covered) placeable.place(0, 0)
                        }
                    },
            ) {
                if (connected != null) {
                    SyncScreen(
                        state = state,
                        downloadStatus = downloadStatus,
                        cameraIp = connected.cameraIp,
                        onAutoSyncChange = viewModel::setAutoSyncEnabled,
                        onSyncNow = viewModel::syncNow,
                        onDisconnect = viewModel::disconnect,
                        onPhotoClick = { index -> viewerIndex = index },
                        onDeletePhotos = viewModel::deletePhotos,
                        onOpenMenu = { showSettings = true },
                        photoTransition = photoTransition,
                        modifier = Modifier.windowInsetsPadding(ScreenInsets),
                    )
                } else {
                    ConnectScreen(
                        state = state,
                        savedCredentials = viewModel.savedCredentials,
                        onConnect = viewModel::connect,
                        onPhotoClick = { index -> viewerIndex = index },
                        onDeletePhotos = viewModel::deletePhotos,
                        onOpenMenu = { showSettings = true },
                        photoTransition = photoTransition,
                        modifier = Modifier.windowInsetsPadding(ScreenInsets),
                    )
                }
            }

            // The viewer opens from either screen: synced photos are on the phone, so browsing
            // them doesn't need a camera connection.
            viewerIndex?.let { openIndex ->
                // Live: photos synced while the viewer is open show up in it.
                PhotoViewerScreen(
                    photos = state.photosNewestFirst(),
                    initialIndex = openIndex,
                    onDelete = { photo -> viewModel.deletePhotos(listOf(photo.uri)) },
                    onClose = { viewerIndex = null },
                    inFlightCount = (downloadStatus as? DownloadStatus.Downloading)?.count ?: 0,
                    carouselStyle = if (filmStripCarousel) CarouselStyle.FilmStrip else CarouselStyle.Classic,
                    photoTransition = photoTransition,
                )
            }

            // Drawn over the screen that opened it rather than replacing it, so that screen (and its
            // photo selection) is still there underneath when the picker closes.
            AnimatedVisibility(
                visible = showSettings,
                enter = scaleIn(overshootSpec(), initialScale = 0.92f) + fadeIn(overshootSpec()),
                exit = scaleOut(overshootExitSpec(), targetScale = 0.92f) + fadeOut(overshootExitSpec()),
            ) {
                SettingsScreen(
                    currentTheme = themeChoice,
                    onSelectTheme = viewModel::setTheme,
                    filmStripCarousel = filmStripCarousel,
                    onFilmStripCarouselChange = viewModel::setFilmStripCarousel,
                    onClose = { showSettings = false },
                )
            }
        }
    }
}
