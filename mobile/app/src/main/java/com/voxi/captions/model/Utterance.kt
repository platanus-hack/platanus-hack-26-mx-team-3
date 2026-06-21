package com.voxi.captions.model

/**
 * Una intervención (frase) ya fijada por Vosk como resultado final.
 *
 * Capa 1: texto. Capa 2: [tone] con la prosodia (cómo se dijo).
 * La Capa 3 (espacio) añadirá el hablante más adelante.
 */
data class Utterance(
    val id: Long,
    val text: String,
    val tone: Tone = Tone.Neutral,
)
