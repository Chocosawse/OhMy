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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ohmy.zfsync.ui.theme.MOTION_DURATION_MILLIS
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
    carouselStyle: CarouselStyle = CarouselStyle.Classic,
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

    // Height of the carousel up from the bottom of the screen (with its margin and the nav bar
    // inset). At 1x the photo is kept clear of it, so it never covers the photo; the action pill
    // above it may still sit over the photo. Hiding the controls only scales them in the draw
    // layer, so this doesn't change as they hide and show, and the photo doesn't jump.
    var controlsHeightPx by remember { mutableIntStateOf(0) }
    var pillHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].uri },
        ) { page ->
            ZoomableImage(
                photos[page],
                zoomStateFor(photos[page]),
                bottomReserved = with(density) { controlsHeightPx.toDp() - pillHeightPx.toDp() } - PillCarouselSpacing,
                onTap = { controlsVisible = !controlsVisible },
            )
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
            // Measured outside the insets and margin, so the reserved space includes them; the pill
            // and the space under it are then taken off, leaving the carousel's share.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { controlsHeightPx = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionPill(
                // Shown and hidden with the other controls.
                visible = controlsVisible,
                onShare = { sharePhotos(context, listOf(photos[pagerState.currentPage])) },
                onDelete = { pendingDelete = photos[pagerState.currentPage] },
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                modifier = Modifier.onSizeChanged { pillHeightPx = it.height },
            )
            Spacer(modifier = Modifier.height(PillCarouselSpacing))
            CarouselIndicator(
                photos = photos,
                inFlightCount = inFlightCount,
                currentIndex = pagerState.currentPage,
                visible = controlsVisible,
                style = carouselStyle,
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

/** The gallery carousel's look: v1 plain thumbnails, or v2 frames on a strip of film. */
enum class CarouselStyle { Classic, FilmStrip }

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
 * [CarouselStyle.FilmStrip] (v2) draws the same strip as film: a dark base, each frame's slot
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
    val film = style == CarouselStyle.FilmStrip
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
            .height(if (film) FilmStripHeight else CarouselHeight)
            .then(
                if (film) {
                    Modifier
                        .graphicsLayer {
                            val p = stripPop.value
                            scaleY = p
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
                    selectionGrowth = { (scale - 1f) / (SelectedThumbnailScale - 1f) },
                    leadsStrip = inFlightCount == 0 && index == 0,
                    endsStrip = index == photos.lastIndex,
                    // Above its neighbours in case they briefly overlap mid-animation.
                    modifier = Modifier.zIndex(if (selected) 1f else 0f),
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ThumbnailSize)
                            .pop { thumbPop.value }
                            .graphicsLayer {
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
    film: Boolean,
    frameNumber: Int?,
    textMeasurer: TextMeasurer,
    shift: () -> Dp,
    modifier: Modifier = Modifier,
    selectionGrowth: () -> Float = { 0f },
    leadsStrip: Boolean = false,
    endsStrip: Boolean = false,
    content: @Composable () -> Unit,
) {
    val slotModifier = if (film) {
        val edgeLabel = remember(frameNumber, textMeasurer) {
            frameNumber?.let { textMeasurer.measure(it.toString().padStart(2, '0'), FilmEdgeTextStyle) }
        }
        Modifier
            .width(ThumbnailSize)
            .fillMaxHeight()
            .drawBehind {
                drawFilmPiece(
                    edgeLabel = edgeLabel,
                    widen = SelectionShift.toPx() * selectionGrowth(),
                    lead = if (leadsStrip) FilmLeader.toPx() else 0f,
                    tail = if (endsStrip) FilmLeader.toPx() else 0f,
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
private fun DrawScope.drawFilmPiece(edgeLabel: TextLayoutResult?, widen: Float, lead: Float, tail: Float) {
    val halfGap = ThumbnailSpacing.toPx() / 2
    val left = -halfGap - widen - lead
    val right = size.width + halfGap + widen + tail
    drawRect(FilmBase, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(right - left, size.height))

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
private val FilmBase = Color(0xFF111111)
private val SprocketRim = Color.White.copy(alpha = 0.35f)
private val SprocketRimWidth = 0.75.dp
private val FilmEdgeTextStyle = TextStyle(
    color = Color(0xCCE0A84A),
    fontSize = 7.sp,
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.5.sp,
)

@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

private val PillCarouselSpacing = 16.dp

/** Double-tap zooms to at least this; more if that's what it takes for the photo to fill the screen. */
private const val DOUBLE_TAP_ZOOM = 2f

/** Pinch limit; raised to the fill scale for photos that need more than this to fill the screen. */
private const val MAX_ZOOM = 5f

/**
 * A photo that, at 1x, is centred on the screen unless the bottom controls ([bottomReserved])
 * would cover it; then it's raised, and if need be shrunk, to sit just above them. Gestures work anywhere on the screen, and once
 * zoomed the photo can spread over the whole screen, including the space the controls leave when
 * they hide.
 */
@Composable
private fun ZoomableImage(photo: SyncedPhoto, zoom: ZoomState, bottomReserved: Dp, onTap: () -> Unit) {
    // The gesture detectors below are set up once per photo, so they read the latest values through these.
    val latestOnTap by rememberUpdatedState(onTap)
    val latestBottomReserved by rememberUpdatedState(bottomReserved)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // The decoded photo's size, known once it loads; needed to work out the fill-the-screen scale.
    var imageSize by remember(photo.uri) { mutableStateOf(GeometrySize.Unspecified) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.uri) {
                detectTapGestures(onTap = { latestOnTap() }, onDoubleTap = { tap ->
                    val geometry = ZoomGeometry(size.width.toFloat(), size.height.toFloat(), latestBottomReserved.toPx(), imageSize)
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
                            val maxScale = maxOf(MAX_ZOOM, ZoomGeometry(size.width.toFloat(), size.height.toFloat(), latestBottomReserved.toPx(), imageSize).fillScale)
                            val newScale = (zoom.scale * event.calculateZoom()).coerceIn(1f, maxScale)
                            zoom.scale = newScale
                            zoom.offset = if (newScale <= 1f) Offset.Zero else zoom.offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = fullSizeRequest,
            contentDescription = photo.filename,
            contentScale = ContentScale.Fit,
            onSuccess = { imageSize = it.painter.intrinsicSize },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomReserved)
                // Fitted into the area above the controls, then moved down within the screen to
                // centre it where there's room (see ZoomGeometry). Unclipped, so a zoomed photo
                // spreads past that area over the rest of the screen.
                .graphicsLayer {
                    val reserved = bottomReserved.toPx()
                    val geometry = ZoomGeometry(size.width, size.height + reserved, reserved, imageSize)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = geometry.restingShift + zoom.offset.y
                },
        )
    }
}

/**
 * Where the photo sits, in the screen's pixels. At 1x it's fitted (ContentScale.Fit) into the
 * area above [bottomReserved], then centred on the screen -- or as near the centre as it can be
 * without its bottom edge going below that area. A zoom scales it about its 1x centre, then
 * translates it by an offset.
 */
private class ZoomGeometry(
    private val screenWidth: Float,
    private val screenHeight: Float,
    bottomReserved: Float,
    imageSize: GeometrySize,
) {
    private val areaHeight = (screenHeight - bottomReserved).coerceAtLeast(1f)

    /** The photo's size on screen at 1x, or null until it has loaded. */
    private val fitted: GeometrySize? = imageSize
        .takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
        ?.let {
            val fit = minOf(screenWidth / it.width, areaHeight / it.height)
            GeometrySize(it.width * fit, it.height * fit)
        }

    /** The photo's centre at 1x. Until it loads, the area's centre. */
    private val photoCenter = Offset(
        screenWidth / 2f,
        fitted?.let { minOf(screenHeight / 2f, areaHeight - it.height / 2f) } ?: (areaHeight / 2f),
    )

    /** How far down the photo is moved from the centre of the area it's fitted into, at 1x. */
    val restingShift: Float = photoCenter.y - areaHeight / 2f

    /** The scale at which the photo covers the whole screen, with no black bars on any side. */
    val fillScale: Float = fitted?.let { maxOf(screenWidth / it.width, screenHeight / it.height) } ?: 1f

    /**
     * The offset that, at [scale], keeps [tap] under the finger: a point p lands at
     * center + (p - center) * scale + offset, so offset = (center - p) * (scale - 1). Then nudged
     * just enough that the photo still covers the screen on each axis it's big enough to, and
     * centred on the screen on any axis it isn't.
     */
    fun offsetFor(tap: Offset, scale: Float): Offset {
        val keepTap = (photoCenter - tap) * (scale - 1f)
        val size = fitted ?: return keepTap
        return Offset(
            cover(keepTap.x, photoCenter.x, size.width * scale, screenWidth),
            cover(keepTap.y, photoCenter.y, size.height * scale, screenHeight),
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
