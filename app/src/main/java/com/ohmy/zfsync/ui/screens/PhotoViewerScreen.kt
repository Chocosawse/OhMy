package com.ohmy.zfsync.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

/**
 * Full-screen, swipeable viewer over the photos this app has downloaded -- built in-app rather
 * than delegated to whatever gallery app the device has, since that turned out to vary wildly in
 * practice (neither ACTION_VIEW nor ACTION_REVIEW produced a cross-photo carousel on two real
 * gallery apps tested). [initialPhotos] is a snapshot taken when the viewer opens, kept as local
 * state from then on: a new auto-synced photo arriving mid-view won't shift pages out from under
 * the pager, but a delete performed here updates immediately.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    initialPhotos: List<SyncedPhoto>,
    initialIndex: Int,
    onDelete: (SyncedPhoto) -> Unit,
    onClose: () -> Unit,
) {
    val photos = remember { initialPhotos.toMutableStateList() }
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
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

    // A photo swiped away from resets to 1x, so coming back to it starts fully zoomed out.
    LaunchedEffect(pagerState.settledPage) {
        val current = photos.getOrNull(pagerState.settledPage)?.uri
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
                currentIndex = pagerState.currentPage,
                visible = currentZoomedOut,
                onThumbnailClick = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
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
                    photos.remove(photo)
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
 * Thumbnail strip. Toggling [visible] pops the on-screen thumbnails in from both ends toward the
 * middle, or out from the middle toward both ends. It keeps its space while hidden, so the pill
 * below doesn't jump, but stops taking touches.
 */
@Composable
private fun CarouselIndicator(
    photos: List<SyncedPhoto>,
    currentIndex: Int,
    visible: Boolean,
    onThumbnailClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        // Glide the current thumbnail to the centre; if it's off screen, jump near it first.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == currentIndex }) {
            listState.scrollToItem(currentIndex)
            withFrameNanos { }
        }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return@LaunchedEffect
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        userScrollEnabled = visible,
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
            val selected = index == currentIndex
            val size by animateDpAsState(if (selected) 48.dp else 40.dp, overshootSpec(), label = "thumbSize")
            val thumbPop = animatePop(visible, cascadeDelayMillis(index, onScreen.first, onScreen.last, showing = visible))
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
                    .clickable(enabled = visible) { onThumbnailClick(index) },
            )
        }
    }
}

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
