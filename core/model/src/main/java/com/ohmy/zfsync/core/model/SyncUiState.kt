package com.ohmy.zfsync.core.model

import android.net.Uri
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
    /**
     * The shot's RAW file, if one was downloaded: a separate file when the camera saved a JPEG
     * too (which is then [uri]), or [uri] itself for a RAW-only shot. The app shows a shot once,
     * never its JPEG and RAW as two photos (see pairRawFiles).
     */
    val rawUri: Uri? = null,
) {
    /** A JPEG with its RAW alongside: what the "+RAW" tag marks. */
    val hasSeparateRaw: Boolean get() = rawUri != null && rawUri != uri
}

/**
 * A shot on the camera that's on its way to the phone: known by name (from the camera's object
 * info) before its bytes arrive, so it can hold its place in the album while it downloads.
 */
data class PendingPhoto(
    /** The camera's handle for it. */
    val handle: Long,
    val filename: String,
    /** When it was taken, by the camera's clock; null if the camera didn't say. */
    val capturedAt: LocalDateTime?,
    val totalBytes: Long,
    val receivedBytes: Long = 0,
) {
    /** Bytes have started to arrive; until then it's queued behind other downloads. */
    val inProgress: Boolean get() = receivedBytes > 0

    /** How much has arrived, 0 to 100. */
    val percent: Int
        get() = if (totalBytes <= 0) 0 else (receivedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
}

data class SyncUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** The camera network in use, while connecting or connected. */
    val cameraSsid: String? = null,
    val autoSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    /** Every photo in the app's album (not just this session's), oldest first. */
    val photos: List<SyncedPhoto> = emptyList(),
    /** Photos downloaded since the user last connected; cleared on an explicit disconnect. */
    val sessionPhotoUris: Set<Uri> = emptySet(),
    val lastError: String? = null,
    /** Shots seen on the camera and still downloading or queued to, in the order they were seen. */
    val pending: List<PendingPhoto> = emptyList(),
    /** Downloads finished since [pending] was last empty: the done part of the current batch. */
    val finishedInBatch: Int = 0,
    /** New camera objects whose details are still being read: while above 0, the batch's size isn't known yet. */
    val lookingUp: Int = 0,
    /** When the phone was last known to have everything new from the camera; null if never. */
    val lastSyncMillis: Long? = null,
)

/** When the photo was taken: its EXIF time (the camera's clock), else when it was saved, in [zone]. */
fun SyncedPhoto.takenAt(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    exposure?.capturedAt ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(savedAtMillis), zone)

/**
 * Display order shared by the home screen and the viewer, so indices line up: newest first by
 * when each photo was taken ([takenAt]). Photos taken at the same moment keep newest-saved first.
 */
fun SyncUiState.photosNewestFirst(zone: ZoneId = ZoneId.systemDefault()): List<SyncedPhoto> =
    photos.asReversed().sortedByDescending { it.takenAt(zone) }
