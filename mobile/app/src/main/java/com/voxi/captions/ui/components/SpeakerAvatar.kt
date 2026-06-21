package com.voxi.captions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiBg

/**
 * Avatar circular del hablante: inicial sobre un degradado del color del carril.
 * Da identidad visual a cada burbuja (spec §8).
 */
@Composable
fun SpeakerAvatar(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 34,
    dimmed: Boolean = false,
) {
    val alpha = if (dimmed) 0.45f else 1f
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.65f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialOf(label),
            style = MaterialTheme.typography.labelLarge,
            color = VoxiBg,
        )
    }
}

/** Prefiere el numero del hablante ("Hablante 1" -> "1"); si no, la inicial. */
private fun initialOf(label: String): String {
    val digit = label.firstOrNull { it.isDigit() }
    return (digit ?: label.firstOrNull() ?: '?').toString().uppercase()
}
