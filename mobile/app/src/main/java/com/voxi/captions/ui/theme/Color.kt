package com.voxi.captions.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Acentos (paleta Voxi) ---
val VoxiTeal = Color(0xFF42E8B4)   // primario / activo / energía
val VoxiBlue = Color(0xFF57B7FF)   // secundario / hablante 2 / calma
val VoxiViolet = Color(0xFF9B84D2) // énfasis / glow (provisional, ver spec §8)
val VoxiMint = Color(0xFFC2EDE0)   // texto alto contraste / superficies suaves
val VoxiSlate = Color(0xFF64748B)  // texto secundario / bordes / atenuado

// --- Paleta de hablantes (spec §6/§8) ---
// Un color distinto por cada voz detectada por la diarizacion. El teal se
// reserva para "Tu" (mensajes propios por TTS), asi que aqui no se incluye.
// Con mas de 8 hablantes los colores se reciclan (index % size).
val VoxiSpeakerPalette = listOf(
    Color(0xFF57B7FF), // 1 azul
    Color(0xFF9B84D2), // 2 violeta
    Color(0xFFFFB454), // 3 ambar
    Color(0xFFFF7A8A), // 4 coral
    Color(0xFF8FD14F), // 5 lima
    Color(0xFFE08CFF), // 6 magenta
    Color(0xFF4DD0E1), // 7 cian
    Color(0xFFFF6E6E), // 8 rojo
)

// --- Neutros oscuros ---
val VoxiBg = Color(0xFF0A0E14)         // fondo base
val VoxiBgElevated = Color(0xFF0E141D) // fondo con un punto mas de luz (gradiente)
val VoxiSurface = Color(0xFF121821)    // tarjetas / barras
val VoxiSurfaceHigh = Color(0xFF1A2330) // superficie elevada / campos
val VoxiBorder = Color(0xFF243043)     // bordes sutiles sobre superficies

// --- Gradientes / brushes reutilizables (spec §8: que se vea premium) ---

/** Fondo principal: degradado vertical muy sutil para dar profundidad. */
val VoxiBackground: Brush
    get() = Brush.verticalGradient(listOf(VoxiBgElevated, VoxiBg))

/** Degradado de marca para el logo y el boton "Decir". */
val VoxiBrandGradient: Brush
    get() = Brush.linearGradient(listOf(VoxiTeal, VoxiBlue))

/** Halo radial suave detras del indicador de escucha. */
fun speakerGradient(base: Color): Brush =
    Brush.linearGradient(listOf(base.copy(alpha = 0.20f), base.copy(alpha = 0.04f)))
