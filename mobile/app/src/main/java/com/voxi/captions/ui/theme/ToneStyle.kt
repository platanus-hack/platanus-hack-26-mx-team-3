package com.voxi.captions.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.voxi.captions.model.Tone

/** Tamaño base del subtítulo en sp; picos hasta ~28 sp al gritar (spec §8). */
const val BUBBLE_BASE_SP = 18f

/** Estilo visual derivado de un [Tone] (spec §5, tabla de mapeo). */
data class ToneStyle(
    val fontScale: Float,
    val weight: FontWeight,
    val italic: Boolean,
    val accent: Color,
    val textColor: Color,
    val shake: Boolean,
    val glow: Boolean,
)

/**
 * Traduce la prosodia a estilo visual:
 *  - Volumen → tamaño + peso (+ shake al gritar, itálica/atenuado al susurrar).
 *  - Pitch + ritmo → color de acento (energía vs. calma).
 *  - Énfasis → glow violeta.
 */
fun toneStyle(tone: Tone): ToneStyle {
    // 0.8x (susurro) .. ~1.55x (grito) → 22sp se vuelve ~34sp.
    val fontScale = 0.8f + tone.volume * 0.75f

    val weight = when {
        tone.volume > 0.7f -> FontWeight.Bold
        tone.volume < 0.3f -> FontWeight.Light
        else -> FontWeight.Normal
    }

    val whisper = tone.volume < 0.3f
    val energetic = tone.pitch > 0.55f && tone.rhythm > 0.5f
    val calm = tone.pitch < 0.45f && tone.rhythm < 0.5f

    val accent = when {
        tone.emphasis -> VoxiViolet  // pico súbito / alarma
        energetic -> VoxiTeal        // alegría / energía
        calm -> VoxiBlue             // calma / seriedad
        else -> VoxiSlate
    }

    val textColor = if (whisper) VoxiSlate else VoxiMint

    return ToneStyle(
        fontScale = fontScale,
        weight = weight,
        italic = whisper,
        accent = accent,
        textColor = textColor,
        shake = tone.volume > 0.8f,
        glow = tone.emphasis,
    )
}
