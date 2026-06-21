package com.voxi.captions.ui.theme

import androidx.compose.ui.graphics.Color
import com.voxi.captions.model.Speaker

/**
 * Color base por hablante (spec §8): cada voz detectada tiene su acento.
 *
 * Se indexa la paleta por el [Speaker.index]; con más hablantes que colores se
 * reciclan (módulo). El teal queda reservado para "Tú" (mensajes propios).
 */
fun speakerColor(speaker: Speaker): Color {
    val palette = VoxiSpeakerPalette
    val i = ((speaker.index % palette.size) + palette.size) % palette.size
    return palette[i]
}
