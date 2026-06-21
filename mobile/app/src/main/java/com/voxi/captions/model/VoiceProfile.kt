package com.voxi.captions.model

/**
 * Huella acústica (embedding ligero) de una intervención, usada para la
 * diarización del Modo A (spec §6).
 *
 * En vez de un solo pitch promedio se usa un vector de características más
 * discriminativo y robusto a outliers:
 *  - [medianPitch]: pitch mediano (0..1). La mediana ignora errores de octava
 *    y picos espurios mejor que el promedio.
 *  - [pitchSpread]: dispersión del pitch (IQR normalizado, 0..1). Cada persona
 *    modula distinto; sirve como segunda dimensión del vector.
 *  - [brightness]: brillo/timbre (energía de altas frecuencias, 0..1). Separa
 *    voces con pitch parecido pero timbre distinto (clave para distinguir mejor
 *    el cambio de persona).
 *  - [meanVolume]: energía media (0..1), solo informativa.
 *  - [voiced]: hubo suficientes tramas con voz clara como para confiar en el
 *    vector. Si es false, la diarización mantiene al último hablante.
 */
data class VoiceProfile(
    val medianPitch: Float,
    val pitchSpread: Float,
    val brightness: Float,
    val meanVolume: Float,
    val voiced: Boolean,
) {
    companion object {
        val Unknown = VoiceProfile(0.5f, 0f, 0.5f, 0f, voiced = false)
    }
}
