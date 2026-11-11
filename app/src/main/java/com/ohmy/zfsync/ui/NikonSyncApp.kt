package com.ohmy.zfsync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.ui.screens.ConnectScreen
import com.ohmy.zfsync.ui.screens.PhotoViewerScreen
import com.ohmy.zfsync.ui.screens.SyncScreen

@Composable
fun NikonSyncApp(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            val connected = state.connectionState as? ConnectionState.Connected
            if (connected != null) {
                val openIndex = viewerIndex
                if (openIndex != null) {
                    // Snapshot taken once per viewer session (re-captured only when a fresh
                    // index is opened), so new auto-synced photos arriving mid-view don't shift
                    // pages out from under the pager -- see PhotoViewerScreen's doc comment.
                    val photosSnapshot = remember(openIndex) { state.downloadedPhotos.asReversed().toList() }
                    PhotoViewerScreen(
                        initialPhotos = photosSnapshot,
                        initialIndex = openIndex,
                        onDelete = { photo -> viewModel.deletePhoto(photo.uri) },
                        onClose = { viewerIndex = null },
                    )
                } else {
                    SyncScreen(
                        state = state,
                        downloadStatus = downloadStatus,
                        cameraIp = connected.cameraIp,
                        onAutoSyncChange = viewModel::setAutoSyncEnabled,
                        onSyncNow = viewModel::syncNow,
                        onDisconnect = viewModel::disconnect,
                        onPhotoClick = { index -> viewerIndex = index },
                    )
                }
            } else {
                ConnectScreen(
                    connectionState = state.connectionState,
                    savedCredentials = viewModel.savedCredentials,
                    onConnect = viewModel::connect,
                )
            }
        }
    }
}
