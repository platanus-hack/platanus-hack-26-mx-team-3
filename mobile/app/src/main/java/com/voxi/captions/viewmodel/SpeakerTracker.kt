package com.voxi.captions.viewmodel

import com.voxi.captions.data.SpeakerStore
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.VoiceProfile
import kotlin.math.sqrt

/**
 * Diarización por huella de voz para N hablantes, con memoria persistente
 * estilo Alexa (spec §6).
 *
 * Cada voz es una gaussiana diagonal adaptativa en el espacio
 * (pitch mediano, dispersión de pitch, brillo/timbre). Cada frase fija se
 * compara contra todas las voces conocidas y:
 *  - si encaja en alguna (distancia pequeña) → es esa persona;
 *  - si no se parece a ninguna (distancia > [NEW_SPEAKER_THRESHOLD]) y aún hay
 *    cupo → se crea un hablante nuevo con el siguiente índice/color;
 *  - una voz que regresa vuelve a su mismo hablante.
 *
 * Las voces se guardan en [SpeakerStore], así que una persona reconocida en una
 * sesión se vuelve a reconocer al reabrir la app. La asignación usa distancia de
 * Mahalanobis diagonal (z-score) con piso de varianza e histéresis por margen
 * para no parpadear en la frontera entre voces parecidas.
 */
class SpeakerTracker(private val store: SpeakerStore? = null) {

    companion object {
        // Adaptación de la gaussiana de cada hablante (0..1; más alto = más rápido).
        private const val ADAPT = 0.2f
        // Piso de desviación por dimensión (evita dividir por ~0 con pocos datos).
        private const val MIN_STD = 0.06f
        // Varianza inicial razonable de una voz (en unidades normalizadas 0..1).
        private const val INIT_VAR = 0.02f
        // Distancia de Mahalanobis por encima de la cual una voz se considera
        // desconocida y se crea un hablante nuevo.
        private const val NEW_SPEAKER_THRESHOLD = 2.8f
        // Margen (en distancia) para cambiar de hablante respecto al último.
        private const val SWITCH_MARGIN = 0.8f
        // Tope de hablantes distintos (después se reusa el más cercano).
        const val MAX_SPEAKERS = 8
        // Pesos por dimensión: el pitch y el timbre son los más discriminativos.
        private const val W_PITCH = 2.0f
        private const val W_SPREAD = 1.0f
        private const val W_BRIGHT = 1.5f
    }

    /** Gaussiana diagonal adaptativa de un hablante en (pitch, spread, brillo). */
    private class Cluster(
        val index: Int,
        var name: String?,
        pitch: Float,
        spread: Float,
        bright: Float,
    ) {
        var meanPitch = pitch
        var meanSpread = spread
        var meanBright = bright
        var varPitch = INIT_VAR
        var varSpread = INIT_VAR
        var varBright = INIT_VAR

        /** Distancia de Mahalanobis diagonal (z-score) ponderada al vector dado. */
        fun distance(pitch: Float, spread: Float, bright: Float): Float {
            val sp = varPitch.coerceAtLeast(MIN_STD * MIN_STD)
            val ss = varSpread.coerceAtLeast(MIN_STD * MIN_STD)
            val sb = varBright.coerceAtLeast(MIN_STD * MIN_STD)
            val dp = pitch - meanPitch
            val ds = spread - meanSpread
            val db = bright - meanBright
            return sqrt(
                W_PITCH * dp * dp / sp +
                    W_SPREAD * ds * ds / ss +
                    W_BRIGHT * db * db / sb,
            )
        }

        /** Actualiza media y varianza por EMA hacia el nuevo vector. */
        fun update(pitch: Float, spread: Float, bright: Float) {
            val dp = pitch - meanPitch
            meanPitch += ADAPT * dp
            varPitch = (1f - ADAPT) * (varPitch + ADAPT * dp * dp)
            val ds = spread - meanSpread
            meanSpread += ADAPT * ds
            varSpread = (1f - ADAPT) * (varSpread + ADAPT * ds * ds)
            val db = bright - meanBright
            meanBright += ADAPT * db
            varBright = (1f - ADAPT) * (varBright + ADAPT * db * db)
        }

        fun toProfile() = SpeakerStore.Profile(
            index, name, meanPitch, meanSpread, meanBright, varPitch, varSpread, varBright,
        )
    }

    private val clusters = mutableListOf<Cluster>()
    private var nextIndex = 0
    private var lastSpeaker = Speaker.First
    private var manual: Speaker? = null

    init {
        // Carga las voces aprendidas en sesiones anteriores (memoria tipo Alexa).
        store?.load()?.forEach { p ->
            val c = Cluster(p.index, p.name, p.meanPitch, p.meanSpread, p.meanBright)
            c.varPitch = p.varPitch
            c.varSpread = p.varSpread
            c.varBright = p.varBright
            clusters.add(c)
        }
        nextIndex = (clusters.maxOfOrNull { it.index } ?: -1) + 1
        if (clusters.isNotEmpty()) lastSpeaker = Speaker(clusters.first().index)
    }

    val manualSpeaker: Speaker? get() = manual
    val currentSpeaker: Speaker get() = manual ?: lastSpeaker

    /** Hablantes ya descubiertos, en orden de aparición (para la UI). */
    val knownSpeakers: List<Speaker>
        get() = if (clusters.isEmpty()) listOf(Speaker.First)
        else clusters.map { Speaker(it.index) }

    fun nameOf(speaker: Speaker): String? =
        clusters.firstOrNull { it.index == speaker.index }?.name

    fun rename(speaker: Speaker, name: String) {
        clusters.firstOrNull { it.index == speaker.index }?.name = name.ifBlank { null }
        persist()
    }

    fun setManual(speaker: Speaker?) {
        manual = speaker
        if (speaker != null) lastSpeaker = speaker
    }

    /** Clasifica una frase ya fijada a partir de su huella de voz. */
    fun classify(profile: VoiceProfile): Speaker {
        manual?.let { return it }

        // Sin voz fiable: no toques el modelo, conserva al último hablante.
        if (!profile.voiced) return lastSpeaker

        val pitch = profile.medianPitch
        val spread = profile.pitchSpread
        val bright = profile.brightness

        // Arranque en frío: la primera voz define el Hablante 1.
        if (clusters.isEmpty()) {
            clusters.add(Cluster(nextIndex, null, pitch, spread, bright))
            lastSpeaker = Speaker(nextIndex)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Voz más cercana entre las conocidas.
        var nearest = clusters[0]
        var nearestDist = nearest.distance(pitch, spread, bright)
        for (c in clusters) {
            val d = c.distance(pitch, spread, bright)
            if (d < nearestDist) {
                nearest = c
                nearestDist = d
            }
        }

        // No se parece a nadie conocido y hay cupo → hablante nuevo.
        if (nearestDist > NEW_SPEAKER_THRESHOLD && clusters.size < MAX_SPEAKERS) {
            val nuevo = Cluster(nextIndex, null, pitch, spread, bright)
            clusters.add(nuevo)
            lastSpeaker = Speaker(nuevo.index)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Histéresis: para cambiar de hablante, el candidato debe ganarle por
        // margen al último; si no, conserva al último (evita parpadeo).
        val lastCluster = clusters.firstOrNull { it.index == lastSpeaker.index }
        val chosen = if (lastCluster != null && lastCluster !== nearest) {
            val dLast = lastCluster.distance(pitch, spread, bright)
            if (nearestDist + SWITCH_MARGIN < dLast) nearest else lastCluster
        } else {
            nearest
        }

        chosen.update(pitch, spread, bright)
        lastSpeaker = Speaker(chosen.index)
        persist()
        return lastSpeaker
    }

    /**
     * Reset suave: arranca una conversación nueva PERO conserva las voces
     * aprendidas (memoria tipo Alexa). Solo olvida cuál fue el último hablante.
     */
    fun softReset() {
        lastSpeaker = manual ?: (clusters.firstOrNull()?.let { Speaker(it.index) } ?: Speaker.First)
    }

    /** Olvida por completo a todas las voces (también en disco). */
    fun forgetAll() {
        clusters.clear()
        nextIndex = 0
        lastSpeaker = manual ?: Speaker.First
        store?.clear()
    }

    private fun persist() {
        store?.save(clusters.map { it.toProfile() })
    }
}
