package com.ohmy.zfsync.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Size
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.ui.components.ActionFabMenu
import com.ohmy.zfsync.ui.components.CaptureTimeTag
import com.ohmy.zfsync.ui.components.ExposureCard
import com.ohmy.zfsync.ui.components.ExposureLabels
import com.ohmy.zfsync.ui.components.FabAction
import com.ohmy.zfsync.ui.components.PhotoGeometry
import com.ohmy.zfsync.ui.components.PhotoTitleCard
import com.ohmy.zfsync.ui.components.PhotoTransitionState
import com.ohmy.zfsync.ui.components.croppedGeometry
import com.ohmy.zfsync.ui.components.formatCaptureTime
import com.ohmy.zfsync.ui.components.sharePhotos
import com.ohmy.zfsync.ui.components.viewerGeometry
import com.ohmy.zfsync.ui.theme.MOTION_DURATION_MILLIS
import com.ohmy.zfsync.ui.theme.animatePop
import com.ohmy.zfsync.ui.theme.cascadeDelayMillis
import com.ohmy.zfsync.ui.theme.fadeSpec
import com.ohmy.zfsync.ui.theme.layerScale
import com.ohmy.zfsync.ui.theme.overshootSpec
import com.ohmy.zfsync.ui.theme.photoTransitionSpec
import com.ohmy.zfsync.ui.theme.pop
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-screen, swipeable viewer over the photos this app has downloaded -- built in-app rather
 * than delegated to whatever gallery app the device has, since that turned out to vary wildly in
 * practice (neither ACTION_VIEW nor ACTION_REVIEW produced a cross-photo carousel on two real
 * gallery apps tested). It follows the live album: photos synced while it's open appear at the
 * front, where the newest go, without moving the photo being viewed (pages are keyed by photo),
 * and the carousel shows a placeholder for each of the [inFlightCount] downloads still running.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoViewerScreen(
    photos: List<SyncedPhoto>,
    initialIndex: Int,
    onDelete: (SyncedPhoto) -> Unit,
    onClose: () -> Unit,
    inFlightCount: Int = 0,
    carouselStyle: CarouselStyle = CarouselStyle.Classic,
    photoTransition: PhotoTransitionState? = null,
    confirmDelete: Boolean = true,
    onConfirmDeleteChange: (Boolean) -> Unit = {},
    showInfo: Boolean = true,
    onShowInfoChange: (Boolean) -> Unit = {},
) {
    // The pager and effects outlive recompositions, so they read the live list through this.
    val latestPhotos by rememberUpdatedState(photos)
    val latestOnClose by rememberUpdatedState(onClose)
    val pagerState = rememberPagerState(initialPage = initialIndex) { latestPhotos.size }
    var pendingDelete by remember { mutableStateOf<SyncedPhoto?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Zoom lives here rather than inside each page so the screen can react to the current
    // photo's zoom (the carousel only shows at 1x). The holders are state objects, so a plain
    // map is enough: reads of their fields are what get observed.
    val zoomStates = remember { HashMap<Uri, ZoomState>() }
    fun zoomStateFor(photo: SyncedPhoto) = zoomStates.getOrPut(photo.uri) { ZoomState() }

    if (photos.isEmpty()) {
        LaunchedEffect(Unit) {
            // Nothing left to fly back: just go, leaving the transition closed for next time.
            photoTransition?.progress?.snapTo(0f)
            onClose()
        }
        return
    }

    // This screen is swapped in by Compose state, not a back-stack entry, so without this the
    // system back gesture has nothing of ours to intercept and falls through to the Activity,
    // closing the whole app instead of returning to the sync screen.
    // --- The photo's move between its list row and this screen (see PhotoTransitionState) ---
    // 0 = in the list, 1 = here; without a transition (e.g. previews) the screen is simply open.
    val progress: () -> Float = { photoTransition?.progress?.value ?: 1f }
    var screenSize by remember { mutableStateOf(GeometrySize.Zero) }
    // The photo on the move, drawn over the (hidden) pager until it lands.
    var flight by remember { mutableStateOf<Flight?>(null) }
    val imageLoader = context.imageLoader

    /** Starts a flight for [uri] if Coil already has an image of it in memory; [geometry] gets its aspect ratio. */
    fun beginFlight(uri: Uri, geometry: (aspectRatio: Float) -> Pair<PhotoGeometry, PhotoGeometry>) {
        val screenSized = imageLoader.cachedScreenPhoto(uri)
        val bitmap = screenSized ?: imageLoader.cachedThumbnail(uri) ?: return
        val (list, viewer) = geometry(bitmap.width.toFloat() / bitmap.height)
        flight = Flight(uri, bitmap.asImageBitmap(), screenSized = screenSized != null, list, viewer)
    }

    // Opening: the photo grows out of its thumbnail. Not after rotation, when it's already open.
    var animateIn by rememberSaveable { mutableStateOf(true) }
    if (photoTransition != null) {
        LaunchedEffect(photoTransition) {
            photoTransition.closing = false
            if (!animateIn) {
                photoTransition.progress.snapTo(1f)
                return@LaunchedEffect
            }
            animateIn = false
            photoTransition.progress.snapTo(0f)
            val photo = latestPhotos.getOrNull(initialIndex)
            val screen = snapshotFlow { screenSize }.first { it != GeometrySize.Zero }
            val bounds = photo?.let { photoTransition.thumbnailBounds(it.uri) }
            if (photo != null && bounds != null) {
                beginFlight(photo.uri) { aspectRatio -> croppedGeometry(bounds, aspectRatio) to viewerGeometry(screen, aspectRatio) }
                photoTransition.activeUri = photo.uri
            }
            // Without a flight (no row or image to fly from) the photo simply fades in.
            photoTransition.progress.animateTo(1f, photoTransitionSpec())
            flight = null
            photoTransition.activeUri = null
        }
    }

    // Closing: the current photo (maybe not the one opened) shrinks back into its row, which the
    // list first scrolls into view. A zoomed-in photo shrinks from where it's zoomed to.
    fun requestClose() {
        if (photoTransition == null) {
            onClose()
            return
        }
        if (photoTransition.closing) return
        photoTransition.closing = true
        coroutineScope.launch {
            val photo = latestPhotos.getOrNull(pagerState.currentPage)
            if (photo != null && screenSize != GeometrySize.Zero) {
                photoTransition.reveal(photo.uri)
                val bounds = photoTransition.thumbnailBounds(photo.uri)
                if (bounds != null) {
                    val zoom = zoomStateFor(photo)
                    beginFlight(photo.uri) { aspectRatio ->
                        croppedGeometry(bounds, aspectRatio) to viewerGeometry(screenSize, aspectRatio, zoom.scale, zoom.offset)
                    }
                    photoTransition.activeUri = photo.uri
                }
            }
            photoTransition.progress.animateTo(0f, photoTransitionSpec())
            photoTransition.activeUri = null
            latestOnClose()
        }
    }

    // A flight that set off with the thumbnail switches to the screen-sized image as soon as the
    // pager has it: a cache lookup per frame, only while flying.
    LaunchedEffect(flight) {
        val current = flight ?: return@LaunchedEffect
        if (current.screenSized) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val bitmap = imageLoader.cachedScreenPhoto(current.uri) ?: continue
            flight = current.copy(image = bitmap.asImageBitmap(), screenSized = true)
            return@LaunchedEffect
        }
    }

    // Fully here: not mid-transition. The controls only show then.
    val entered by remember {
        derivedStateOf { flight == null && photoTransition?.closing != true && progress() >= 1f }
    }

    BackHandler(onBack = ::requestClose)
    LightSystemBarIcons()

    // A photo swiped away from resets to 1x, so coming back to it starts fully zoomed out.
    LaunchedEffect(pagerState.settledPage) {
        val current = latestPhotos.getOrNull(pagerState.settledPage)?.uri
        zoomStates.forEach { (uri, zoom) -> if (uri != current) zoom.reset() }
    }
    val currentZoomedOut = zoomStateFor(photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]).scale <= 1f
    // Whether the controls (status bar, close button, carousel, pill) show. Zooming in hides them
    // and returning to 1x brings them back; a single tap toggles them at any zoom.
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(currentZoomedOut) { controlsVisible = currentZoomedOut }
    val controlsShown = controlsVisible && entered
    var actionsExpanded by remember { mutableStateOf(false) }
    StatusBarHidden(hidden = entered && !controlsVisible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it.toSize() }
            // Read in the draw phase, so the fade doesn't recompose.
            .drawBehind { drawRect(Color.Black, alpha = progress()) },
    ) {
        HorizontalPager(
            state = pagerState,
            // Hidden while a flight stands in for the current photo; otherwise fades with the
            // screen (the fallback when there's no flight).
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (flight != null) 0f else progress() },
            key = { photos[it].uri },
            // Keeps the photos either side composed, so they start loading before they're swiped
            // to rather than as they slide in.
            beyondViewportPageCount = 1,
        ) { page ->
            ZoomableImage(
                photos[page],
                zoomStateFor(photos[page]),
                // With the actions open, a tap on the photo just closes them.
                onTap = { if (actionsExpanded) actionsExpanded = false else controlsVisible = !controlsVisible },
            )
        }

        flight?.let { FlyingPhoto(it, progress) }

        // Shown and hidden with the other controls.
        val closePop = animatePop(controlsShown)
        IconButton(
            onClick = ::requestClose,
            enabled = controlsShown,
            // The photo itself runs edge to edge behind the system bars; controls stay clear of them.
            // Padded for where the status bar is even while it's hidden, so the button doesn't
            // jump up and down as zooming hides and shows the bar.
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.statusBarsIgnoringVisibility))
                .padding(8.dp)
                .pop { closePop.value },
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
        }

        val barInsets = WindowInsets.safeDrawing.union(WindowInsets.statusBarsIgnoringVisibility)
        // Top-right, mirroring the close button: shows or hides the floating info below.
        IconButton(
            onClick = { onShowInfoChange(!showInfo) },
            enabled = controlsShown,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(barInsets)
                .padding(8.dp)
                .pop { closePop.value },
        ) {
            Icon(
                // Filled while the info shows, outlined while it's hidden.
                if (showInfo) Icons.Filled.Info else Icons.Outlined.Info,
                contentDescription = stringResource(if (showInfo) R.string.viewer_hide_info else R.string.viewer_show_info),
                tint = Color.White,
            )
        }

        // The current photo's floating info: shown with the other controls, unless turned off.
        val currentPhoto = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]
        // A plain fade, not the controls' pop: these are captions on the photo, not controls.
        val infoAlpha = animateFloatAsState(
            if (controlsShown && showInfo) 1f else 0f,
            fadeSpec(),
            label = "infoAlpha",
        )
        PhotoTitleCard(
            filename = currentPhoto.filename,
            exposure = currentPhoto.exposure,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(barInsets)
                // Under the close button, and clear of the capture time on the right.
                .padding(start = 16.dp, top = 64.dp, end = 56.dp)
                .graphicsLayer { alpha = infoAlpha.value },
        )
        CaptureTimeTag(
            // The camera's own clock; files without it fall back to when the app saved them.
            text = formatCaptureTime(
                currentPhoto.exposure?.capturedAt
                    ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(currentPhoto.savedAtMillis), ZoneId.systemDefault()),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(barInsets)
                // Under the info button, level with the title card.
                .padding(top = 64.dp, end = 16.dp)
                .graphicsLayer { alpha = infoAlpha.value },
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Docked bottom-right, just above the carousel; its actions open upward over the photo.
            Box(modifier = Modifier.fillMaxWidth()) {
                // Bottom-left, level with the FAB and kept clear of it (16 + 56 + 16dp).
                ExposureCard(
                    exposure = currentPhoto.exposure,
                    labels = ExposureLabels(
                        focalLength = stringResource(R.string.viewer_exposure_focal_length),
                        aperture = stringResource(R.string.viewer_exposure_aperture),
                        shutter = stringResource(R.string.viewer_exposure_shutter),
                        iso = stringResource(R.string.viewer_exposure_iso),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 88.dp)
                        .graphicsLayer { alpha = infoAlpha.value },
                )
                ActionFabMenu(
                    actions = listOf(
                        FabAction(Icons.Default.Share, stringResource(R.string.viewer_share)) {
                            sharePhotos(context, listOf(photos[pagerState.currentPage]))
                        },
                        FabAction(Icons.Default.Delete, stringResource(R.string.viewer_delete)) {
                            val photo = photos[pagerState.currentPage]
                            if (confirmDelete) pendingDelete = photo else onDelete(photo)
                        },
                    ),
                    expanded = actionsExpanded,
                    onExpandedChange = { actionsExpanded = it },
                    // Shown and hidden with the other controls.
                    visible = controlsShown,
                    contentDescription = stringResource(R.string.viewer_actions),
                    closeDescription = stringResource(R.string.viewer_actions_close),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            CarouselIndicator(
                photos = photos,
                inFlightCount = inFlightCount,
                currentIndex = pagerState.currentPage,
                visible = controlsShown,
                style = carouselStyle,
                // Instant, not animated: dragging the strip changes photo many times a second.
                onSelect = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
            )
        }
    }

    pendingDelete?.let { photo ->
        // Only takes effect if they go on to delete: ticking it then cancelling changes nothing.
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.viewer_delete_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.viewer_delete_dialog_text))
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .toggleable(value = dontAskAgain, role = Role.Checkbox, onValueChange = { dontAskAgain = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The row is the touch target; the checkbox only shows the state.
                        Checkbox(checked = dontAskAgain, onCheckedChange = null)
                        Text(
                            stringResource(R.string.viewer_delete_dont_ask_again),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAskAgain) onConfirmDeleteChange(false)
                    // The live album drops it, and the pager moves to a neighbouring photo.
                    onDelete(photo)
                    pendingDelete = null
                }) { Text(stringResource(R.string.viewer_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.viewer_delete_cancel)) }
            },
        )
    }
}

/**
 * Switches the status and navigation bar icons to light (white) while this is in composition, and
 * restores them after: the viewer is black whatever the app's light/dark mode, and the bars are
 * transparent, so dark icons would vanish against it.
 */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val wereLightStatusBars = controller.isAppearanceLightStatusBars
        val wereLightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wereLightStatusBars
            controller.isAppearanceLightNavigationBars = wereLightNavigationBars
        }
    }
}

/**
 * Hides the status bar while [hidden], showing it again otherwise and when this leaves
 * composition. While hidden, a swipe from the top edge reveals it briefly.
 */
@Composable
private fun StatusBarHidden(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, hidden) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = previousBehavior
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The gallery carousel's look: v1 plain thumbnails, or frames on a strip of film, either black
 * (v2) or translucent amber (v3). [key] is how the choice is saved, so it must not change.
 */
enum class CarouselStyle(val key: String) {
    Classic("classic"),
    FilmStrip("film_strip"),
    AmberFilm("amber_film"),
    ;

    companion object {
        /** Unknown or missing keys fall back to [Classic]. */
        fun fromKey(key: String?): CarouselStyle = entries.firstOrNull { it.key == key } ?: Classic
    }
}

/**
 * Filmstrip of thumbnails: whichever thumbnail sits at the centre is the selected photo.
 * Dragging or flinging the strip scrolls through photos, snapping so a thumbnail always lands
 * centred, and the main pager follows. Tapping a thumbnail selects it, and whenever the photo
 * changes any other way (a tap, or swiping the main photo) the strip glides it to the centre.
 *
 * Every thumbnail keeps a fixed slot: the selected one grows by scaling, and the thumbnails either
 * side shift outward to open wider gaps around it, all drawn rather than laid out. Resizing slots
 * instead would move thumbnails under the finger mid-drag and fight the snapping.
 *
 * In-flight downloads show as placeholders at the front, where new photos land. They shift strip
 * positions relative to page numbers, so photos are found by key (their URI), not position.
 * Toggling [visible] pops the on-screen items in from both ends toward the middle, or out from
 * the middle toward both ends; while hidden the strip keeps its space but ignores touches.
 *
 * The film styles ([CarouselStyle.FilmStrip], v2, and [CarouselStyle.AmberFilm], v3; see
 * [FilmLook]) draw the same strip as film: a base, each frame's slot
 * drawing its own sprocket holes above and below (see-through, like real perforations), and every
 * tenth frame a printed edge number. The slots share one
 * hole spacing, so neighbours join into continuous perforated edges. It's all drawn (a few rects
 * and one pre-measured label per slot), never laid out, so scrolling stays cheap. Hiding folds the
 * strip shut after its frames cascade out; showing unfolds it first.
 */
@Composable
private fun CarouselIndicator(
    photos: List<SyncedPhoto>,
    inFlightCount: Int,
    currentIndex: Int,
    visible: Boolean,
    style: CarouselStyle,
    onSelect: (Int) -> Unit,
) {
    val film = style.filmLook
    val textMeasurer = rememberTextMeasurer()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val latestPhotos by rememberUpdatedState(photos)
    val latestCurrentIndex by rememberUpdatedState(currentIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // The page whose thumbnail is nearest the strip's centre; placeholders don't count.
    val centredPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .mapNotNull { item ->
                    val page = latestPhotos.indexOfFirst { it.uri == item.key }.takeIf { it >= 0 }
                    page?.let { it to abs(item.offset + item.size / 2f - centre) }
                }
                .minByOrNull { it.second }
                ?.first
        }
    }

    // Whether the strip is driving the selection: from the start of a drag until its fling and
    // snap settle. Otherwise the photo drives the strip.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var stripDriven by remember { mutableStateOf(false) }
    LaunchedEffect(dragged, listState.isScrollInProgress) {
        if (dragged) stripDriven = true else if (!listState.isScrollInProgress) stripDriven = false
    }

    // Strip -> photo: while the strip is being scrolled, the centred thumbnail is the photo.
    LaunchedEffect(listState) {
        snapshotFlow { centredPage }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { page ->
                if ((dragged || stripDriven) && page != latestCurrentIndex) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnSelect(page)
                }
            }
    }

    // Photo -> strip: glide the current thumbnail to the centre; if it's off screen, jump near it
    // first. Skipped while the strip itself is driving, since it's already centred there.
    LaunchedEffect(currentIndex, inFlightCount, stripDriven) {
        if (stripDriven) return@LaunchedEffect
        val currentKey = latestPhotos.getOrNull(currentIndex)?.uri ?: return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.key == currentKey }) {
            listState.scrollToItem(inFlightCount + currentIndex)
            withFrameNanos { }
        }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == currentKey } ?: return@LaunchedEffect
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        // Default (non-bouncing) spring: a scroll that overshoots its target reads as jitter.
        listState.animateScrollBy((item.offset + item.size / 2 - viewportCenter).toFloat())
    }

    // Which thumbnails are on screen when the toggle happens decides each one's cascade delay.
    // Read without observation: it's a one-off snapshot per toggle, not something to recompose on.
    val onScreen = remember(visible) {
        Snapshot.withoutReadObservation {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) IntRange.EMPTY else items.first().index..items.last().index
        }
    }

    // The film base and perforations fold shut once the frames have (mostly) cascaded out, and
    // unfold before they cascade back in.
    val lastFrameExitDelay =
        if (onScreen.isEmpty()) 0 else cascadeDelayMillis(onScreen.first, onScreen.first, onScreen.last, showing = false)
    val stripPop = animatePop(visible, if (visible) 0 else lastFrameExitDelay + MOTION_DURATION_MILLIS / 2)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (film != null) FilmStripHeight else CarouselHeight)
            .then(
                if (film != null) {
                    Modifier
                        .graphicsLayer {
                            val p = stripPop.value
                            scaleY = layerScale(p)
                            alpha = p.coerceIn(0f, 1f)
                            // One offscreen layer for the whole strip, so the sprocket holes the
                            // frames clear out of their film reveal the photo behind the strip,
                            // rather than punching through to the window.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        // Half the strip on each side, so the first and last thumbnails can reach the centre.
        val sidePadding = ((maxWidth - ThumbnailSize) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(ThumbnailSpacing),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = visible,
            // Snaps the nearest thumbnail to the centre when a drag or fling ends.
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            // Cascade delays go by position in the strip, placeholders included.
            items(inFlightCount, key = { "placeholder-$it" }) { index ->
                val placeholderPop = animatePop(visible, cascadeDelayMillis(index, onScreen.first, onScreen.last, showing = visible))
                CarouselSlot(
                    film = film,
                    frameNumber = null,
                    textMeasurer = textMeasurer,
                    // Placeholders are always before the photos, so always on the selection's left.
                    shift = { -SelectionShift },
                    leadsStrip = index == 0,
                ) {
                    PlaceholderThumbnail(Modifier.pop { placeholderPop.value })
                }
            }
            itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
                val selected = index == currentIndex
                val scale by animateFloatAsState(if (selected) SelectedThumbnailScale else 1f, overshootSpec(), label = "thumbScale")
                val shift by animateDpAsState(
                    when {
                        index < currentIndex -> -SelectionShift
                        index > currentIndex -> SelectionShift
                        else -> 0.dp
                    },
                    overshootSpec(),
                    label = "thumbShift",
                )
                // 0 at normal size, 1 fully enlarged; the overshoot can briefly take it past either end.
                val growth = { (scale - 1f) / (SelectedThumbnailScale - 1f) }
                // On the film strip, a frame develops from negative into positive as it grows into
                // the selection, and back as it shrinks. Derived, so a frame only recomposes as it
                // starts or finishes doing so, not every animation frame.
                val developing by remember(film) { derivedStateOf { film != null && scale > 1f } }
                val frameShape = film?.frameShape ?: RectangleShape
                val position = inFlightCount + index
                val thumbPop = animatePop(visible, cascadeDelayMillis(position, onScreen.first, onScreen.last, showing = visible))
                CarouselSlot(
                    film = film,
                    // Printed every tenth frame from 00, like the edge numbers on real film.
                    frameNumber = index.takeIf { it % EDGE_NUMBER_EVERY == 0 },
                    textMeasurer = textMeasurer,
                    shift = { shift },
                    // Tracks the selected thumbnail's growth, which runs in step with its
                    // neighbours' shift, so its film always just meets theirs.
                    selectionGrowth = growth,
                    // The image's drawn size: popping in or out, times its selection scale.
                    frameScale = { thumbPop.value.coerceAtLeast(0f) * scale },
                    leadsStrip = inFlightCount == 0 && index == 0,
                    endsStrip = index == photos.lastIndex,
                    // Above its neighbours in case they briefly overlap mid-animation.
                    modifier = Modifier.zIndex(if (selected) 1f else 0f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ThumbnailSize)
                            .pop { thumbPop.value }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                // Rounded film frames are clipped in this same layer: no extra one.
                                if (frameShape != RectangleShape) {
                                    shape = frameShape
                                    clip = true
                                }
                            }
                            .then(
                                // Thinner before scaling, so it's 2dp once the thumbnail is enlarged.
                                if (selected) Modifier.border((2f / SelectedThumbnailScale).dp, Color.White, frameShape) else Modifier,
                            )
                            .clickable(enabled = visible) { latestOnSelect(index) },
                    ) {
                        // Developing: the positive underneath, the negative fading off it. Only
                        // while developing (the selected frame, and one returning to the strip),
                        // so every other frame stays a single draw with no extra layer. Both
                        // load the same cached thumbnail.
                        if (developing) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.filename,
                            contentScale = ContentScale.Crop,
                            // Frames on the film strip are its negatives.
                            colorFilter = film?.negative,
                            modifier = Modifier
                                .matchParentSize()
                                .then(
                                    if (developing) {
                                        // Read in the draw phase: the fade follows the enlarge
                                        // animation without recomposing.
                                        Modifier.graphicsLayer { alpha = 1f - growth().coerceIn(0f, 1f) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One carousel item's slot, drawn offset by [shift] (the outward shift that opens room around the
 * selection) with everything in it. Classic: just the thumbnail. Film strip: the frame's own piece
 * of film behind the thumbnail, so film and frame always move together: the base, sprocket holes
 * above and below, and the edge number ([frameNumber]; null for unnumbered frames and
 * placeholders), measured once and only drawn after that.
 *
 * Each piece spans its slot plus half the gap either side, so neighbours join seamlessly. The
 * selected slot's piece widens by [selectionGrowth] (0 to 1) of the neighbours' shift, to bridge
 * the gap that opens; [leadsStrip] / [endsStrip] add a short leader so the film ends just past
 * the first and last frames.
 */
@Composable
private fun CarouselSlot(
    film: FilmLook?,
    frameNumber: Int?,
    textMeasurer: TextMeasurer,
    shift: () -> Dp,
    modifier: Modifier = Modifier,
    selectionGrowth: () -> Float = { 0f },
    frameScale: () -> Float = { 0f },
    leadsStrip: Boolean = false,
    endsStrip: Boolean = false,
    content: @Composable () -> Unit,
) {
    val slotModifier = if (film != null) {
        val edgeLabel = remember(frameNumber, textMeasurer, film) {
            frameNumber?.let { textMeasurer.measure(it.toString().padStart(2, '0'), film.edgeTextStyle) }
        }
        Modifier
            .width(ThumbnailSize)
            .fillMaxHeight()
            .drawBehind {
                drawFilmPiece(
                    look = film,
                    edgeLabel = edgeLabel,
                    widen = SelectionShift.toPx() * selectionGrowth(),
                    lead = if (leadsStrip) FilmLeader.toPx() else 0f,
                    tail = if (endsStrip) FilmLeader.toPx() else 0f,
                    frameSize = if (film.clearBaseUnderFrames) ThumbnailSize.toPx() * frameScale() else 0f,
                )
            }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer { translationX = shift().toPx() }
            .then(slotModifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A frame's piece of film: the base across its slot and half the gap either side (plus [widen]
 * both ways, and [lead] / [tail] at the ends of the strip), a row of sprocket holes above and
 * below centred on the frame, and the printed edge number above the top row. Every piece spaces
 * its holes the same, so neighbouring pieces join into one continuous, evenly perforated edge.
 */
private fun DrawScope.drawFilmPiece(
    look: FilmLook,
    edgeLabel: TextLayoutResult?,
    widen: Float,
    lead: Float,
    tail: Float,
    frameSize: Float,
) {
    val halfGap = ThumbnailSpacing.toPx() / 2
    val left = -halfGap - widen - lead
    val right = size.width + halfGap + widen + tail
    drawRect(look.base, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(right - left, size.height))
    // Translucent film only: the base cleared from under the frame's image ([frameSize] square,
    // centred, as currently drawn), since the negative's own clear areas already are the base.
    // Without it the frame would read as a denser box.
    if (frameSize > 0f) {
        drawRect(
            Color.Black,
            topLeft = Offset((size.width - frameSize) / 2f, (size.height - frameSize) / 2f),
            size = androidx.compose.ui.geometry.Size(frameSize, frameSize),
            blendMode = BlendMode.Clear,
        )
    }

    val pitch = (ThumbnailSize + ThumbnailSpacing).toPx() / SPROCKETS_PER_FRAME
    val holeWidth = SprocketWidth.toPx()
    val holeHeight = SprocketHeight.toPx()
    val corner = CornerRadius(SprocketCornerRadius.toPx())
    val topRow = SprocketInset.toPx()
    val bottomRow = size.height - topRow - holeHeight
    // Fully qualified: this file also imports Coil's Size, for full-resolution loading.
    val hole = androidx.compose.ui.geometry.Size(holeWidth, holeHeight)
    val rim = Stroke(width = SprocketRimWidth.toPx())
    repeat(SPROCKETS_PER_FRAME) { i ->
        // Centred on the frame: the holes span the slot plus half the gap either side.
        val x = -halfGap + i * pitch + (pitch - holeWidth) / 2
        for (y in floatArrayOf(topRow, bottomRow)) {
            // Real holes: cleared out of the film so the photo behind shows through, with a faint
            // rim so they still read over dark photos.
            drawRoundRect(Color.Black, Offset(x, y), hole, corner, blendMode = BlendMode.Clear)
            drawRoundRect(SprocketRim, Offset(x, y), hole, corner, style = rim)
        }
    }
    if (edgeLabel != null) {
        drawText(edgeLabel, topLeft = Offset(EdgeLabelInset.toPx(), (topRow - edgeLabel.size.height) / 2f))
    }
}

/** Stands in for a photo still downloading, until its thumbnail replaces it. */
@Composable
private fun PlaceholderThumbnail(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sync_placeholder_title)
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .background(Color.White.copy(alpha = 0.15f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

private val ThumbnailSize = 40.dp

private val SelectedThumbnailSize = 56.dp

/** The selected thumbnail is drawn at [SelectedThumbnailSize] by scaling its [ThumbnailSize] slot. */
private val SelectedThumbnailScale = SelectedThumbnailSize / ThumbnailSize

/** Gap between thumbnails throughout the strip... */
private val ThumbnailSpacing = 4.dp

/** ...except either side of the selected thumbnail. */
private val SelectedThumbnailSpacing = 4.dp

/**
 * How far the thumbnails either side of the selection are drawn outward: enough to clear its
 * growth and widen its two gaps from [ThumbnailSpacing] to [SelectedThumbnailSpacing].
 */
private val SelectionShift = (SelectedThumbnailSize - ThumbnailSize) / 2 + (SelectedThumbnailSpacing - ThumbnailSpacing)

/** Leaves room above and below the current thumbnail for its grow animation to overshoot. */
private val CarouselHeight = 60.dp

// Film strip (v2): tall enough for the perforated edges around the frames; the selected frame
// grows over them.
private val FilmStripHeight = 72.dp
private const val SPROCKETS_PER_FRAME = 4
private const val EDGE_NUMBER_EVERY = 10
private val SprocketWidth = 5.dp
private val SprocketHeight = 4.dp
private val SprocketCornerRadius = 1.dp
/** Distance of each sprocket row from its edge of the strip; the edge number sits above the top row. */
private val SprocketInset = 9.dp
private val EdgeLabelInset = 2.dp
/** Film past the first and last frames, so the strip ends just beyond them. */
private val FilmLeader = 4.dp
/**
 * One film carousel style's materials: the strip's [base], the [negative] color filter its frames
 * are drawn through, its edge numbers' [edgeTextStyle], and whether the base is cleared from under
 * each frame ([clearBaseUnderFrames], for translucent film, where base under the frame would read
 * as a denser box), and the shape its frames are clipped to ([frameShape]).
 */
@Immutable
private class FilmLook(
    val base: Color,
    val negative: ColorFilter,
    val edgeTextStyle: TextStyle,
    val clearBaseUnderFrames: Boolean,
    val frameShape: Shape = RectangleShape,
)

private val CarouselStyle.filmLook: FilmLook?
    get() = when (this) {
        CarouselStyle.Classic -> null
        CarouselStyle.FilmStrip -> BlackFilmLook
        CarouselStyle.AmberFilm -> AmberFilmLook
    }

/**
 * v2: a black strip, with negatives in colors sampled from a photo of color negatives on a light
 * table. Where the photo was darkest the film is clear, showing the base's deep copper-orange;
 * where it was brightest it's densest, a dark red-brown. Opaque, sitting on the black.
 */
private val BlackFilmLook = FilmLook(
    base = Color(0xFF111111),
    negative = negativeFilter(dense = Color(0xFF57200C), clear = Color(0xFFB56244)),
    edgeTextStyle = filmEdgeTextStyle(Color(0xCCE0A84A)),
    clearBaseUnderFrames = false,
    // Slight squircles: a rounded rectangle (about 15% of the frame) rather than a true
    // superellipse, which as a path clip is only anti-aliased by the GPU from Android 13.
    frameShape = RoundedCornerShape(6.dp),
)

/**
 * v3: the whole strip is translucent amber film base, and the frames' negatives run from that
 * same amber (clear) to a deep red-brown (dense); denser film is less translucent, as on real
 * film. Edge numbers in the dense color, opaque, to stay legible over the photo behind.
 */
private val AmberFilmClear = Color(0x88C65731)
private val AmberFilmLook = FilmLook(
    base = AmberFilmClear,
    negative = negativeFilter(dense = Color(0xCC4A0003), clear = AmberFilmClear),
    edgeTextStyle = filmEdgeTextStyle(Color(0xFF4A0003)),
    clearBaseUnderFrames = true,
)

/**
 * Turns a thumbnail into a toned negative in a single color matrix: luminance (Rec. 601 weights),
 * inverted, then mapped onto the ramp from [clear] (where the photo was darkest) to [dense]
 * (brightest). Per channel c, alpha included, so translucency can follow density:
 * out = clear_c - (clear_c - dense_c) * luminance.
 *
 * Applied by the GPU as the thumbnail draws (the paint's color filter), so it costs nothing per
 * frame and leaves Coil's cached images untouched (the same thumbnails serve the list and the
 * viewer). One instance per style, shared by every frame on the strip.
 */
private fun negativeFilter(dense: Color, clear: Color): ColorFilter {
    val (wr, wg, wb) = Triple(0.299f, 0.587f, 0.114f)
    val denseChannels = floatArrayOf(dense.red, dense.green, dense.blue, dense.alpha)
    val clearChannels = floatArrayOf(clear.red, clear.green, clear.blue, clear.alpha)
    // Rows for red, green, blue and alpha; the thumbnail's own alpha (always opaque) isn't used.
    val rows = (0..3).flatMap { c ->
        val range = clearChannels[c] - denseChannels[c]
        // Matrix offsets are on the 0-255 scale; the multipliers act on 0-255 channel values too.
        listOf(-range * wr, -range * wg, -range * wb, 0f, clearChannels[c] * 255f)
    }
    return ColorFilter.colorMatrix(ColorMatrix(rows.toFloatArray()))
}

private val SprocketRim = Color.White.copy(alpha = 0.35f)
private val SprocketRimWidth = 0.75.dp
private fun filmEdgeTextStyle(color: Color) = TextStyle(
    color = color,
    fontSize = 7.sp,
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.5.sp,
)

@Stable
/** Coil memory cache key of a photo's screen-sized image, as the viewer loads it. */
private fun screenCacheKey(uri: Uri) = "$uri-screen"

/** Coil's default memory cache key for a photo loaded by URI alone (no transformations), as the list's thumbnails are. */
private fun thumbnailCacheKey(uri: Uri) = uri.toString()

private fun ImageLoader.cachedScreenPhoto(uri: Uri): Bitmap? = memoryCache?.get(MemoryCache.Key(screenCacheKey(uri)))?.bitmap

private fun ImageLoader.cachedThumbnail(uri: Uri): Bitmap? = memoryCache?.get(MemoryCache.Key(thumbnailCacheKey(uri)))?.bitmap

/**
 * The photo on its way between its list row ([list]) and this screen ([viewer]), drawn from an
 * image already in memory: no decoding, so it's ready the moment the transition starts.
 */
@Immutable
private data class Flight(
    val uri: Uri,
    val image: ImageBitmap,
    val screenSized: Boolean,
    val list: PhotoGeometry,
    val viewer: PhotoGeometry,
)

/**
 * Draws [flight] between its two layouts at [progress]: the whole image's rect and the crop it's
 * seen through both move, so the square thumbnail uncrops into the full photo. Draw phase only:
 * no layout or recomposition per frame.
 */
@Composable
private fun FlyingPhoto(flight: Flight, progress: () -> Float) {
    Spacer(
        modifier = Modifier.fillMaxSize().drawBehind {
            val geometry = flight.list.lerp(flight.viewer, progress())
            val clip = geometry.clip
            val image = geometry.image
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                drawImage(
                    flight.image,
                    dstOffset = IntOffset(image.left.roundToInt(), image.top.roundToInt()),
                    dstSize = IntSize(image.width.roundToInt(), image.height.roundToInt()),
                )
            }
        },
    )
}

private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

/** Double-tap zooms to at least this; more if that's what it takes for the photo to fill the screen. */
private const val DOUBLE_TAP_ZOOM = 2f

/** Pinch limit; raised to the fill scale for photos that need more than this to fill the screen. */
private const val MAX_ZOOM = 5f

@Composable
private fun ZoomableImage(photo: SyncedPhoto, zoom: ZoomState, onTap: () -> Unit) {
    // The gesture detectors below are set up once per photo, so they read the latest callback through this.
    val latestOnTap by rememberUpdatedState(onTap)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // The decoded photo's size, known once it loads; needed to work out the fill-the-screen scale.
    var imageSize by remember(photo.uri) { mutableStateOf(GeometrySize.Unspecified) }

    // Two sizes, each under its own cache key so neither is mistaken for the other (or for the
    // thumbnails, cached under the bare URI):
    // - Screen-sized, for viewing at 1x: no size given, so Coil sizes it to this composable, and
    //   decodes by subsampling -- far quicker than a full ~24MP decode, which is what left pages
    //   black while they slid in. Shows the thumbnail meanwhile.
    // - Full resolution, only once the photo is zoomed in, showing the screen-sized one meanwhile.
    //   Kept for the rest of the page's life, so zooming out and in again doesn't reload it.
    val screenRequest = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .memoryCacheKey(screenCacheKey(photo.uri))
            .placeholderMemoryCacheKey(thumbnailCacheKey(photo.uri))
            .build()
    }
    val fullSizeRequest = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .size(Size.ORIGINAL)
            .memoryCacheKey("${photo.uri}-full")
            .placeholderMemoryCacheKey(screenCacheKey(photo.uri))
            .build()
    }
    val zoomedIn by remember(zoom) { derivedStateOf { zoom.scale > 1f } }
    var fullSizeWanted by remember(photo.uri) { mutableStateOf(false) }
    LaunchedEffect(zoomedIn) { if (zoomedIn) fullSizeWanted = true }

    AsyncImage(
        model = if (fullSizeWanted) fullSizeRequest else screenRequest,
        contentDescription = photo.filename,
        contentScale = ContentScale.Fit,
        // Only the size's aspect ratio is used, and both requests share it.
        onSuccess = { imageSize = it.painter.intrinsicSize },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.uri) {
                detectTapGestures(onTap = { latestOnTap() }, onDoubleTap = { tap ->
                    val geometry = ZoomGeometry(size.width.toFloat(), size.height.toFloat(), imageSize)
                    val fromScale = zoom.scale
                    val fromOffset = zoom.offset
                    val zoomingIn = zoom.scale <= 1f
                    val toScale = if (zoomingIn) maxOf(DOUBLE_TAP_ZOOM, geometry.fillScale) else 1f
                    val toOffset = if (zoomingIn) geometry.offsetFor(tap, toScale) else Offset.Zero
                    coroutineScope.launch {
                        // Default critically-damped spring, deliberately not the overshoot curve:
                        // zooming past the target and back reads as jitter on a full-screen photo.
                        animate(0f, 1f) { fraction, _ ->
                            zoom.scale = fromScale + (toScale - fromScale) * fraction
                            zoom.offset = lerp(fromOffset, toOffset, fraction)
                        }
                    }
                })
            }
            .pointerInput(photo.uri) {
                // Unlike detectTransformGestures, only consume what this image actually uses: a
                // one-finger drag at 1x is left unconsumed so the parent HorizontalPager can turn
                // it into a page swipe, while pinches (and pans once zoomed in) stay here.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || zoom.scale > 1f) {
                            val maxScale = maxOf(MAX_ZOOM, ZoomGeometry(size.width.toFloat(), size.height.toFloat(), imageSize).fillScale)
                            val newScale = (zoom.scale * event.calculateZoom()).coerceIn(1f, maxScale)
                            zoom.scale = newScale
                            zoom.offset = if (newScale <= 1f) Offset.Zero else zoom.offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = zoom.scale,
                scaleY = zoom.scale,
                translationX = zoom.offset.x,
                translationY = zoom.offset.y,
            ),
    )
}

/**
 * Where the photo sits, in the screen's pixels. At 1x it's fitted (ContentScale.Fit) and centred
 * on the screen; a zoom scales it about the screen's centre, then translates it by an offset.
 */
private class ZoomGeometry(
    private val screenWidth: Float,
    private val screenHeight: Float,
    imageSize: GeometrySize,
) {
    private val screenCenter = Offset(screenWidth / 2f, screenHeight / 2f)

    /** The photo's size on screen at 1x, or null until it has loaded. */
    private val fitted: GeometrySize? = imageSize
        .takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
        ?.let {
            val fit = minOf(screenWidth / it.width, screenHeight / it.height)
            GeometrySize(it.width * fit, it.height * fit)
        }

    /** The scale at which the photo covers the whole screen, with no black bars on any side. */
    val fillScale: Float = fitted?.let { maxOf(screenWidth / it.width, screenHeight / it.height) } ?: 1f

    /**
     * The offset that, at [scale], keeps [tap] under the finger: a point p lands at
     * center + (p - center) * scale + offset, so offset = (center - p) * (scale - 1). Then nudged
     * just enough that the photo still covers the screen on each axis it's big enough to, and
     * centred on the screen on any axis it isn't.
     */
    fun offsetFor(tap: Offset, scale: Float): Offset {
        val keepTap = (screenCenter - tap) * (scale - 1f)
        val size = fitted ?: return keepTap
        return Offset(
            cover(keepTap.x, screenCenter.x, size.width * scale, screenWidth),
            cover(keepTap.y, screenCenter.y, size.height * scale, screenHeight),
        )
    }

    /** One axis of [offsetFor]: the photo spans center + offset ± extent / 2, the screen 0..screenExtent. */
    private fun cover(offset: Float, center: Float, extent: Float, screenExtent: Float): Float =
        if (extent <= screenExtent) {
            screenExtent / 2f - center
        } else {
            offset.coerceIn(screenExtent - center - extent / 2f, extent / 2f - center)
        }
}
