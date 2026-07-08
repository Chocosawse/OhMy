package com.ohmy.zfsync.model

import android.net.Uri

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val cameraIp: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class SyncedPhoto(
    val filename: String,
    val savedAtMillis: Long,
    val uri: Uri,
)

data class SyncUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val autoSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val downloadedPhotos: List<SyncedPhoto> = emptyList(),
    val lastError: String? = null,
)
