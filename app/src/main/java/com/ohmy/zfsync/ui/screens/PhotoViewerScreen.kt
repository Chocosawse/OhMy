package com.ohmy.zfsync.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen, swipeable viewer over the photos this app has downloaded -- built in-app rather
 * than delegated to whatever gallery app the device has, since that turned out to vary wildly in
 * practice (neither ACTION_VIEW nor ACTION_REVIEW produced a cross-photo carousel on two real
 * gallery apps tested). It follows the live album: photos synced while it's open appear at the
 * front, where the newest go, without moving the photo being viewed (pages are keyed by photo),
 * and the carousel shows a placeholder for each of the [inFlightCount] downloads still running.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].uri },
        ) { page ->
            ZoomableImage(photos[page], zoomStateFor(photos[page]))
        }

        IconButton(
            onClick = onClose,
            // The photo itself runs edge to edge behind the system bars; controls stay clear of them.
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CarouselIndicator(
                photos = photos,
                inFlightCount = inFlightCount,
                currentIndex = pagerState.currentPage,
                visible = currentZoomedOut,
                onThumbnailClick = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                // Instant, not animated: scrubbing changes photo many times a second.
                onScrubTo = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActionPill(
                onShare = { sharePhotos(context, listOf(photos[pagerState.currentPage])) },
                onDelete = { pendingDelete = photos[pagerState.currentPage] },
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Thumbnail strip: a placeholder per in-flight download at the front (where new photos land),
 * then a thumbnail per photo, in page order. Toggling [visible] pops the on-screen items in from
 * both ends toward the middle, or out from the middle toward both ends. It keeps its space while
 * hidden, so the pill below doesn't jump, but stops taking touches.
 *
 * Placeholders shift the strip's item positions relative to page numbers, so photos are found by
 * key (their URI) rather than by position.
 */
@Composable
private fun CarouselIndicator(
    photos: List<SyncedPhoto>,
    inFlightCount: Int,
    currentIndex: Int,
    visible: Boolean,
    onThumbnailClick: (Int) -> Unit,
    onScrubTo: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var scrubbing by remember { mutableStateOf(false) }
    val latestPhotos by rememberUpdatedState(photos)

    LaunchedEffect(currentIndex, inFlightCount, scrubbing) {
        // Mid-scrub the strip must stay put under the finger; it recentres once the finger lifts.
        if (scrubbing) return@LaunchedEffect
        val currentKey = latestPhotos.getOrNull(currentIndex)?.uri ?: return@LaunchedEffect
        // Glide the current thumbnail to the centre; if it's off screen, jump near it first.
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

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(CarouselHeight)
            .carouselScrub(
                listState = listState,
                enabled = visible,
                haptics = haptics,
                onScrubbingChange = { scrubbing = it },
                pageForKey = { key -> latestPhotos.indexOfFirst { it.uri == key }.takeIf { it >= 0 } },
                onScrubTo = onScrubTo,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Dragging scrubs (carouselScrub) rather than scrolling; the strip scrolls itself at the edges.
        userScrollEnabled = false,
    ) {
        // Cascade delays go by position in the strip, placeholders included.
        items(inFlightCount, key = { "placeholder-$it" }) { index ->
            val placeholderPop = animatePop(visible, cascadeDelayMillis(index, onScreen.first, onScreen.last, showing = visible))
            PlaceholderThumbnail(Modifier.pop { placeholderPop.value })
        }
        itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
            val selected = index == currentIndex
            val size by animateDpAsState(if (selected) SelectedThumbnailSize else ThumbnailSize, overshootSpec(), label = "thumbSize")
            val position = inFlightCount + index
            val thumbPop = animatePop(visible, cascadeDelayMillis(position, onScreen.first, onScreen.last, showing = visible))
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .pop { thumbPop.value }
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color.White)
                        } else {
                            Modifier
                        },
                    )
                    // Touches are handled by carouselScrub; this keeps a tap action for accessibility.
                    .then(if (visible) Modifier.semantics { onClick { onThumbnailClick(index); true } } else Modifier),
            )
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

/**
 * Scrubbing: touching the strip jumps to the thumbnail under the finger, and dragging keeps
 * following it, with a haptic tick per photo. While the finger is near either end the strip
 * auto-scrolls (faster the closer it is), so the whole album can be scrubbed in one drag.
 */
private fun Modifier.carouselScrub(
    listState: LazyListState,
    enabled: Boolean,
    haptics: HapticFeedback,
    onScrubbingChange: (Boolean) -> Unit,
    /** The page for a strip item's key, or null for non-photo items (download placeholders). */
    pageForKey: (Any) -> Int?,
    onScrubTo: (Int) -> Unit,
): Modifier = pointerInput(listState, enabled) {
    if (!enabled) return@pointerInput
    coroutineScope {
        val edgePx = 48.dp.toPx()
        val maxScrollPerFramePx = 20.dp.toPx()

        awaitEachGesture {
            // Initial pass, consumed throughout: the thumbnails and the photo pager underneath
            // mustn't also react to the same touch.
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            down.consume()
            var pointerX = down.position.x
            var current: Int? = null

            // The photo whose thumbnail centre is nearest the finger, so the gaps between thumbnails,
            // the strip's end padding and download placeholders still pick a photo.
            fun nearestPage(x: Float): Int? {
                val info = listState.layoutInfo
                // Item offsets start after the leading content padding; viewportStartOffset is minus that.
                val contentX = x + info.viewportStartOffset
                return info.visibleItemsInfo
                    .mapNotNull { item -> pageForKey(item.key)?.let { page -> page to abs(item.offset + item.size / 2f - contentX) } }
                    .minByOrNull { it.second }
                    ?.first
            }

            fun followFinger() {
                val index = nearestPage(pointerX) ?: return
                if (index == current) return
                if (current != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                current = index
                onScrubTo(index)
            }

            fun scrollSpeed(x: Float): Float = when {
                x < edgePx -> -maxScrollPerFramePx * (edgePx - x) / edgePx
                x > size.width - edgePx -> maxScrollPerFramePx * (x - (size.width - edgePx)) / edgePx
                else -> 0f
            }

            onScrubbingChange(true)
            followFinger()
            var autoScroll: Job? = null
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    pointerX = change.position.x
                    followFinger()
                    if (scrollSpeed(pointerX) != 0f && autoScroll?.isActive != true) {
                        autoScroll = launch {
                            while (isActive) {
                                val speed = scrollSpeed(pointerX)
                                if (speed == 0f) break
                                withFrameNanos { }
                                listState.scrollBy(speed)
                                // Thumbnails slide under a still finger while the strip scrolls.
                                followFinger()
                            }
                        }
                    }
                }
            } finally {
                autoScroll?.cancel()
                onScrubbingChange(false)
            }
        }
    }
}

private val ThumbnailSize = 40.dp

private val SelectedThumbnailSize = 56.dp

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
private fun ZoomableImage(photo: SyncedPhoto, zoom: ZoomState) {
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
                detectTapGestures(onDoubleTap = { tap ->
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
