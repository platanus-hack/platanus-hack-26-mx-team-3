package com.voxi.captions.model

/**
 * Prosodia de una intervención (spec §5): cómo se dijo, no qué se dijo.
 *
 * Las tres señales continuas están normalizadas a 0..1 para que la UI las
 * traduzca directamente a tamaño / peso / color. Las dos banderas marcan
 * eventos puntuales (énfasis y entonación de pregunta).
 */
data class Tone(
    /** 0 = susurro, 1 = grito (RMS normalizado en dBFS). */
    val volume: Float = 0.5f,
    /** 0 = grave, 1 = agudo (frecuencia fundamental normalizada). */
    val pitch: Float = 0.5f,
    /** 0 = lento, 1 = rápido (onsets de energía por segundo). */
    val rhythm: Float = 0.5f,
    /** Pico súbito de volumen → énfasis/alarma. */
    val emphasis: Boolean = false,
    /** La entonación sube al final → pregunta. */
    val rising: Boolean = false,
) {
    companion object {
        val Neutral = Tone()
    }
}
