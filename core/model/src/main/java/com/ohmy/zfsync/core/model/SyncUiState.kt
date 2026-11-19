package com.ohmy.zfsync.core.model

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
    /** Null when the photo has no readable exposure EXIF. */
    val exposure: ExposureInfo? = null,
)

data class SyncUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val autoSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    /** Every photo in the app's album (not just this session's), oldest first. */
    val photos: List<SyncedPhoto> = emptyList(),
    /** Photos downloaded since the user last connected; cleared on an explicit disconnect. */
    val sessionPhotoUris: Set<Uri> = emptySet(),
    val lastError: String? = null,
)

/** Display order shared by the list and the viewer so indices line up: this session's photos
 *  first, then everything earlier, each newest first. */
fun SyncUiState.photosNewestFirst(): List<SyncedPhoto> {
    val (session, earlier) = photos.asReversed().partition { it.uri in sessionPhotoUris }
    return session + earlier
}
