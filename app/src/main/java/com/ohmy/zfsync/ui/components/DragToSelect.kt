package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.ui.home.GridCell
import com.ohmy.zfsync.ui.home.keysInRectangle
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Google Photos-style range selection on a LazyColumn or LazyVerticalGrid: long-press an item, then drag without
 * lifting to select (or, if the pressed item was already selected, deselect) every item between
 * it and the one under the finger. Dragging back shrinks the range and restores items outside it
 * to how they were before the drag, and the list auto-scrolls while the finger is near its top or
 * bottom edge. Each gesture is applied on top of the selection as it stood when it began, so
 * ranges from separate long-presses accumulate. With [onDragOut], a long-press on a selected item
 * may instead drag the selection out of the app.
 *
 * [selectableKeys] must return the lazy item keys of selectable items in on-screen order;
 * pressing anything else (headers, placeholders) falls through as a normal long press. The
 * gesture is set up once and outlives recompositions, so the lambdas must read current state
 * (e.g. via rememberUpdatedState) rather than capture plain values.
 */
fun <K : Any> Modifier.dragToSelect(
    listState: LazyListState,
    selectableKeys: () -> List<K>,
    selection: () -> Set<K>,
    onSelectionChange: (Set<K>) -> Unit,
    haptics: HapticFeedback,
    onDragOut: ((K) -> Boolean)? = null,
): Modifier = dragToSelect(
    scrollState = listState,
    keyAt = { position ->
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { position.y.toInt() in it.offset until it.offset + it.size }
            ?.key
    },
    selectableKeys = selectableKeys,
    selection = selection,
    onSelectionChange = onSelectionChange,
    haptics = haptics,
    onDragOut = onDragOut,
)

/**
 * [dragToSelect] on a LazyVerticalGrid, selecting a rectangle rather than a run: the photo first
 * pressed and the one under the finger are its opposite corners, and every photo in the lines
 * and columns between them is in it. [cellOf] gives a selectable key's line and column.
 * [startPaddingPx] is the grid's start content padding, allowed for whether or not item positions
 * include it.
 */
fun <K : Any> Modifier.dragToSelect(
    gridState: LazyGridState,
    startPaddingPx: Int,
    selectableKeys: () -> List<K>,
    cellOf: () -> Map<K, GridCell>,
    selection: () -> Set<K>,
    onSelectionChange: (Set<K>) -> Unit,
    haptics: HapticFeedback,
    onDragOut: ((K) -> Boolean)? = null,
): Modifier = dragToSelect(
    scrollState = gridState,
    keyAt = keyAt@{ position ->
        val items = gridState.layoutInfo.visibleItemsInfo
        val y = position.y.toInt()
        if (items.isEmpty()) return@keyAt null
        // Whether item positions count the start padding: if so, the leftmost item sits at it.
        val countsPadding = items.minOf { it.offset.x } >= startPaddingPx / 2
        val x = position.x.toInt() - if (countsPadding) 0 else startPaddingPx
        val row = items.filter { y in it.offset.y until it.offset.y + it.size.height }
        // In the row under the finger, the cell under it; in a gap between cells (or the side
        // margins), the nearest one, so the selection doesn't flicker as the finger crosses a gap.
        row.firstOrNull { x in it.offset.x until it.offset.x + it.size.width }?.key
            ?: row.minByOrNull { minOf(abs(x - it.offset.x), abs(x - (it.offset.x + it.size.width))) }?.key
    },
    selectableKeys = selectableKeys,
    range = { anchor, to, _ ->
        val cells = cellOf()
        val from = cells[anchor]
        val corner = cells[to]
        if (from == null || corner == null) listOf(anchor) else keysInRectangle(cells, from, corner)
    },
    selection = selection,
    onSelectionChange = onSelectionChange,
    haptics = haptics,
    onDragOut = onDragOut,
)

/** The gesture itself, for any lazy container: [keyAt] finds the item key at a position in it. */
private fun <K : Any> Modifier.dragToSelect(
    scrollState: ScrollableState,
    keyAt: (Offset) -> Any?,
    selectableKeys: () -> List<K>,
    selection: () -> Set<K>,
    /** What's selected between the pressed item and the one under the finger; by default the run of [selectableKeys] between them. */
    range: (anchor: K, to: K, keys: List<K>) -> Collection<K> = { anchor, to, keys ->
        val from = keys.indexOf(anchor)
        val until = keys.indexOf(to)
        if (from < 0 || until < 0) emptyList() else keys.subList(minOf(from, until), maxOf(from, until) + 1)
    },
    onSelectionChange: (Set<K>) -> Unit,
    haptics: HapticFeedback,
    /**
     * Offered a long-press on an already-selected item first: returns true if it took the press
     * (dragging the selection out to another app), and then no range is selected.
     */
    onDragOut: ((K) -> Boolean)? = null,
): Modifier = pointerInput(scrollState) {
    coroutineScope {
        val edgePx = 64.dp.toPx()
        val maxScrollPerFramePx = 24.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            fun selectableAt(position: Offset): K? {
                val hit = keyAt(position) ?: return null
                return selectableKeys().firstOrNull { it == hit }
            }

            val anchor = selectableAt(longPress.position) ?: return@awaitEachGesture
            val base = selection()
            if (anchor in base && onDragOut?.invoke(anchor) == true) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // The system takes the touch over once its drag starts. Until then, keep the
                // finger's moves from scrolling the list or tapping a photo.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == longPress.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                return@awaitEachGesture
            }
            val adding = anchor !in base
            var current: K? = null
            var pointer = longPress.position

            fun extendTo(position: Offset) {
                val key = selectableAt(position) ?: return
                if (key == current) return
                val range = range(anchor, key, selectableKeys())
                if (range.isEmpty()) return
                if (current != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                current = key
                onSelectionChange(if (adding) base + range else base - range)
            }

            fun scrollSpeed(y: Float): Float = when {
                y < edgePx -> -maxScrollPerFramePx * (edgePx - y) / edgePx
                y > size.height - edgePx -> maxScrollPerFramePx * (y - (size.height - edgePx)) / edgePx
                else -> 0f
            }

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            extendTo(pointer)

            var autoScroll: Job? = null
            try {
                while (true) {
                    // Initial pass so this sees (and consumes) moves before the container's own
                    // scroll handling and the items' click handling: the drag mustn't also scroll
                    // the list by hand, and lifting the finger mustn't also register a tap.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    pointer = change.position
                    extendTo(pointer)
                    if (scrollSpeed(pointer.y) != 0f && autoScroll?.isActive != true) {
                        autoScroll = launch {
                            while (isActive) {
                                val speed = scrollSpeed(pointer.y)
                                if (speed == 0f) break
                                withFrameNanos { }
                                scrollState.scrollBy(speed)
                                // Items slide under a stationary finger while scrolling.
                                extendTo(pointer)
                            }
                        }
                    }
                }
            } finally {
                autoScroll?.cancel()
            }
        }
    }
}
