package com.voxi.captions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiBrandGradient
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Marca de Voxi reutilizable para mantener una identidad consistente en toda la
 * app (spec branding): la insignia con la "V" sobre el degradado de marca y, si
 * se quiere, el nombre con un subtitulo breve.
 */
@Composable
fun VoxiBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VoxiBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "V",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.5f).sp,
            color = VoxiBg,
        )
    }
}

/** Insignia + "Voxi" (+ subtitulo opcional), el lockup de marca del encabezado. */
@Composable
fun VoxiWordmark(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 28.dp,
    subtitle: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        VoxiBadge(size = badgeSize)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "Voxi",
                style = MaterialTheme.typography.titleLarge,
                color = VoxiTeal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxiSlate,
                )
            }
        }
    }
}
