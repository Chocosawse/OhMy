package com.ohmy.zfsync.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers what to download from the camera, and when it was last synced, across launches. */
class SyncPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloadRaws = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_RAWS, false))

    /** Whether RAW files (e.g. NEF) are downloaded as well as JPEGs; off by default, as they're large. */
    val downloadRaws: StateFlow<Boolean> = _downloadRaws.asStateFlow()

    fun setDownloadRaws(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_RAWS, enabled).apply()
        _downloadRaws.value = enabled
    }

    private val _lastSyncMillis = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L })

    /** When the phone last had everything new from the camera (see SyncUiState.lastSyncMillis); null if never. */
    val lastSyncMillis: StateFlow<Long?> = _lastSyncMillis.asStateFlow()

    fun setLastSyncMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, millis).apply()
        _lastSyncMillis.value = millis
    }

    private companion object {
        const val PREFS_NAME = "sync"
        const val KEY_DOWNLOAD_RAWS = "download_raws"
        const val KEY_LAST_SYNC = "last_sync_millis"
    }
}
