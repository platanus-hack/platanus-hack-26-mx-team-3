package com.voxi.captions.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxi.captions.model.Tone
import com.voxi.captions.ui.theme.BUBBLE_BASE_SP
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiViolet
import com.voxi.captions.ui.theme.toneStyle
import androidx.compose.material3.Text
import kotlin.math.roundToInt

/**
 * Burbuja de chat cuyo estilo refleja el tono de voz (spec §5 y §8).
 *
 * El tamaño se anima suave (crece al gritar, se encoge al susurrar), el énfasis
 * añade glow violeta y un grito leve "shake".
 */
@Composable
fun SpeechBubble(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    isPartial: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val style = toneStyle(tone)

    val animatedSize by animateFloatAsState(
        targetValue = BUBBLE_BASE_SP * style.fontScale,
        animationSpec = tween(250),
        label = "fontSize",
    )

    // "Shake" muy leve al gritar (solo en frases ya fijadas, no en el parcial).
    val shakeDx = if (style.shake && !isPartial) {
        val transition = rememberInfiniteTransition(label = "shake")
        val dx by transition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(90), RepeatMode.Reverse),
            label = "shakeDx",
        )
        dx
    } else {
        0f
    }

    val accentAlpha = if (isPartial) 0.3f else 0.85f
    val displayText = if (tone.rising && !isPartial && !text.trimEnd().endsWith("?")) {
        "$text?"
    } else {
        text
    }

    var bubbleModifier = modifier.offset { IntOffset(shakeDx.roundToInt(), 0) }
    if (style.glow && !isPartial) {
        bubbleModifier = bubbleModifier.shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = VoxiViolet,
            spotColor = VoxiViolet,
        )
    }
    bubbleModifier = bubbleModifier
        .background(VoxiSurface, shape)
        .border(1.dp, style.accent.copy(alpha = accentAlpha), shape)
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Text(
        text = displayText,
        fontSize = animatedSize.sp,
        fontWeight = style.weight,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        color = if (isPartial) VoxiSlate else style.textColor,
        modifier = bubbleModifier,
    )
}
