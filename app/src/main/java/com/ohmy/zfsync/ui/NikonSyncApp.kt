package com.ohmy.zfsync.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.ui.components.PhotoTransitionState
import com.ohmy.zfsync.ui.screens.AlbumsScreen
import com.ohmy.zfsync.ui.screens.HomeScreen
import com.ohmy.zfsync.ui.screens.PhotoViewerScreen
import com.ohmy.zfsync.ui.screens.SettingsScreen
import com.ohmy.zfsync.ui.screens.fabBottomMargin
import com.ohmy.zfsync.ui.theme.overshootExitSpec
import com.ohmy.zfsync.ui.theme.overshootSpec

/**
 * Insets the home screen pads by: top and sides only. Their photo list runs to the
 * bottom edge, behind the transparent navigation bar, and pads its own content for the bottom
 * inset so its last row can still scroll clear of the bar.
 */
private val ScreenInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun NikonSyncApp(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val themeChoice by viewModel.themeChoice.collectAsState()
    val carouselStyle by viewModel.carouselStyle.collectAsState()
    val confirmDelete by viewModel.confirmDelete.collectAsState()
    val showPhotoInfo by viewModel.showPhotoInfo.collectAsState()
    val dateStyle by viewModel.dateStyle.collectAsState()
    val downloadRaws by viewModel.downloadRaws.collectAsState()
    val homeLayout by viewModel.homeLayout.collectAsState()
    // Read again whenever the connection changes: connecting for the first time saves them.
    val savedCredentials = remember(state.connectionState) { viewModel.savedCredentials }
    // Saveable: rotation recreates the activity, and an open gallery or theme picker must survive it.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Browsing the device's other albums: the album list, and the album open in a viewer over it.
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var openAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    val albums by viewModel.albums.collectAsState()
    val albumPhotos by viewModel.albumPhotos.collectAsState()
    // Also reopens it after the activity is recreated (rotation), if the ViewModel didn't survive.
    LaunchedEffect(openAlbumId) { openAlbumId?.let(viewModel::openAlbum) }
    // Deleting another app's photo goes through the system's confirmation; reload once it's done.
    val albumDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.reloadAlbum()
    }

    // Links a photo's list row and the viewer, for the photo's move between them. Already open
    // when the viewer is restored (e.g. after rotation), so it doesn't replay.
    val photoTransition = remember { PhotoTransitionState(initiallyOpen = viewerIndex != null) }
    // Whether the home screen's FAB is showing, for the viewer's FAB (in the same place) to fade in from.
    var homeFabShown by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The screen the viewer opens from stays composed under it, keeping its scroll
            // position, so the photo can return to its row. Only while the viewer is fully open
            // (opaque, and not on its way out) is it left unplaced: then it isn't drawn, touched
            // or read by accessibility, at no cost.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val covered = viewerIndex != null &&
                                photoTransition.progress.value >= 1f &&
                                !photoTransition.closing
                            if (!covered) placeable.place(0, 0)
                        }
                    },
            ) {
                HomeScreen(
                    state = state,
                    savedCredentials = savedCredentials,
                    layout = homeLayout,
                    onLayoutChange = viewModel::setHomeLayout,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onPhotoClick = { index -> viewerIndex = index },
                    onDeletePhotos = viewModel::deletePhotos,
                    onOpenSettings = { showSettings = true },
                    onBrowseAlbums = { showAlbums = true },
                    photoTransition = photoTransition,
                    settingsOpen = showSettings,
                    fabBottomMargin = fabBottomMargin(carouselStyle),
                    onFabShownChange = { homeFabShown = it },
                    modifier = Modifier.windowInsetsPadding(ScreenInsets),
                )
            }

            // Synced photos are on the phone, so browsing them doesn't need a camera connection.
            viewerIndex?.let { openIndex ->
                // Live: photos synced while the viewer is open show up in it.
                PhotoViewerScreen(
                    photos = remember(state.photos) { state.photosNewestFirst() },
                    initialIndex = openIndex,
                    onDelete = { photo -> viewModel.deletePhotos(listOf(photo.uri)) },
                    onClose = { viewerIndex = null },
                    inFlightCount = (downloadStatus as? DownloadStatus.Downloading)?.count ?: 0,
                    carouselStyle = carouselStyle,
                    photoTransition = photoTransition,
                    confirmDelete = confirmDelete,
                    onConfirmDeleteChange = viewModel::setConfirmDelete,
                    showInfo = showPhotoInfo,
                    onShowInfoChange = viewModel::setShowPhotoInfo,
                    dateStyle = dateStyle,
                    // Whether the home screen's FAB was showing as the viewer opened.
                    fabCarriesOver = remember { homeFabShown },
                )
            }

            // Drawn over the screen that opened it rather than replacing it, so that screen (and its
            // photo selection) is still there underneath when the picker closes.
            AnimatedVisibility(
                visible = showSettings,
                enter = scaleIn(overshootSpec(), initialScale = 0.92f) + fadeIn(overshootSpec()),
                exit = scaleOut(overshootExitSpec(), targetScale = 0.92f) + fadeOut(overshootExitSpec()),
            ) {
                SettingsScreen(
                    currentTheme = themeChoice,
                    onSelectTheme = viewModel::setTheme,
                    carouselStyle = carouselStyle,
                    onCarouselStyleChange = viewModel::setCarouselStyle,
                    confirmDelete = confirmDelete,
                    onConfirmDeleteChange = viewModel::setConfirmDelete,
                    downloadRaws = downloadRaws,
                    onDownloadRawsChange = viewModel::setDownloadRaws,
                    dateStyle = dateStyle,
                    onDateStyleChange = viewModel::setDateStyle,
                    homeLayout = homeLayout,
                    onHomeLayoutChange = viewModel::setHomeLayout,
                    onClose = { showSettings = false },
                )
            }

            AnimatedVisibility(
                visible = showAlbums,
                enter = scaleIn(overshootSpec(), initialScale = 0.92f) + fadeIn(overshootSpec()),
                exit = scaleOut(overshootExitSpec(), targetScale = 0.92f) + fadeOut(overshootExitSpec()),
            ) {
                AlbumsScreen(
                    albums = albums,
                    onAccessChanged = viewModel::loadAlbums,
                    onOpenAlbum = { album -> openAlbumId = album.id },
                    onClose = { showAlbums = false },
                )
            }

            // An album from the list, in the same viewer: its photos newest first, read-only
            // except through the system (delete asks, edit saves a copy), EXIF read as needed.
            val album = albumPhotos
            if (openAlbumId != null && album != null) {
                val newestFirst = remember(album) { album.asReversed() }
                PhotoViewerScreen(
                    photos = newestFirst,
                    initialIndex = 0,
                    onDelete = { photo ->
                        viewModel.deleteRequest(photo)?.let { albumDelete.launch(IntentSenderRequest.Builder(it).build()) }
                    },
                    onClose = {
                        openAlbumId = null
                        viewModel.closeAlbum()
                    },
                    carouselStyle = carouselStyle,
                    // The system asks before deleting another app's photo; asking first too would be twice.
                    confirmDelete = false,
                    systemConfirmsDelete = true,
                    showInfo = showPhotoInfo,
                    onShowInfoChange = viewModel::setShowPhotoInfo,
                    dateStyle = dateStyle,
                    ownsPhotos = false,
                    canDelete = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    loadExposure = viewModel::readExposure,
                )
            }
        }
    }
}
