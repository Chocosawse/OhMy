package com.ohmy.zfsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ohmy.zfsync.OhMyApplication
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.service.CameraSyncService
import com.ohmy.zfsync.sync.CameraSyncRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraSyncRepository = (application as OhMyApplication).syncRepository

    val state: StateFlow<SyncUiState> = repository.state

    fun connect(ssid: String, password: String) {
        viewModelScope.launch {
            repository.connect(ssid, password)
            if (repository.state.value.connectionState is ConnectionState.Connected) {
                CameraSyncService.start(getApplication())
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
        CameraSyncService.stop(getApplication())
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        repository.setAutoSyncEnabled(enabled)
    }

    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }
}
