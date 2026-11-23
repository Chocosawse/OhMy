package com.ohmy.zfsync.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.ui.theme.AppTypography
import com.ohmy.zfsync.ui.theme.LocalHomeColors
import com.ohmy.zfsync.ui.theme.animationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** What the scrubber reads and drives: a lazy list's or grid's position, by item. */
@Stable
internal interface ScrubTarget {
    val totalItems: Int

    /** How many items fit on screen, roughly: the last index worth scrolling to is [totalItems] less this. */
    val visibleItems: Int

    /** How far through the content the top of the screen is, by item, including the part of the top item scrolled past. */
    val position: Float

    val isScrollInProgress: Boolean

    suspend fun scrollToItem(index: Int)
}

internal fun LazyListState.asScrubTarget(): ScrubTarget = object : ScrubTarget {
    override val totalItems get() = layoutInfo.totalItemsCount
    override val visibleItems get() = layoutInfo.visibleItemsInfo.size
    override val position: Float
        get() {
            val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            return firstVisibleItemIndex + firstVisibleItemScrollOffset / first.size.coerceAtLeast(1).toFloat()
        }
    override val isScrollInProgress get() = this@asScrubTarget.isScrollInProgress
    override suspend fun scrollToItem(index: Int) = this@asScrubTarget.scrollToItem(index)
}

internal fun LazyGridState.asScrubTarget(): ScrubTarget = object : ScrubTarget {
    override val totalItems get() = layoutInfo.totalItemsCount
    override val visibleItems get() = layoutInfo.visibleItemsInfo.size
    override val position: Float
        get() {
            val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            return firstVisibleItemIndex + firstVisibleItemScrollOffset / first.size.height.coerceAtLeast(1).toFloat()
        }
    override val isScrollInProgress get() = this@asScrubTarget.isScrollInProgress
    override suspend fun scrollToItem(index: Int) = this@asScrubTarget.scrollToItem(index)
}

/** A section the scrubber marks on its track and names while dragged: its heading's item index, and its title. */
internal data class ScrubSection(val index: Int, val title: String)

/** The handle: a narrow upright rectangle, growing a little while it's held. */
private val HandleWidth = 8.dp
private val HandleHeight = 40.dp
private const val HANDLE_HELD_SCALE = 1.25f

/** The touch area around the handle, much bigger than it looks. */
private val HandleTouchWidth = 40.dp
private val HandleTouchHeight = 64.dp

/** From the screen's edge to the track's line. */
private val TrackInset = 16.dp

/** The track's line. */
private val TrackWidth = 2.dp

/** A section's mark: a dot on the track. */
private val SectionDotSize = 8.dp

/** The current section's name sits this far from the handle, on the content side. */
private val LabelGap = 12.dp

/** How long the scrubber stays after scrolling stops, and how long it takes to fade. */
private const val LINGER_MILLIS = 1500L
private const val FADE_MILLIS = 200

/** Worth scrubbing: more than this many screens of content. */
private const val MIN_SCREENS = 3

/**
 * A fast scroller along the right edge, Material You's pattern set editorially: a 2dp track,
 * an 8dp rectangular handle in the accent color that grows a little while dragged, a dot for
 * each of [sections], and while dragging the current section's title beside the handle. It
 * shows while the content scrolls and fades out shortly after; only for content several screens
 * long. Fills the space it's given, the track running its full height.
 */
@Composable
internal fun FastScrubber(target: ScrubTarget, sections: List<ScrubSection>, modifier: Modifier = Modifier) {
    val colors = LocalHomeColors.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animate = animationsEnabled()
    val latestSections by rememberUpdatedState(sections)

    var dragging by remember { mutableStateOf(false) }
    // The handle's place while dragged: under the finger, not where the content has got to.
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var requestedIndex by remember { mutableIntStateOf(-1) }

    var recentlyScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        snapshotFlow { target.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                recentlyScrolled = true
            } else {
                delay(LINGER_MILLIS)
                recentlyScrolled = false
            }
        }
    }
    val worthIt by remember(target) {
        derivedStateOf { target.visibleItems > 0 && target.totalItems > target.visibleItems * MIN_SCREENS }
    }
    val shown = worthIt && (dragging || recentlyScrolled)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (animate) FADE_MILLIS else 0), label = "scrubberAlpha")
    val held by animateFloatAsState(
        if (dragging) HANDLE_HELD_SCALE else 1f,
        tween(if (animate) 150 else 0, easing = FastOutSlowInEasing),
        label = "scrubberHeld",
    )

    /** The last index worth scrolling to: further, and the screen can't show it at the top. */
    fun lastIndex() = (target.totalItems - target.visibleItems).coerceAtLeast(1)

    // Follows the finger: scrolls to the item for the latest place, skipping any it's passed.
    LaunchedEffect(target) {
        snapshotFlow { requestedIndex }.collectLatest { index -> if (index >= 0) target.scrollToItem(index) }
    }

    // The section the handle is in, while dragged: named beside it, with a tick on entering one.
    val currentSection by remember(target) {
        derivedStateOf {
            if (!dragging) return@derivedStateOf null
            val index = (dragFraction * lastIndex()).roundToInt()
            latestSections.lastOrNull { it.index <= index } ?: latestSections.firstOrNull()
        }
    }
    LaunchedEffect(currentSection?.index) { if (dragging && currentSection != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    if (alpha <= 0f && !shown) return

    BoxWithConstraints(modifier = modifier.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics { }) {
        val handleHeightPx = with(density) { HandleHeight.toPx() }
        val travelPx = (constraints.maxHeight - handleHeightPx).coerceAtLeast(1f)
        val trackX = constraints.maxWidth - with(density) { TrackInset.toPx() }
        val fraction by remember(target) {
            derivedStateOf { if (dragging) dragFraction else (target.position / lastIndex()).coerceIn(0f, 1f) }
        }

        // The track and the sections' dots.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val line = TrackWidth.toPx()
            drawRect(colors.ink.copy(alpha = 0.25f), topLeft = Offset(trackX - line / 2, 0f), size = Size(line, size.height))
            val dot = SectionDotSize.toPx()
            val last = lastIndex().toFloat()
            for (section in latestSections) {
                val y = (section.index / last).coerceIn(0f, 1f) * travelPx + handleHeightPx / 2
                // A dot on the track, in ink.
                drawCircle(colors.ink, radius = dot / 2, center = Offset(trackX, y))
            }
        }

        // The handle, in a much larger touch area; dragging it scrolls the content. The finger is
        // followed in screen terms: the area moves with it, so its own coordinates would cancel
        // out the very motion they measure.
        val latestFraction by rememberUpdatedState(fraction)
        val latestTravel by rememberUpdatedState(travelPx)
        val touchCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Box(
            modifier = Modifier
                .offset {
                    val touchW = HandleTouchWidth.roundToPx()
                    val touchH = HandleTouchHeight.roundToPx()
                    IntOffset(
                        (trackX - touchW / 2f).roundToInt(),
                        (fraction * travelPx + handleHeightPx / 2 - touchH / 2f).roundToInt(),
                    )
                }
                .size(HandleTouchWidth, HandleTouchHeight)
                .onPlaced { touchCoordinates[0] = it }
                .pointerInput(target) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val coordinates = touchCoordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                        down.consume()
                        val startY = coordinates.localToRoot(down.position).y
                        val startFraction = latestFraction
                        dragFraction = startFraction
                        dragging = true
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val y = (touchCoordinates[0]?.takeIf { it.isAttached } ?: break).localToRoot(change.position).y
                                dragFraction = (startFraction + (y - startY) / latestTravel).coerceIn(0f, 1f)
                                requestedIndex = (dragFraction * lastIndex()).roundToInt()
                            }
                        } finally {
                            dragging = false
                            requestedIndex = -1
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(HandleWidth, HandleHeight)
                    .graphicsLayer {
                        scaleX = held
                        scaleY = held
                    }
                    .background(colors.accent),
            )
        }

        // The section being scrubbed through, named beside the handle.
        currentSection?.let { section ->
            Text(
                section.title,
                style = AppTypography.titleMedium,
                color = colors.background,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val right = trackX - (HandleWidth / 2 + LabelGap).toPx()
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = right.roundToInt().coerceAtLeast(0)))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val centerY = fraction * travelPx + handleHeightPx / 2
                            placeable.place((right - placeable.width).roundToInt(), (centerY - placeable.height / 2f).roundToInt())
                        }
                    }
                    .background(colors.ink)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
