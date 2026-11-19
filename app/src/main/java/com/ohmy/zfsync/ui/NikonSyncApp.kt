package com.ohmy.zfsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.ui.screens.ConnectScreen
import com.ohmy.zfsync.ui.screens.PhotoViewerScreen
import com.ohmy.zfsync.ui.screens.SyncScreen
import com.ohmy.zfsync.ui.screens.ThemePickerScreen
import com.ohmy.zfsync.ui.theme.overshootExitSpec
import com.ohmy.zfsync.ui.theme.overshootSpec

@Composable
fun NikonSyncApp(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val themeChoice by viewModel.themeChoice.collectAsState()
    // Saveable: rotation recreates the activity, and an open gallery or theme picker must survive it.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            val connected = state.connectionState as? ConnectionState.Connected
            val openIndex = viewerIndex
            when {
                // The viewer opens from either screen: synced photos are on the phone, so
                // browsing them doesn't need a camera connection.
                openIndex != null -> {
                    // Live: photos synced while the viewer is open show up in it.
                    PhotoViewerScreen(
                        photos = state.photosNewestFirst(),
                        initialIndex = openIndex,
                        onDelete = { photo -> viewModel.deletePhotos(listOf(photo.uri)) },
                        onClose = { viewerIndex = null },
                        inFlightCount = (downloadStatus as? DownloadStatus.Downloading)?.count ?: 0,
                    )
                }
                connected != null -> SyncScreen(
                    state = state,
                    downloadStatus = downloadStatus,
                    cameraIp = connected.cameraIp,
                    onAutoSyncChange = viewModel::setAutoSyncEnabled,
                    onSyncNow = viewModel::syncNow,
                    onDisconnect = viewModel::disconnect,
                    onPhotoClick = { index -> viewerIndex = index },
                    onDeletePhotos = viewModel::deletePhotos,
                    onOpenMenu = { showThemePicker = true },
                    modifier = Modifier.safeDrawingPadding(),
                )
                else -> ConnectScreen(
                    state = state,
                    savedCredentials = viewModel.savedCredentials,
                    onConnect = viewModel::connect,
                    onPhotoClick = { index -> viewerIndex = index },
                    onDeletePhotos = viewModel::deletePhotos,
                    onOpenMenu = { showThemePicker = true },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }

            // Drawn over the screen that opened it rather than replacing it, so that screen (and its
            // photo selection) is still there underneath when the picker closes.
            AnimatedVisibility(
                visible = showThemePicker,
                enter = scaleIn(overshootSpec(), initialScale = 0.92f) + fadeIn(overshootSpec()),
                exit = scaleOut(overshootExitSpec(), targetScale = 0.92f) + fadeOut(overshootExitSpec()),
            ) {
                ThemePickerScreen(
                    current = themeChoice,
                    onSelect = viewModel::setTheme,
                    onClose = { showThemePicker = false },
                )
            }
        }
    }
}
