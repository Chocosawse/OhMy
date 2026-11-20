package com.ohmy.zfsync

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.ohmy.zfsync.storage.DeviceAlbums
import com.ohmy.zfsync.storage.ExternalPhoto
import com.ohmy.zfsync.ui.components.DateStyle
import com.ohmy.zfsync.ui.screens.CarouselStyle
import com.ohmy.zfsync.ui.screens.PhotoViewerScreen
import com.ohmy.zfsync.ui.theme.OhMyTheme
import com.ohmy.zfsync.ui.theme.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The gallery viewer for images other apps open ("Open with", a camera's review screen): the same
 * viewer as the app's own photos, in the photo's album when the app can see it, else on its own.
 * It runs in the calling app's task, so closing it (back, or the close button) returns there.
 */
class PhotoViewerActivity : ComponentActivity() {

    private val app get() = application as OhMyApplication
    private val deviceAlbums by lazy { DeviceAlbums(this) }

    /** What's being viewed; null while it's looked up. */
    private var external by mutableStateOf<ExternalPhoto?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        load(uri)

        setContent {
            val paletteName by app.themeStore.paletteName.collectAsState()
            val carouselStyleKey by app.galleryPreferences.carouselStyle.collectAsState()
            val showInfo by app.galleryPreferences.showPhotoInfo.collectAsState()
            val dateStyleKey by app.galleryPreferences.dateStyle.collectAsState()
            // Deleting goes through the system's confirmation; after it, show what's left.
            val delete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) load(uri, afterDelete = true)
            }
            OhMyTheme(choice = ThemeChoice.fromPaletteName(paletteName)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    external?.let { shown ->
                        PhotoViewerScreen(
                            photos = shown.photos,
                            initialIndex = shown.initialIndex,
                            onDelete = { photo ->
                                deviceAlbums.deleteRequest(photo)?.let { delete.launch(IntentSenderRequest.Builder(it).build()) }
                            },
                            onClose = ::finish,
                            carouselStyle = CarouselStyle.fromKey(carouselStyleKey),
                            // The system asks before deleting another app's photo.
                            confirmDelete = false,
                            showInfo = showInfo,
                            onShowInfoChange = app.galleryPreferences::setShowPhotoInfo,
                            ownsPhotos = false,
                            canDelete = shown.inLibrary && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                            loadExposure = { photoUri -> withContext(Dispatchers.IO) { deviceAlbums.readExposure(photoUri) } },
                            dateStyle = DateStyle.fromKey(dateStyleKey),
                        )
                    }
                }
            }
        }
    }

    /** Looks the photo up (and its album, if visible). After a delete, a lone photo is gone: close. */
    private fun load(uri: Uri, afterDelete: Boolean = false) {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { deviceAlbums.openExternal(uri) }
            if (afterDelete && found.photos.size <= 1 && found.photos.firstOrNull()?.uri == uri) {
                finish()
            } else {
                external = found
            }
        }
    }
}
