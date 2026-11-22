package com.ohmy.zfsync

import android.app.Application
import com.ohmy.zfsync.storage.GalleryPreferencesStore
import com.ohmy.zfsync.storage.SyncPreferencesStore
import com.ohmy.zfsync.storage.ThemeStore
import com.ohmy.zfsync.sync.CameraSyncRepository

class OhMyApplication : Application() {
    val syncRepository: CameraSyncRepository by lazy {
        CameraSyncRepository(this, syncPreferences)
    }
    val themeStore: ThemeStore by lazy { ThemeStore(this) }
    val galleryPreferences: GalleryPreferencesStore by lazy { GalleryPreferencesStore(this) }
    val syncPreferences: SyncPreferencesStore by lazy { SyncPreferencesStore(this) }
}
