package com.ohmy.zfsync.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The app's motion language. Every animation uses OvershootEasing (exits use its mirror,
 * OvershootExitEasing), via overshootSpec / overshootExitSpec. Exceptions, which keep Compose's
 * default non-overshooting motion: the photo pager's own swipe/snap, where overshooting would
 * flash the neighbouring photo, and the viewer's double-tap zoom.
 */

private const val OVERSHOOT_TENSION = 1.5f

/** Ease-out with a slight (~8%) overshoot: Android's OvershootInterpolator formula at a low tension. */
val OvershootEasing = Easing { t ->
    val u = t - 1f
    u * u * ((OVERSHOOT_TENSION + 1f) * u + OVERSHOOT_TENSION) + 1f
}

/**
 * [OvershootEasing] played backwards, for exits: a slight wind-up, then away. Played forwards, an
 * overshoot on something shrinking to zero would dip below zero (e.g. a mirrored, negative scale).
 */
val OvershootExitEasing = Easing { t -> 1f - OvershootEasing.transform(1f - t) }

const val MOTION_DURATION_MILLIS = 350

fun <T> overshootSpec(delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(MOTION_DURATION_MILLIS, delayMillis, OvershootEasing)

fun <T> overshootExitSpec(delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(MOTION_DURATION_MILLIS, delayMillis, OvershootExitEasing)

private const val CASCADE_MAX_STEP_MILLIS = 60
private const val CASCADE_MAX_SPREAD_MILLIS = 200

/**
 * Stagger delay for one item of a row that pops in from both ends toward the middle (and, when
 * hiding, out from the middle toward both ends). [first]..[last] are the positions taking part,
 * e.g. the thumbnails currently on screen; anything outside gets no delay. The step shrinks for
 * long rows so the whole cascade stays within [CASCADE_MAX_SPREAD_MILLIS].
 */
fun cascadeDelayMillis(position: Int, first: Int, last: Int, showing: Boolean): Int {
    if (position < first || position > last) return 0
    val maxDepth = (last - first) / 2
    if (maxDepth == 0) return 0
    val depth = minOf(position - first, last - position)
    val step = minOf(CASCADE_MAX_STEP_MILLIS, CASCADE_MAX_SPREAD_MILLIS / maxDepth)
    return step * if (showing) depth else maxDepth - depth
}

/** 0 = fully popped out, 1 = fully in; overshoots slightly past 1 on the way in. */
@Composable
fun animatePop(visible: Boolean, delayMillis: Int = 0): State<Float> = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = if (visible) overshootSpec(delayMillis) else overshootExitSpec(delayMillis),
    label = "pop",
)

/** Scales and fades by a pop progress. Read in the draw layer, so animating it doesn't recompose. */
fun Modifier.pop(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    scaleX = p
    scaleY = p
    alpha = p.coerceIn(0f, 1f)
}
