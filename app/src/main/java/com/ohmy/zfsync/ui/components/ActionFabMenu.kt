package com.ohmy.zfsync.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.ui.theme.MOTION_DURATION_MILLIS
import com.ohmy.zfsync.ui.theme.animatePop
import com.ohmy.zfsync.ui.theme.overshootSpec
import com.ohmy.zfsync.ui.theme.pop

/** One entry of an [ActionFabMenu]. */
@Immutable
data class FabAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** How much the FAB grows while pressed or open. */
private const val FAB_EXPANDED_SCALE = 1.1f

/** How much an action grows while the finger is over it. */
private const val ACTION_HOVER_SCALE = 1.08f

/** Stagger between actions popping in (nearest the FAB first) or out (farthest first). */
private const val ACTION_CASCADE_STEP_MILLIS = 50

/** Between Material's small and regular FABs: present without outweighing the photo. Matches the exposure card's height, so the two line up. */
internal val FabSize = 48.dp
/**
 * The viewer's translucent dark fill for controls over the photo: the FAB, and the circles behind
 * the close and info buttons. The same as the title and exposure labels' boxes, so the overlay
 * reads as one layer over the photo.
 */
internal val ViewerScrimColor = LightInfoBoxColor

/** Closed: Material's FAB shape, a rounded square (16dp corners). */
private val FabClosedCorner = 16.dp

/** Open: a circle (half the FAB's size). */
private val FabOpenCorner = FabSize / 2

/**
 * A speed dial (Material's "FAB menu"): a FAB that opens a short stack of [actions] above it.
 *
 * - Tap: opens the menu, which stays open; tap an action to run it, or the FAB (or back, or the
 *   caller via [onExpandedChange]) to close it.
 * - Hold and slide (press-drag-release): the menu opens under the finger; sliding onto an action
 *   highlights it, and letting go there runs it. Letting go anywhere else just closes the menu.
 *
 * Subtle haptic ticks as the menu opens and as the finger reaches each action, and a confirm on
 * running one. [actions] are nearest-the-FAB first. Hidden with the rest of the screen's controls
 * via [visible], which also closes it.
 */
@Composable
fun ActionFabMenu(
    actions: List<FabAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    visible: Boolean,
    contentDescription: String,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val latestActions by rememberUpdatedState(actions)
    val latestExpanded by rememberUpdatedState(expanded)
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)

    // Closing with the other controls (e.g. when zooming in hides them).
    LaunchedEffect(visible) { if (!visible) latestOnExpandedChange(false) }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    var pressed by remember { mutableStateOf(false) }
    // The action under the finger during a hold-and-slide, by index into [actions].
    var hovered by remember { mutableStateOf<Int?>(null) }
    // Layout coordinates, read only when the finger moves: the FAB's (its pointer positions are
    // local to it) and each action's (to find the one under the finger).
    val fabCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val actionCoordinates = remember { HashMap<Int, LayoutCoordinates>() }

    // Each action's pop, held here rather than in the action so the actions can leave
    // composition entirely once they've popped out.
    val count = actions.size
    val pops = List(count) { index ->
        val delay = ACTION_CASCADE_STEP_MILLIS * if (expanded) index else count - 1 - index
        animatePop(expanded && visible, delay)
    }
    val showActions by remember(count) { derivedStateOf { latestExpanded || pops.any { it.value > 0f } } }

    val fabPop = animatePop(visible)
    val fabScale by animateFloatAsState(
        if (visible && (expanded || pressed)) FAB_EXPANDED_SCALE else 1f,
        overshootSpec(),
        label = "fabScale",
    )
    val iconRotation by animateFloatAsState(if (expanded) 90f else 0f, overshootSpec(), label = "fabIconRotation")
    // Rounds into a circle as the menu opens and back to a rounded square as it closes: smoothly,
    // without the overshoot (corners past a circle can't be drawn, and short of the square would wobble).
    val corner by animateDpAsState(
        if (expanded && visible) FabOpenCorner else FabClosedCorner,
        tween(MOTION_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "fabCorner",
    )

    /** The action under [rootPosition], if any; generous sideways, so the finger needn't be exact. */
    fun actionAt(rootPosition: Offset): Int? = actionCoordinates.entries.firstOrNull { (_, coordinates) ->
        if (!coordinates.isAttached) return@firstOrNull false
        val bounds = coordinates.boundsInRoot()
        val slopX = 48.dp.value * view.resources.displayMetrics.density
        val slopY = 6.dp.value * view.resources.displayMetrics.density
        rootPosition.x in (bounds.left - slopX)..(bounds.right + slopX) &&
            rootPosition.y in (bounds.top - slopY)..(bounds.bottom + slopY)
    }?.key

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (showActions) {
            // Top to bottom: the farthest from the FAB first.
            for (index in (count - 1) downTo 0) {
                val action = actions[index]
                ActionRow(
                    action = action,
                    hovered = hovered == index,
                    enabled = expanded,
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                    modifier = Modifier
                        .onPlaced { actionCoordinates[index] = it }
                        .pop { pops[index].value },
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        Surface(
            shape = RoundedCornerShape(corner),
            // The small boxes' denser dark (the caption date's): still the photo's overlay rather
            // than a solid block on it, but firm enough to read as a button over a bright photo.
            // No shadow, which would show through the fill.
            color = InfoBoxColor,
            contentColor = Color.White,
            modifier = Modifier
                .pop { fabPop.value }
                .onPlaced { fabCoordinates[0] = it }
                .semantics {
                    role = Role.Button
                    this.contentDescription = if (expanded) closeDescription else contentDescription
                    onClick { onExpandedChange(!expanded); true }
                }
                .pointerInput(visible) {
                    if (!visible) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val wasExpanded = latestExpanded
                        pressed = true
                        if (!wasExpanded) {
                            latestOnExpandedChange(true)
                            view.tick()
                        }
                        var moved = false
                        var lastUptime = down.uptimeMillis
                        var target: Int? = null
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastUptime = change.uptimeMillis
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                val root = fabCoordinates[0]?.takeIf { it.isAttached }?.localToRoot(change.position)
                                val now = root?.let(::actionAt)
                                if (now != target) {
                                    target = now
                                    hovered = now
                                    if (now != null) view.tick()
                                }
                                change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            pressed = false
                            hovered = null
                        }
                        val held = moved || lastUptime - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                        val chosen = target
                        when {
                            chosen != null -> {
                                view.confirm()
                                latestOnExpandedChange(false)
                                latestActions.getOrNull(chosen)?.onClick?.invoke()
                            }
                            // Held, then let go over nothing: changed their mind.
                            held -> latestOnExpandedChange(false)
                            // A tap on the open FAB closes it; a tap on the closed one leaves it open.
                            wasExpanded -> latestOnExpandedChange(false)
                        }
                    }
                }
                // Inside the gesture, so pointer positions are in the unscaled FAB's coordinates.
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                },
        ) {
            Box(modifier = Modifier.size(FabSize), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
    }
}

/** One action: its icon and description in a pill, highlighted while the finger is over it. */
@Composable
private fun ActionRow(action: FabAction, hovered: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (hovered) ACTION_HOVER_SCALE else 1f, overshootSpec(), label = "actionHover")
    Surface(
        shape = RoundedCornerShape(50),
        // Highlighted in the FAB's colors; otherwise dark, to read over any photo.
        color = if (hovered) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
        contentColor = if (hovered) MaterialTheme.colorScheme.onPrimary else Color.White,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Grows toward the screen's middle, away from its right edge.
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(action.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A subtle tick: the menu opening, or the finger reaching an action. */
private fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Running the chosen action. */
private fun View.confirm() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}
