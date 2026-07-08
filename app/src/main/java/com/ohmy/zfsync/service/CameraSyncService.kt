package com.ohmy.zfsync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ohmy.zfsync.OhMyApplication
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps the PTP/IP connection and event listener alive while the app is backgrounded, so shots
 * keep auto-downloading even when the screen is off. Stops itself once the camera disconnects.
 */
class CameraSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sync_notification_watching)))

        val repository = (application as OhMyApplication).syncRepository
        scope.launch {
            repository.state.collect { state ->
                when (state.connectionState) {
                    is ConnectionState.Connected -> {
                        val count = state.downloadedPhotos.size
                        updateNotification(getString(R.string.sync_notification_connected, count))
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Error -> stopSelf()
                    is ConnectionState.Connecting -> Unit
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "camera_sync"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CameraSyncService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraSyncService::class.java))
        }
    }
}
