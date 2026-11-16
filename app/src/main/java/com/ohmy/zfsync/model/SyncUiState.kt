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
    /** Every photo in the app's album (not just this session's), oldest first. */
    val photos: List<SyncedPhoto> = emptyList(),
    val lastError: String? = null,
)
