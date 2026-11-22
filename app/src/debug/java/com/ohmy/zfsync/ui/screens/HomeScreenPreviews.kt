package com.ohmy.zfsync.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.home.HomeLayout
import com.ohmy.zfsync.ui.preview.HomeScreenStateProvider
import com.ohmy.zfsync.ui.theme.OhMyTheme
import com.ohmy.zfsync.ui.theme.ThemeChoice
import com.ohmy.zfsync.ui.theme.paletteName

/*
 * The mock's frame is 720 by 948dp; the previews use it, so they compare side by side. Every one
 * is "today" on FakeData's shooting day, so its photos fall in today's sections.
 */

private const val MOCK_WIDTH = 720
private const val MOCK_HEIGHT = 948

private fun palette(name: String): ThemeChoice = ThemeChoice.all.firstOrNull { it.paletteName == name } ?: ThemeChoice.System

private val Newspaper = palette("Newspaper")

@Composable
private fun HomePreview(
    state: SyncUiState,
    layout: HomeLayout = HomeLayout.List,
    theme: ThemeChoice = Newspaper,
    savedCredentials: SavedCameraCredentials? = FakeData.credentials,
    cardExpanded: Boolean? = null,
) {
    OhMyTheme(choice = theme) {
        HomeScreen(
            state = state,
            savedCredentials = savedCredentials,
            layout = layout,
            onLayoutChange = {},
            onConnect = { _, _ -> },
            onDisconnect = {},
            onPhotoClick = {},
            onDeletePhotos = {},
            onOpenSettings = {},
            onBrowseAlbums = {},
            today = FakeData.shootingDay,
            cardInitiallyExpanded = cardExpanded,
        )
    }
}

/** Grid, connected, one shot at 64% and one queued. */
@Preview(name = "Grid, syncing", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeGridSyncingPreview() {
    HomePreview(FakeData.States.connectedSyncing, layout = HomeLayout.Grid)
}

@Preview(name = "List, up to date", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeListUpToDatePreview() {
    HomePreview(FakeData.States.connected)
}

@Preview(name = "Camera not found, card open", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeCameraNotFoundPreview() {
    HomePreview(FakeData.States.cameraNotFound, cardExpanded = true)
}

@Preview(name = "Connecting", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeConnectingPreview() {
    HomePreview(FakeData.States.connectingToSaved, cardExpanded = true)
}

@PreviewLightDark
@Preview(name = "Honey Gold", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeHoneyGoldPreview() {
    HomePreview(FakeData.States.connectedSyncing, layout = HomeLayout.Grid, theme = palette("Honey Gold"))
}

/** Emerald: where its accent can't carry readable text, the button's fill is darkened (see HomeColors.accentFill). */
@PreviewLightDark
@Preview(name = "Emerald", widthDp = MOCK_WIDTH, heightDp = MOCK_HEIGHT)
@Composable
private fun HomeEmeraldPreview() {
    HomePreview(FakeData.States.cameraNotFound, theme = palette("Emerald"), cardExpanded = true)
}

/** Every home state at phone size, in light and dark. */
@PreviewLightDark
@Composable
private fun HomeStatesPreview(@PreviewParameter(HomeScreenStateProvider::class) state: SyncUiState) {
    HomePreview(state, savedCredentials = if (state == FakeData.States.firstLaunch) null else FakeData.credentials)
}
