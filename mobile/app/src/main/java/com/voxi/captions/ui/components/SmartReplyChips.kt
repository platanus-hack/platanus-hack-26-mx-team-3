package com.voxi.captions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Respuestas sugeridas con IA (estilo "smart replies" de LinkedIn): hasta 3
 * chips que la persona sorda puede tocar para que el teléfono diga esa frase en
 * voz alta (spec §7). Se generan a partir de lo último que se escuchó.
 *
 * Si la lista está vacía no dibuja nada (no ocupa espacio).
 */
@Composable
fun SmartReplyChips(
    replies: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        replies.forEachIndexed { index, reply ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            Text(
                text = reply,
                style = MaterialTheme.typography.bodyMedium,
                color = VoxiMint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(VoxiSurfaceHigh, RoundedCornerShape(18.dp))
                    .border(1.dp, VoxiTeal.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .clickable { onPick(reply) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
