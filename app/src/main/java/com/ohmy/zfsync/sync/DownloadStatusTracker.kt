package com.ohmy.zfsync.sync

import com.ohmy.zfsync.core.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Tracks how many downloads are in flight and exposes that as a simple [DownloadStatus] stream,
 * independent of any particular screen or caller. Anything that wants to show "a download is in
 * progress" -- the Sync screen today, potentially a notification, widget, or another screen
 * later -- observes [status] directly, without depending on [CameraSyncRepository] or the rest
 * of the sync UI state.
 */
class DownloadStatusTracker {
    private val activeCount = MutableStateFlow(0)

    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status.asStateFlow()

    /** Runs [block], reporting [DownloadStatus.Downloading] for its duration. Nests safely. */
    suspend fun <T> track(block: suspend () -> T): T {
        begin()
        try {
            return block()
        } finally {
            end()
        }
    }

    private fun begin() {
        val count = activeCount.updateAndGet { it + 1 }
        _status.value = DownloadStatus.Downloading(count)
    }

    private fun end() {
        val remaining = activeCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        _status.value = if (remaining == 0) DownloadStatus.Idle else DownloadStatus.Downloading(remaining)
    }
}
