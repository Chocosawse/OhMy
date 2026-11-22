package com.ohmy.zfsync.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.theme.ThemeChoice

/*
 * The state variations each screen is previewed in. Public so snapshot tests can render the same
 * set (e.g. iterate `values`), keeping previews and screenshots in step.
 */

/** States the home screen is shown in. */
class HomeScreenStateProvider : PreviewParameterProvider<SyncUiState> {
    override val values = sequenceOf(
        FakeData.States.cameraNotFound,
        FakeData.States.firstLaunch,
        FakeData.States.connectingToSaved,
        FakeData.States.connectionError,
        FakeData.States.connectedSyncing,
        FakeData.States.connected,
        FakeData.States.connectedSyncingWithError,
        FakeData.States.connectedEmptyAutoSyncOff,
    )
}

/** Every theme the picker offers. */
class ThemeChoiceProvider : PreviewParameterProvider<ThemeChoice> {
    override val values = ThemeChoice.all.asSequence()
}
