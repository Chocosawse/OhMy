package com.ohmy.zfsync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.ohmy.zfsync.model.SyncedPhoto
import com.ohmy.zfsync.ui.components.ActionPill
import com.ohmy.zfsync.ui.components.sharePhotos
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

    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    // This screen is swapped in by Compose state, not a back-stack entry, so without this the
    // system back gesture has nothing of ours to intercept and falls through to the Activity,
    // closing the whole app instead of returning to the sync screen.
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].uri },
        ) { page ->
            ZoomableImage(photos[page])
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CarouselIndicator(
                photos = photos,
                currentIndex = pagerState.currentPage,
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

@Composable
private fun CarouselIndicator(
    photos: List<SyncedPhoto>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) { listState.animateScrollToItem(currentIndex) }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
            val selected = index == currentIndex
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (selected) 48.dp else 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onThumbnailClick(index) },
            )
        }
    }
}

@Composable
private fun ZoomableImage(photo: SyncedPhoto) {
    var scale by remember(photo.uri) { mutableStateOf(1f) }
    var offset by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

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
                // Unlike detectTransformGestures, only consume what this image actually uses: a
                // one-finger drag at 1x is left unconsumed so the parent HorizontalPager can turn
                // it into a page swipe, while pinches (and pans once zoomed in) stay here.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
    )
}
