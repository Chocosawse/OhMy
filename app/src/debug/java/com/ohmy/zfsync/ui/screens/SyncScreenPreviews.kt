package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.preview.SyncScreenStateProvider
import com.ohmy.zfsync.ui.theme.OhMyTheme

@PreviewLightDark
@Composable
private fun SyncScreenPreview(@PreviewParameter(SyncScreenStateProvider::class) state: SyncUiState) {
    SyncScreenPreviewContent(state, DownloadStatus.Idle)
}

/** Downloads in flight: the status row plus placeholder rows at the top of "This session". */
@PreviewLightDark
@Composable
private fun SyncScreenDownloadingPreview() {
    SyncScreenPreviewContent(FakeData.States.connected, FakeData.downloadingTwo)
}

@Composable
private fun SyncScreenPreviewContent(state: SyncUiState, downloadStatus: DownloadStatus) {
    OhMyTheme {
        SyncScreen(
            state = state,
            downloadStatus = downloadStatus,
            cameraIp = FakeData.CAMERA_IP,
            onAutoSyncChange = {},
            onSyncNow = {},
            onDisconnect = {},
            onPhotoClick = {},
            onDeletePhotos = {},
            onOpenMenu = {},
        )
    }
}
