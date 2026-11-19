package com.ohmy.zfsync.core.model

/** Whether a photo is currently being downloaded from the camera, and how many at once. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val count: Int) : DownloadStatus
}
