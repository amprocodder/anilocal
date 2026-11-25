package com.anilocal.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The app's press feedback, in place of Material's ripple: a flat [tint] fades over the pressed
 * element (and lighter over a hovered or keyboard-focused one), and with [pressedScale] the element
 * dips slightly while held. Drawing-only — no layout or recomposition — and the tint covers the
 * element's bounds, so a `clip(shape)` before the clickable shapes it.
 */
class PressIndication(
    private val tint: Color = Color.White,
    private val pressedAlpha: Float = 0.10f,
    private val pressedScale: Float = 1f,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressNode(interactionSource, tint, pressedAlpha, pressedScale)

    override fun equals(other: Any?): Boolean =
        other is PressIndication && other.tint == tint && other.pressedAlpha == pressedAlpha &&
            other.pressedScale == pressedScale

    override fun hashCode(): Int = (tint.hashCode() * 31 + pressedAlpha.hashCode()) * 31 + pressedScale.hashCode()

    companion object {
        /** Rows, cards, list items: a light tint. */
        val Default = PressIndication()
        /** Filled buttons: darken and dip 3%. */
        val Button = PressIndication(tint = Color.Black, pressedAlpha = 0.16f, pressedScale = 0.97f)
        /** Posters and artwork: a light tint and a dip. */
        val Card = PressIndication(pressedAlpha = 0.08f, pressedScale = 0.97f)
    }
}

private class PressNode(
    private val source: InteractionSource,
    private val tint: Color,
    private val pressedAlpha: Float,
    private val pressedScale: Float,
) : Modifier.Node(), DrawModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val amount = Animatable(0f)   // 0 = rest, 1 = fully pressed
    private var hoverOrFocus = 0f
    private var pressIn: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            val others = mutableListOf<Interaction>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is HoverInteraction.Enter, is FocusInteraction.Focus -> others += interaction
                    is HoverInteraction.Exit -> others -= interaction.enter
                    is FocusInteraction.Unfocus -> others -= interaction.focus
                }
                hoverOrFocus = when {
                    others.any { it is FocusInteraction.Focus } -> 0.12f / pressedAlpha
                    others.isNotEmpty() -> 0.05f / pressedAlpha
                    else -> 0f
                }.coerceAtMost(1f)
                if (presses.isNotEmpty()) {
                    pressIn = launch { amount.animateTo(1f, tween(PRESS_IN_MS)) }
                } else {
                    // Let a quick tap's press finish showing before it fades, so a tap is never invisible.
                    val running = pressIn
                    launch {
                        running?.join()
                        amount.animateTo(hoverOrFocus, tween(PRESS_OUT_MS))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val a = amount.value
        val s = 1f - (1f - pressedScale) * a.coerceIn(0f, 1f)
        if (s != 1f) {
            val content = this
            scale(s, pivot = Offset(size.width / 2f, size.height / 2f)) { content.drawTinted(a) }
        } else {
            drawTinted(a)
        }
    }

    private fun ContentDrawScope.drawTinted(a: Float) {
        drawContent()
        if (a > 0f) drawRect(tint.copy(alpha = pressedAlpha * a))
    }

    private companion object {
        const val PRESS_IN_MS = 60
        const val PRESS_OUT_MS = 200
    }
}
