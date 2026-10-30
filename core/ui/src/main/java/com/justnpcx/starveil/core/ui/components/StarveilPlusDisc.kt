package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilBrand
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilMotion
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/** The ring's weight, and the glyph's. */
private val RING_WIDTH = 1.6.dp
private val GLYPH_WIDTH = 2.3.dp

/** The glyph's span: 26dp across, so 13 from the centre. */
private val GLYPH_HALF = 13.dp

/**
 * The plus: a disc with the brand gradient drawn as a ring and a glyph.
 *
 * A gradient ring rather than a gradient fill, because the page under it has to
 * stay visible through the middle: the disc floats over a list, and a solid
 * brand-coloured circle at this size is a button that shouts. The face is the
 * surface token — white in the day, #232426 at night — and nothing else about
 * the control changes between the two modes.
 *
 * The shadow is neutral black. A coloured shadow under a coloured ring reads as
 * a glow, and a glow on a control that is not the primary action is a claim the
 * design does not make.
 */
@Composable
fun StarveilPlusDisc(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = StarveilMotion.stateTransition(),
        label = "plus-press",
    )
    val haptics = LocalHapticFeedback.current
    // The brand's own stops, so the violet sits at 55% here exactly as it does
    // in the icon and the ring on the home page.
    val brush = Brush.linearGradient(*StarveilBrand.stops)

    Box(
        modifier = modifier
            .size(StarveilSpacing.plusDisc)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .clip(CircleShape)
            .background(StarveilColors.floatDisc)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RING_WIDTH.toPx()
            drawCircle(
                brush = brush,
                radius = size.minDimension / 2 - stroke / 2,
                style = Stroke(width = stroke),
            )
            val half = GLYPH_HALF.toPx()
            val width = GLYPH_WIDTH.toPx()
            drawLine(
                brush = brush,
                start = Offset(center.x - half, center.y),
                end = Offset(center.x + half, center.y),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            drawLine(
                brush = brush,
                start = Offset(center.x, center.y - half),
                end = Offset(center.x, center.y + half),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}
