package com.ohmy.zfsync.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ohmy.zfsync.OhMyApplication
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.service.CameraSyncService
import com.ohmy.zfsync.storage.SavedCameraCredentials
import com.ohmy.zfsync.sync.CameraSyncRepository
import com.ohmy.zfsync.sync.DownloadStatus
import com.ohmy.zfsync.ui.theme.ThemeChoice
import com.ohmy.zfsync.ui.theme.paletteName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraSyncRepository = (application as OhMyApplication).syncRepository
    private val themeStore = (application as OhMyApplication).themeStore

    val state: StateFlow<SyncUiState> = repository.state
    val downloadStatus: StateFlow<DownloadStatus> = repository.downloadStatus
    val savedCredentials: SavedCameraCredentials? = repository.loadSavedCredentials()

    val themeChoice: StateFlow<ThemeChoice> = themeStore.paletteName
        .map(ThemeChoice::fromPaletteName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.fromPaletteName(themeStore.paletteName.value))

    fun setTheme(choice: ThemeChoice) {
        themeStore.setPaletteName(choice.paletteName)
    }

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

    fun refreshPhotos() {
        repository.refreshPhotos()
    }

    fun deletePhotos(uris: Collection<Uri>) {
        viewModelScope.launch { repository.deletePhotos(uris) }
    }

    /** Call when the app returns to the foreground: silently rejoins the camera if the Wi-Fi
     *  connection dropped on its own while backgrounded (see CameraSyncRepository.handleConnectionLost). */
    fun retryConnectionIfNeeded() {
        val credentials = repository.consumeLostConnectionCredentials() ?: return
        connect(credentials.ssid, credentials.password)
    }
}
