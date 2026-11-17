package com.ohmy.zfsync.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The app's motion language. Every animation uses OvershootEasing (exits use its mirror,
 * OvershootExitEasing), via overshootSpec / overshootExitSpec. Two exceptions: opacity fades run
 * linearly over the same duration (fadeSpec), since opacity can't overshoot and on the overshoot
 * curve a fade would finish almost immediately; and the photo pager's own swipe/snap keeps its
 * default motion, where overshooting would flash the neighbouring photo.
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

/** Opacity fade over the same duration as a pop, evenly from start to end in either direction. */
fun <T> fadeSpec(delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(MOTION_DURATION_MILLIS, delayMillis, LinearEasing)

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

/** A pop's two channels: scale on the overshoot curve, opacity on a full-length linear fade. */
@Stable
class PopState internal constructor(private val scaleState: State<Float>, private val alphaState: State<Float>) {
    val scale: Float get() = scaleState.value
    val alpha: Float get() = alphaState.value

    /** True once both channels have reached zero, i.e. nothing is left on screen. */
    val isGone: Boolean get() = scale == 0f && alpha == 0f
}

/** Pops in (scale 0 to 1 with a slight overshoot, fading in) or out; both channels share [delayMillis]. */
@Composable
fun animatePop(visible: Boolean, delayMillis: Int = 0): PopState {
    val target = if (visible) 1f else 0f
    val scale = animateFloatAsState(
        targetValue = target,
        animationSpec = if (visible) overshootSpec(delayMillis) else overshootExitSpec(delayMillis),
        label = "popScale",
    )
    val alpha = animateFloatAsState(targetValue = target, animationSpec = fadeSpec(delayMillis), label = "popAlpha")
    return remember(scale, alpha) { PopState(scale, alpha) }
}

/** Applies a [PopState]. Read in the draw layer, so animating it doesn't recompose. */
fun Modifier.pop(state: PopState): Modifier = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    alpha = state.alpha
}
