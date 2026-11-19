package com.ohmy.zfsync.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How much a pressed element grows: a subtle 15%. */
const val PRESS_EXPAND_SCALE = 1.15f

/**
 * Grows the element slightly while it's pressed (tap or hold) and settles it back on release,
 * on the app's overshoot curve. Pass the same [interactionSource] the clickable element uses,
 * e.g. `IconButton(onClick, Modifier.pressExpand(source), interactionSource = source)`.
 *
 * A quick tap still gets the full expansion before shrinking back, so taps are visible too. It
 * scales in the draw layer, so it never changes layout.
 */
fun Modifier.pressExpand(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESS_EXPAND_SCALE,
): Modifier = this then PressExpandElement(interactionSource, pressedScale)

private data class PressExpandElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressExpandNode>() {
    override fun create() = PressExpandNode(interactionSource, pressedScale)

    override fun update(node: PressExpandNode) = node.update(interactionSource, pressedScale)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressExpand"
        properties["interactionSource"] = interactionSource
        properties["pressedScale"] = pressedScale
    }
}

private class PressExpandNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {

    private val scale = Animatable(1f)
    private var collectJob: Job? = null
    private var expandJob: Job? = null
    private val presses = mutableListOf<PressInteraction.Press>()

    override fun onAttach() = collectInteractions()

    override fun onDetach() {
        presses.clear()
    }

    fun update(interactionSource: InteractionSource, pressedScale: Float) {
        this.pressedScale = pressedScale
        if (interactionSource != this.interactionSource) {
            this.interactionSource = interactionSource
            presses.clear()
            collectJob?.cancel()
            collectInteractions()
        }
    }

    private fun collectInteractions() {
        collectJob = coroutineScope.launch {
            interactionSource.interactions.collect { onInteraction(it) }
        }
    }

    private fun onInteraction(interaction: Interaction) {
        val wasPressed = presses.isNotEmpty()
        when (interaction) {
            is PressInteraction.Press -> presses += interaction
            is PressInteraction.Release -> presses -= interaction.press
            is PressInteraction.Cancel -> presses -= interaction.press
        }
        val pressed = presses.isNotEmpty()
        if (pressed == wasPressed) return
        if (pressed) {
            expandJob = coroutineScope.launch { scale.animateTo(pressedScale, overshootSpec()) }
        } else {
            val expanding = expandJob
            coroutineScope.launch {
                // Let a quick tap's expansion play out so it's visible, then settle back unless
                // a new press started meanwhile.
                expanding?.join()
                if (presses.isEmpty()) scale.animateTo(1f, overshootSpec())
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            // Read in the layer block, so animating redraws the layer without re-measuring.
            placeable.placeWithLayer(0, 0) {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
    }
}
