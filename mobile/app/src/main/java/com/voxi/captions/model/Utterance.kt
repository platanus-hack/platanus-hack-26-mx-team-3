package com.voxi.captions.model

/**
 * Una intervención (frase) ya fijada por Vosk como resultado final.
 *
 * Capa 1: texto. Capa 2: [tone] con la prosodia (cómo se dijo).
 * Capa 3 (Modo A): [speaker] indica quién la dijo.
 *
 * [origin] decide el lado del chat: lo que se escucha ([Origin.HEARD]) va a la
 * izquierda y lo que el usuario dicta por TTS ([Origin.SELF]) va a la derecha.
 */
data class Utterance(
    val id: Long,
    val text: String,
    val tone: Tone = Tone.Neutral,
    val speaker: Speaker = Speaker.First,
    val origin: Origin = Origin.HEARD,
)
