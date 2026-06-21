package com.voxi.captions.audio

/**
 * Buffer de audio listo para consumir: siempre PCM16 mono a 16 kHz para Vosk y
 * el análisis de prosodia.
 *
 * [pan] es el aporte del Modo C (spec §6): cuando el micrófono es estéreo, el
 * balance de energía izquierda/derecha estima de qué lado viene el sonido
 * (−1 = izquierda, 0 = centro, +1 = derecha). Es `null` cuando el dispositivo
 * captura en mono o cuando hay silencio (dirección desconocida).
 */
class AudioFrame(
    val samples: ShortArray,
    val length: Int,
    val pan: Float?,
)
