package com.ohmy.zfsync.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.ui.components.ActionPill
import com.ohmy.zfsync.ui.components.sharePhotos
import com.ohmy.zfsync.ui.theme.animatePop
import com.ohmy.zfsync.ui.theme.cascadeDelayMillis
import com.ohmy.zfsync.ui.theme.overshootSpec
import com.ohmy.zfsync.ui.theme.pop
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
) {
    // The pager and effects outlive recompositions, so they read the live list through this.
    val latestPhotos by rememberUpdatedState(photos)
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
        LaunchedEffect(Unit) { onClose() }
        return
    }

    // This screen is swapped in by Compose state, not a back-stack entry, so without this the
    // system back gesture has nothing of ours to intercept and falls through to the Activity,
    // closing the whole app instead of returning to the sync screen.
    BackHandler(onBack = onClose)
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
    StatusBarHidden(hidden = !controlsVisible)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].uri },
        ) { page ->
            ZoomableImage(photos[page], zoomStateFor(photos[page]), onTap = { controlsVisible = !controlsVisible })
        }

        // Shown and hidden with the other controls.
        val closePop = animatePop(controlsVisible)
        IconButton(
            onClick = onClose,
            enabled = controlsVisible,
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

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionPill(
                // Shown and hidden with the other controls.
                visible = controlsVisible,
                onShare = { sharePhotos(context, listOf(photos[pagerState.currentPage])) },
                onDelete = { pendingDelete = photos[pagerState.currentPage] },
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            )
            Spacer(modifier = Modifier.height(16.dp))
            CarouselIndicator(
                photos = photos,
                inFlightCount = inFlightCount,
                currentIndex = pagerState.currentPage,
                visible = controlsVisible,
                // Instant, not animated: dragging the strip changes photo many times a second.
                onSelect = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
            )
        }
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.viewer_delete_dialog_title)) },
            text = { Text(stringResource(R.string.viewer_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
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
 */
@Composable
private fun CarouselIndicator(
    photos: List<SyncedPhoto>,
    inFlightCount: Int,
    currentIndex: Int,
    visible: Boolean,
    onSelect: (Int) -> Unit,
) {
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
        listState.animateScrollBy((item.offset + item.size / 2 - viewportCenter).toFloat(), overshootSpec())
    }

    // Which thumbnails are on screen when the toggle happens decides each one's cascade delay.
    // Read without observation: it's a one-off snapshot per toggle, not something to recompose on.
    val onScreen = remember(visible) {
        Snapshot.withoutReadObservation {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) IntRange.EMPTY else items.first().index..items.last().index
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(CarouselHeight)) {
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
                // Placeholders are always before the photos, so always on the selection's left.
                PlaceholderThumbnail(
                    Modifier
                        .pop { placeholderPop.value }
                        .graphicsLayer { translationX = -SelectionShift.toPx() },
                )
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
                val position = inFlightCount + index
                val thumbPop = animatePop(visible, cascadeDelayMillis(position, onScreen.first, onScreen.last, showing = visible))
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        // Above its neighbours in case they briefly overlap mid-animation.
                        .zIndex(if (selected) 1f else 0f)
                        .size(ThumbnailSize)
                        .pop { thumbPop.value }
                        .graphicsLayer {
                            translationX = shift.toPx()
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(
                            // Thinner before scaling, so it's 2dp once the thumbnail is enlarged.
                            if (selected) Modifier.border((2f / SelectedThumbnailScale).dp, Color.White) else Modifier,
                        )
                        .clickable(enabled = visible) { latestOnSelect(index) },
                )
            }
        }
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
private val SelectedThumbnailSpacing = 8.dp

/**
 * How far the thumbnails either side of the selection are drawn outward: enough to clear its
 * growth and widen its two gaps from [ThumbnailSpacing] to [SelectedThumbnailSpacing].
 */
private val SelectionShift = (SelectedThumbnailSize - ThumbnailSize) / 2 + (SelectedThumbnailSpacing - ThumbnailSpacing)

/** Leaves room above and below the current thumbnail for its grow animation to overshoot. */
private val CarouselHeight = 60.dp

@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

private const val DOUBLE_TAP_ZOOM = 2f

@Composable
private fun ZoomableImage(photo: SyncedPhoto, zoom: ZoomState, onTap: () -> Unit) {
    // The tap detector below is set up once per photo, so it reads the latest callback through this.
    val latestOnTap by rememberUpdatedState(onTap)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // The thumbnail/carousel rows load the same URI at a tiny size, and Coil's memory cache is
    // keyed by data + size by default -- without a size override here, this request can be
    // satisfied by whichever request to this URI the cache saw first, which upscales a tiny
    // cached bitmap instead of decoding the photo at full resolution.
    val fullSizeRequest = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .size(Size.ORIGINAL)
            .memoryCacheKey("${photo.uri}-full")
            .build()
    }

    AsyncImage(
        model = fullSizeRequest,
        contentDescription = photo.filename,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.uri) {
                detectTapGestures(onTap = { latestOnTap() }, onDoubleTap = { tap ->
                    // Toggle between 1x and 200%. Zooming in keeps the tapped spot under the
                    // finger: with the default center transform origin, a point p lands at
                    // center + (p - center) * scale + offset, so offset = (center - p) * (scale - 1).
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val fromScale = zoom.scale
                    val fromOffset = zoom.offset
                    val zoomingIn = zoom.scale <= 1f
                    val toScale = if (zoomingIn) DOUBLE_TAP_ZOOM else 1f
                    val toOffset = if (zoomingIn) (center - tap) * (DOUBLE_TAP_ZOOM - 1f) else Offset.Zero
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
                            val newScale = (zoom.scale * event.calculateZoom()).coerceIn(1f, 5f)
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
