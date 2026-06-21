package com.voxi.captions.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxi.captions.model.VoiceType
import com.voxi.captions.ui.theme.VoxiBackground
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiBrandGradient
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Selector inicial de la voz que el teléfono presta a la persona sorda en la vía
 * de regreso (spec §7): masculina, femenina o neutral. Se muestra solo la
 * primera vez; luego la elección queda guardada.
 */
@Composable
fun VoiceSelectionScreen(
    onSelect: (VoiceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxiBackground)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VoxiBrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                style = MaterialTheme.typography.headlineSmall,
                color = VoxiBg,
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = "Elige tu voz",
            style = MaterialTheme.typography.titleLarge,
            color = VoxiTeal,
        )
        Text(
            text = "Cuando escribas, el teléfono lo dirá en voz alta con esta voz. " +
                "Puedes cambiarla después.",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        VoiceType.entries.forEach { type ->
            VoiceOption(type = type, onClick = { onSelect(type) })
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun VoiceOption(type: VoiceType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VoxiSurfaceHigh)
            .border(1.dp, VoxiTeal.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = VoxiTeal,
            )
            Text(
                text = type.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = VoxiSlate,
            )
        }
        Text(
            text = "Usar",
            style = MaterialTheme.typography.labelLarge,
            color = VoxiBg,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(VoxiTeal)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
