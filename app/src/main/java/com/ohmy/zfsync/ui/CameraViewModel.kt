package com.ohmy.zfsync.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ohmy.zfsync.OhMyApplication
import com.ohmy.zfsync.core.model.Album
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.service.CameraSyncService
import com.ohmy.zfsync.storage.DeviceAlbums
import com.ohmy.zfsync.sync.CameraSyncRepository
import com.ohmy.zfsync.ui.components.DateStyle
import com.ohmy.zfsync.ui.screens.CarouselStyle
import com.ohmy.zfsync.ui.theme.ThemeChoice
import com.ohmy.zfsync.ui.theme.paletteName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraSyncRepository = (application as OhMyApplication).syncRepository
    private val themeStore = (application as OhMyApplication).themeStore
    private val galleryPreferences = (application as OhMyApplication).galleryPreferences
    private val syncPreferences = (application as OhMyApplication).syncPreferences
    private val deviceAlbums = DeviceAlbums(application)

    val state: StateFlow<SyncUiState> = repository.state
    val downloadStatus: StateFlow<DownloadStatus> = repository.downloadStatus
    val savedCredentials: SavedCameraCredentials? = repository.loadSavedCredentials()

    val themeChoice: StateFlow<ThemeChoice> = themeStore.paletteName
        .map(ThemeChoice::fromPaletteName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.fromPaletteName(themeStore.paletteName.value))

    fun setTheme(choice: ThemeChoice) {
        themeStore.setPaletteName(choice.paletteName)
    }

    val carouselStyle: StateFlow<CarouselStyle> = galleryPreferences.carouselStyle
        .map(CarouselStyle::fromKey)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CarouselStyle.fromKey(galleryPreferences.carouselStyle.value))

    fun setCarouselStyle(style: CarouselStyle) {
        galleryPreferences.setCarouselStyle(style.key)
    }

    val confirmDelete: StateFlow<Boolean> = galleryPreferences.confirmDelete

    fun setConfirmDelete(confirm: Boolean) {
        galleryPreferences.setConfirmDelete(confirm)
    }

    val showPhotoInfo: StateFlow<Boolean> = galleryPreferences.showPhotoInfo

    val dateStyle: StateFlow<DateStyle> = galleryPreferences.dateStyle
        .map(DateStyle::fromKey)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateStyle.fromKey(galleryPreferences.dateStyle.value))

    fun setDateStyle(style: DateStyle) {
        galleryPreferences.setDateStyle(style.key)
    }

    /** Read by the sync itself whenever it meets a RAW on the camera, so a change applies straight away. */
    val downloadRaws: StateFlow<Boolean> = syncPreferences.downloadRaws

    fun setDownloadRaws(enabled: Boolean) {
        syncPreferences.setDownloadRaws(enabled)
    }

    // --- Browsing the device's other albums ---

    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    /** The device's albums, as far as the photos permission lets the app see them. */
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Reloads [albums]: on opening the album list, and after the photos permission changes. */
    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) { _albums.value = deviceAlbums.loadAlbums() }
    }

    private val _albumPhotos = MutableStateFlow<List<SyncedPhoto>?>(null)

    /** The open album's photos, oldest first; null while loading or with none open. */
    val albumPhotos: StateFlow<List<SyncedPhoto>?> = _albumPhotos.asStateFlow()

    private var openAlbumId: String? = null

    fun openAlbum(albumId: String) {
        if (albumId != openAlbumId) _albumPhotos.value = null
        openAlbumId = albumId
        viewModelScope.launch(Dispatchers.IO) { _albumPhotos.value = deviceAlbums.loadAlbum(albumId) }
    }

    fun closeAlbum() {
        openAlbumId = null
        _albumPhotos.value = null
        // Its photos may have changed (deleted, say), so its cover and count may have too.
        loadAlbums()
    }

    /** Reloads the open album, e.g. after one of its photos was deleted. */
    fun reloadAlbum() {
        openAlbumId?.let(::openAlbum)
    }

    suspend fun readExposure(uri: Uri): ExposureInfo? = withContext(Dispatchers.IO) { deviceAlbums.readExposure(uri) }

    /** The system's delete confirmation for a photo in another album, or null where unavailable (Android 10). */
    fun deleteRequest(photo: SyncedPhoto): IntentSender? = deviceAlbums.deleteRequest(photo)

    fun setShowPhotoInfo(show: Boolean) {
        galleryPreferences.setShowPhotoInfo(show)
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
