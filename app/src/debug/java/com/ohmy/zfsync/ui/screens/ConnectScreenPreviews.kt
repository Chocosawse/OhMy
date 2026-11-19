package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.preview.ConnectScreenStateProvider
import com.ohmy.zfsync.ui.theme.OhMyTheme

@PreviewLightDark
@Composable
private fun ConnectScreenPreview(@PreviewParameter(ConnectScreenStateProvider::class) state: SyncUiState) {
    OhMyTheme {
        ConnectScreen(
            state = state,
            savedCredentials = FakeData.credentials,
            onConnect = { _, _ -> },
            onPhotoClick = {},
            onDeletePhotos = {},
            onOpenMenu = {},
        )
    }
}
