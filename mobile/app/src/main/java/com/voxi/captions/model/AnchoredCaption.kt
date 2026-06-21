package com.voxi.captions.model

/**
 * Subtítulo "pegado" a una persona en el Modo cámara (spec §6, Modo B).
 *
 * Guarda lo último que dijo esa persona junto con la posición normalizada
 * (0..1) de su cara en el momento de hablar, para que el texto quede anclado
 * encima de ella y la siga aunque se mueva.
 */
data class AnchoredCaption(
    val text: String,
    val tone: Tone,
    val speaker: Speaker,
    val cx: Float,
    val cy: Float,
    // Ancho de la cara (0..1) para colocar la burbuja por ENCIMA de la cabeza
    // y no taparla (spec §6, Modo B).
    val faceWidth: Float = 0.2f,
)
