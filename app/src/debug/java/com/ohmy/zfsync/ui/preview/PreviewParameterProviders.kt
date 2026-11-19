package com.ohmy.zfsync.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.testing.FakeData
import com.ohmy.zfsync.ui.theme.ThemeChoice

/*
 * The state variations each screen is previewed in. Public so snapshot tests can render the same
 * set (e.g. iterate `values`), keeping previews and screenshots in step.
 */

/** States the home (connect) screen is shown in. */
class ConnectScreenStateProvider : PreviewParameterProvider<SyncUiState> {
    override val values = sequenceOf(
        FakeData.States.disconnected,
        FakeData.States.firstLaunch,
        FakeData.States.connecting,
        FakeData.States.connectionError,
    )
}

/** States the connected (sync) screen is shown in. */
class SyncScreenStateProvider : PreviewParameterProvider<SyncUiState> {
    override val values = sequenceOf(
        FakeData.States.connected,
        FakeData.States.connectedNoNewShots,
        FakeData.States.connectedSyncingWithError,
        FakeData.States.connectedEmptyAutoSyncOff,
    )
}

/** Every theme the picker offers. */
class ThemeChoiceProvider : PreviewParameterProvider<ThemeChoice> {
    override val values = ThemeChoice.all.asSequence()
}
