package com.ohmy.zfsync.sync

import android.content.Context
import android.net.Network
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.model.SyncedPhoto
import com.ohmy.zfsync.network.CameraWifiException
import com.ohmy.zfsync.network.CameraWifiManager
import com.ohmy.zfsync.ptpip.PtpEvent
import com.ohmy.zfsync.ptpip.PtpEventCode
import com.ohmy.zfsync.ptpip.PtpIpClient
import com.ohmy.zfsync.storage.PhotoSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the whole camera connection lifecycle: joins the camera's Wi-Fi AP, opens a PTP/IP
 * session, remembers which object handles were already on the card so auto-sync only reacts to
 * genuinely new shots, and saves incoming JPEGs to shared storage.
 */
class CameraSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = CameraWifiManager(appContext)
    private val photoSaver = PhotoSaver(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ptpClient: PtpIpClient? = null
    private var network: Network? = null
    private var eventJob: Job? = null
    private val knownHandles = mutableSetOf<Long>()

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    suspend fun connect(ssid: String, password: String) {
        _state.update { it.copy(connectionState = ConnectionState.Connecting, lastError = null) }
        try {
            val net = wifiManager.connect(ssid, password)
            val cameraIp = wifiManager.getCameraIpAddress(net)
                ?: throw CameraWifiException("Could not determine the camera's IP address")

            val client = PtpIpClient.connect(cameraIp, net)
            client.handshake()
            client.openSession()

            knownHandles.clear()
            knownHandles.addAll(client.getObjectHandles().toList())

            network = net
            ptpClient = client
            eventJob = client.startEventLoop(scope, ::onPtpEvent)

            _state.update { it.copy(connectionState = ConnectionState.Connected(cameraIp)) }
        } catch (t: Throwable) {
            disconnect()
            _state.update { it.copy(connectionState = ConnectionState.Error(t.message ?: "Connection failed")) }
        }
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

    fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        ptpClient?.close()
        ptpClient = null
        wifiManager.disconnect()
        network = null
        knownHandles.clear()
        _state.update { SyncUiState() }
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
            val info = client.getObjectInfo(handle)
            if (!info.isJpeg) return

            val filename = info.filename.ifBlank { "IMG_$handle.jpg" }
            val uri = photoSaver.createPendingImageUri(filename)
            try {
                photoSaver.openOutputStream(uri).use { out -> client.getObject(handle, out) }
                photoSaver.markComplete(uri)
            } catch (t: Throwable) {
                photoSaver.deleteIncomplete(uri)
                throw t
            }

            _state.update {
                it.copy(downloadedPhotos = it.downloadedPhotos + SyncedPhoto(filename, System.currentTimeMillis(), uri))
            }
        } catch (t: Throwable) {
            _state.update { it.copy(lastError = "Failed to download $handle: ${t.message}") }
        }
    }
}
