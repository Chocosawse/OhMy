package com.ohmy.zfsync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.ui.screens.ConnectScreen
import com.ohmy.zfsync.ui.screens.SyncScreen

@Composable
fun NikonSyncApp(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            val connected = state.connectionState as? ConnectionState.Connected
            if (connected != null) {
                SyncScreen(
                    state = state,
                    cameraIp = connected.cameraIp,
                    onAutoSyncChange = viewModel::setAutoSyncEnabled,
                    onSyncNow = viewModel::syncNow,
                    onDisconnect = viewModel::disconnect,
                )
            } else {
                ConnectScreen(
                    connectionState = state.connectionState,
                    onConnect = viewModel::connect,
                )
            }
        }
    }
}
