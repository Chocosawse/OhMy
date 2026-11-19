package com.ohmy.zfsync.sync

import android.content.Context
import android.net.Network
import android.net.Uri
import android.util.Log
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.network.CameraWifiException
import com.ohmy.zfsync.network.CameraWifiManager
import com.ohmy.zfsync.ptpip.PtpEvent
import com.ohmy.zfsync.ptpip.PtpEventCode
import com.ohmy.zfsync.ptpip.PtpIpClient
import com.ohmy.zfsync.storage.CameraCredentialsStore
import com.ohmy.zfsync.storage.PhotoSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "OhMyCameraSync"

/**
 * Owns the whole camera connection lifecycle: joins the camera's Wi-Fi AP, opens a PTP/IP
 * session, remembers which object handles were already on the card so auto-sync only reacts to
 * genuinely new shots, and saves incoming JPEGs to shared storage.
 */
class CameraSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = CameraWifiManager(appContext)
    private val photoSaver = PhotoSaver(appContext)
    private val credentialsStore = CameraCredentialsStore(appContext)
    private val downloadStatusTracker = DownloadStatusTracker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ptpClient: PtpIpClient? = null
    private var network: Network? = null
    private var eventJob: Job? = null
    private val knownHandles = mutableSetOf<Long>()

    /** The credentials actually in use, kept so a mid-session Wi-Fi loss can be retried with them. */
    private var activeSsid: String? = null
    private var activePassword: String? = null

    /** Set only when a previously-working connection drops on its own (see [handleConnectionLost]),
     *  as opposed to a plain failed/declined connect attempt -- so a caller can tell "this is worth
     *  silently retrying once the app is back in the foreground" apart from "the user is mid-way
     *  through typing credentials on the connect screen." */
    private var lostConnectionCredentials: SavedCameraCredentials? = null

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /** Independent of [state]: whether a photo is being downloaded right now, for anything that
     *  only cares about that (see [DownloadStatusTracker]). */
    val downloadStatus: StateFlow<DownloadStatus> = downloadStatusTracker.status

    /** Serializes album reloads against downloads adding to the list, so a reload whose query ran
     *  just before a download finished can't overwrite the list and drop that new photo. */
    private val photosMutex = Mutex()

    init {
        refreshPhotos()
    }

    /** Reloads [SyncUiState.photos] from the album, picking up anything deleted or changed outside the app. */
    fun refreshPhotos() {
        scope.launch {
            try {
                photosMutex.withLock {
                    val photos = photoSaver.loadAll()
                    _state.update { it.copy(photos = photos) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load album: $t")
            }
        }
    }

    fun loadSavedCredentials(): SavedCameraCredentials? = credentialsStore.load()

    /** Returns and clears the credentials of a connection that dropped on its own, if any -- for a
     *  caller to retry once the app is back in the foreground (see [lostConnectionCredentials]). */
    fun consumeLostConnectionCredentials(): SavedCameraCredentials? =
        lostConnectionCredentials.also { lostConnectionCredentials = null }

    suspend fun connect(ssid: String, password: String) {
        activeSsid = ssid
        activePassword = password
        lostConnectionCredentials = null
        _state.update { it.copy(connectionState = ConnectionState.Connecting, lastError = null) }
        try {
            val net = stage("joining Wi-Fi") {
                wifiManager.connect(ssid, password, onLost = { handleConnectionLost("camera Wi-Fi network lost") })
            }
            val cameraIp = stage("resolving camera IP") {
                wifiManager.getCameraIpAddress(net) ?: throw CameraWifiException("Could not determine the camera's IP address")
            }

            val client = stage("opening PTP/IP connection") { connectPtpIpWithRetry(cameraIp, net) }
            stage("opening PTP session") { client.openSession() }

            knownHandles.clear()
            knownHandles.addAll(stage("listing existing photos") { client.getObjectHandles() }.toList())

            network = net
            ptpClient = client
            eventJob = client.startEventLoop(
                scope,
                onEvent = ::onPtpEvent,
                onClosed = { t -> handleConnectionLost("PTP/IP event channel closed: ${t.message}") },
            )
            credentialsStore.save(ssid, password)

            _state.update { it.copy(connectionState = ConnectionState.Connected(cameraIp)) }
        } catch (t: Throwable) {
            teardownResources()
            _state.update { it.copy(connectionState = ConnectionState.Error(t.message ?: "Connection failed")) }
        }
    }

    /**
     * Called when an established session dies on its own: either the Wi-Fi network was torn down
     * (the camera's AP has no internet capability, so Android may reclaim it once the app is
     * backgrounded, even with [CameraSyncService] running) or the camera closed the PTP/IP event
     * channel. Retrying immediately would likely fail the same way while still backgrounded (a
     * WifiNetworkSpecifier join can need a system dialog that can't be shown off-screen), so this
     * surfaces the drop and remembers the credentials; [consumeLostConnectionCredentials] lets a
     * caller retry once the app is back in front of the user.
     */
    @Synchronized
    private fun handleConnectionLost(reason: String) {
        // Both signals can fire for one drop; only the first one for a live session counts.
        if (_state.value.connectionState !is ConnectionState.Connected) return
        Log.w(TAG, "Connection lost: $reason")
        val ssid = activeSsid
        val password = activePassword
        teardownResources()
        if (ssid != null && password != null) {
            lostConnectionCredentials = SavedCameraCredentials(ssid, password)
        }
        _state.update { it.copy(connectionState = ConnectionState.Error("Lost connection to the camera")) }
    }

    /** Tears down the live connection's resources without touching [_state] beyond what callers do themselves. */
    private fun teardownResources() {
        eventJob?.cancel()
        eventJob = null
        ptpClient?.close()
        ptpClient = null
        wifiManager.disconnect()
        network = null
        knownHandles.clear()
    }

    /** Wraps a connection stage so failures report where in the sequence they happened. */
    private inline fun <T> stage(name: String, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw Exception("Failed while $name: ${t.message}", t)
    }

    /**
     * Right after the phone's Wi-Fi radio hands back a usable [Network] for the camera's AP, the
     * camera's own embedded PTP/IP responder can still be a moment away from actually accepting
     * connections (the IP layer coming up doesn't guarantee its application-level server thread
     * is ready yet). A cold TCP connect attempted in that window gets accepted and then reset
     * with zero bytes read. Retrying a few times with a short backoff clears this up without
     * needing to detect readiness some other way.
     */
    private suspend fun connectPtpIpWithRetry(cameraIp: String, net: Network): PtpIpClient {
        var lastError: Throwable? = null
        repeat(PTP_CONNECT_ATTEMPTS) { attempt ->
            Log.d(TAG, "PTP/IP connect attempt ${attempt + 1}/$PTP_CONNECT_ATTEMPTS to $cameraIp")
            var client: PtpIpClient? = null
            try {
                client = PtpIpClient.connect(cameraIp, net)
                client.handshake()
                Log.d(TAG, "PTP/IP connect attempt ${attempt + 1} succeeded")
                return client
            } catch (t: Throwable) {
                Log.e(TAG, "PTP/IP connect attempt ${attempt + 1} failed: $t")
                client?.close()
                lastError = t
                if (attempt < PTP_CONNECT_ATTEMPTS - 1) delay(PTP_CONNECT_RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IllegalStateException("Could not connect to camera")
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _state.update { it.copy(autoSyncEnabled = enabled) }
    }

    /** Fetches every object handle the camera reports that we haven't already downloaded. */
    suspend fun syncNow() {
        val client = ptpClient ?: return
        _state.update { it.copy(isSyncing = true) }
        try {
            val newHandles = client.getObjectHandles().filter { knownHandles.add(it) }
            for (handle in newHandles) downloadObject(handle)
        } catch (t: Throwable) {
            _state.update { it.copy(lastError = t.message ?: "Sync failed") }
        } finally {
            _state.update { it.copy(isSyncing = false) }
        }
    }

    /** Deletes previously-downloaded photos (the local copies only -- the camera's card is untouched).
     *  Each is attempted independently, so one failure doesn't leave the rest undeleted. */
    suspend fun deletePhotos(uris: Collection<Uri>) {
        val deleted = mutableSetOf<Uri>()
        var lastFailure: Throwable? = null
        withContext(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    photoSaver.delete(uri)
                    deleted += uri
                } catch (t: Throwable) {
                    lastFailure = t
                }
            }
        }
        val failedCount = uris.size - deleted.size
        _state.update {
            it.copy(
                photos = it.photos.filterNot { photo -> photo.uri in deleted },
                sessionPhotoUris = it.sessionPhotoUris - deleted,
                lastError = lastFailure?.let { t -> "Failed to delete $failedCount photo(s): ${t.message}" } ?: it.lastError,
            )
        }
    }

    fun disconnect() {
        teardownResources()
        activeSsid = null
        activePassword = null
        lostConnectionCredentials = null
        // The album outlives the connection; only the session-related state resets.
        _state.update { SyncUiState(photos = it.photos) }
    }

    private fun onPtpEvent(event: PtpEvent) {
        if (event.code != PtpEventCode.OBJECT_ADDED) return
        if (!_state.value.autoSyncEnabled) return
        val handle = event.parameters.getOrNull(0) ?: return
        if (!knownHandles.add(handle)) return
        scope.launch { downloadObject(handle) }
    }

    private suspend fun downloadObject(handle: Long) {
        val client = ptpClient ?: return
        try {
            // Checked before tracking so a RAW file (skipped here) never counts as a download in
            // flight -- otherwise a RAW+JPEG shot briefly reports two incoming photos.
            val info = client.getObjectInfo(handle)
            if (!info.isJpeg) return

            // The photo is added to the list inside track(), before the in-flight count drops,
            // so UI showing a placeholder per in-flight download swaps it for the real row.
            downloadStatusTracker.track {
                val filename = info.filename.ifBlank { "IMG_$handle.jpg" }
                val uri = photoSaver.createPendingImageUri(filename)
                try {
                    photoSaver.openOutputStream(uri).use { out -> client.getObject(handle, out) }
                    photoSaver.markComplete(uri)
                } catch (t: Throwable) {
                    photoSaver.deleteIncomplete(uri)
                    throw t
                }
                // Manual syncs run from the main thread; reading EXIF is file I/O.
                val exposure = withContext(Dispatchers.IO) { photoSaver.readExposure(uri) }

                photosMutex.withLock {
                    _state.update {
                        val photos = if (it.photos.any { photo -> photo.uri == uri }) {
                            it.photos
                        } else {
                            it.photos + SyncedPhoto(filename, System.currentTimeMillis(), uri, exposure)
                        }
                        it.copy(photos = photos, sessionPhotoUris = it.sessionPhotoUris + uri)
                    }
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(lastError = "Failed to download $handle: ${t.message}") }
        }
    }

    private companion object {
        const val PTP_CONNECT_ATTEMPTS = 4
        const val PTP_CONNECT_RETRY_DELAY_MS = 1_500L
    }
}
