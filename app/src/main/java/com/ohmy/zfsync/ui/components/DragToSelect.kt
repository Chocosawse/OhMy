package com.ohmy.zfsync.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Google Photos-style range selection on a LazyColumn: long-press an item, then drag without
 * lifting to select (or, if the pressed item was already selected, deselect) every item between
 * it and the one under the finger. Dragging back shrinks the range and restores items outside it
 * to how they were before the drag, and the list auto-scrolls while the finger is near its top or
 * bottom edge. Each gesture is applied on top of the selection as it stood when it began, so
 * ranges from separate long-presses accumulate.
 *
 * [selectableKeys] must return the LazyColumn item keys of selectable items in on-screen order;
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
): Modifier = pointerInput(listState) {
    coroutineScope {
        val edgePx = 64.dp.toPx()
        val maxScrollPerFramePx = 24.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            fun keyAt(y: Float): K? {
                val hit = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { y.toInt() in it.offset until it.offset + it.size }
                    ?.key ?: return null
                return selectableKeys().firstOrNull { it == hit }
            }

            val anchor = keyAt(longPress.position.y) ?: return@awaitEachGesture
            val base = selection()
            val adding = anchor !in base
            var current: K? = null
            var pointerY = longPress.position.y

            fun extendTo(y: Float) {
                val key = keyAt(y) ?: return
                if (key == current) return
                val keys = selectableKeys()
                val from = keys.indexOf(anchor)
                val to = keys.indexOf(key)
                if (from < 0 || to < 0) return
                val range = keys.subList(minOf(from, to), maxOf(from, to) + 1)
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
            extendTo(pointerY)

            var autoScroll: Job? = null
            try {
                while (true) {
                    // Initial pass so this sees (and consumes) moves before the LazyColumn's own
                    // scroll handling and the rows' click handling: the drag mustn't also scroll
                    // the list by hand, and lifting the finger mustn't also register a tap.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    pointerY = change.position.y
                    extendTo(pointerY)
                    if (scrollSpeed(pointerY) != 0f && autoScroll?.isActive != true) {
                        autoScroll = launch {
                            while (isActive) {
                                val speed = scrollSpeed(pointerY)
                                if (speed == 0f) break
                                withFrameNanos { }
                                listState.scrollBy(speed)
                                // Rows slide under a stationary finger while scrolling.
                                extendTo(pointerY)
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
