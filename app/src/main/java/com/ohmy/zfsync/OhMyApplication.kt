package com.ohmy.zfsync

import android.app.Application
import com.ohmy.zfsync.storage.ThemeStore
import com.ohmy.zfsync.sync.CameraSyncRepository

class OhMyApplication : Application() {
    val syncRepository: CameraSyncRepository by lazy { CameraSyncRepository(this) }
    val themeStore: ThemeStore by lazy { ThemeStore(this) }
}
