package com.voxi.captions.model

/**
 * Hablante en el chat espacial (spec §6).
 *
 * Antes eran dos carriles fijos (Hablante 1 / 2). Ahora cada voz nueva detectada
 * por la diarización ([com.voxi.captions.viewmodel.SpeakerTracker]) recibe un
 * [index] incremental (0, 1, 2, …) que se mantiene estable durante la
 * conversación: la primera persona que habla es "Hablante 1", la segunda voz
 * distinta "Hablante 2", y así para N personas. El color se deriva del index
 * (ver `speakerColor`).
 */
@JvmInline
value class Speaker(val index: Int) {
    val displayName: String get() = "Hablante ${index + 1}"

    companion object {
        /** Primer hablante observado (arranque en frío). */
        val First = Speaker(0)
    }
}
