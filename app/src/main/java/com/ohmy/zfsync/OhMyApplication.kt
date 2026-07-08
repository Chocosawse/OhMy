package com.ohmy.zfsync

import android.app.Application
import com.ohmy.zfsync.sync.CameraSyncRepository

class OhMyApplication : Application() {
    val syncRepository: CameraSyncRepository by lazy { CameraSyncRepository(this) }
}
