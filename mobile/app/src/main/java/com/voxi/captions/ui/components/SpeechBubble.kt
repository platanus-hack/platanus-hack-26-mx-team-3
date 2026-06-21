package com.voxi.captions.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxi.captions.model.Tone
import com.voxi.captions.ui.theme.BUBBLE_BASE_SP
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.VoxiViolet
import com.voxi.captions.ui.theme.toneStyle
import kotlin.math.roundToInt

/**
 * Burbuja de chat: el hablante define color, avatar y carril (spec §6/§8), y el
 * tono define tamaño/peso/itálica/glow/shake del texto (spec §5).
 */
@Composable
fun SpeechBubble(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    isPartial: Boolean = false,
    speakerName: String? = null,
    speakerColor: Color = VoxiTeal,
    alignEnd: Boolean = false,
) {
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

    // Cola hacia el lado del hablante: esquina inferior reducida.
    val shape = if (alignEnd) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
    }

    val borderAlpha = if (isPartial) 0.35f else 0.9f
    val displayText = if (tone.rising && !isPartial && !text.trimEnd().endsWith("?")) {
        "$text?"
    } else {
        text
    }

    val avatar: @Composable () -> Unit = {
        SpeakerAvatar(
            label = speakerName ?: "?",
            color = speakerColor,
            dimmed = isPartial,
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!alignEnd) {
            avatar()
            Spacer(Modifier.width(8.dp))
        }

        var bubbleModifier = Modifier.offset { IntOffset(shakeDx.roundToInt(), 0) }
        if (style.glow && !isPartial) {
            bubbleModifier = bubbleModifier.shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = VoxiViolet,
                spotColor = VoxiViolet,
            )
        }
        bubbleModifier = bubbleModifier
            .background(
                Brush.verticalGradient(listOf(VoxiSurfaceHigh, VoxiSurface)),
                shape,
            )
            .border(1.dp, speakerColor.copy(alpha = borderAlpha), shape)
            .padding(horizontal = 16.dp, vertical = 11.dp)

        Column(modifier = bubbleModifier) {
            if (speakerName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = speakerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = speakerColor.copy(alpha = if (isPartial) 0.55f else 1f),
                    )
                    if (isPartial) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "escribiendo…",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoxiSlate,
                        )
                    }
                }
                Spacer(Modifier.size(3.dp))
            }
            Text(
                text = displayText,
                fontSize = animatedSize.sp,
                fontWeight = style.weight,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                color = if (isPartial) VoxiSlate else style.textColor,
            )
        }

        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            avatar()
        }
    }
}
