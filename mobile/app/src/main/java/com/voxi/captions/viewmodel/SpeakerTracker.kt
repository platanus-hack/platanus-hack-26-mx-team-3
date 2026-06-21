package com.voxi.captions.viewmodel

import com.voxi.captions.model.Speaker
import com.voxi.captions.model.VoiceProfile
import kotlin.math.sqrt

/**
 * Diarización por huella de voz para N hablantes (spec §6).
 *
 * Antes el modelo estaba topado a 2 carriles. Ahora cada frase fija se compara
 * contra todas las voces ya conocidas y:
 *  - si encaja en alguna (distancia pequeña) → es esa persona;
 *  - si no se parece a ninguna (distancia > [NEW_SPEAKER_THRESHOLD]) y aún hay
 *    cupo → se crea un hablante nuevo con el siguiente índice/color;
 *  - una voz que regresa vuelve a su mismo hablante (los clusters se conservan).
 *
 * Cada voz es una gaussiana diagonal adaptativa en el espacio (pitch mediano,
 * dispersión de pitch). La asignación usa distancia de Mahalanobis diagonal
 * (z-score) con piso de varianza, y una histéresis por margen para no parpadear
 * en la frontera entre dos voces parecidas. El índice de cada hablante es
 * estable (orden de aparición): el primero es Hablante 1, etc.
 */
class SpeakerTracker {

    companion object {
        // Adaptación de la gaussiana de cada hablante (0..1; más alto = más rápido).
        private const val ADAPT = 0.2f
        // Piso de desviación por dimensión (evita dividir por ~0 con pocos datos).
        private const val MIN_STD = 0.06f
        // Varianza inicial razonable de una voz (en unidades normalizadas 0..1).
        private const val INIT_VAR = 0.02f
        // Distancia de Mahalanobis por encima de la cual una voz se considera
        // desconocida y se crea un hablante nuevo.
        private const val NEW_SPEAKER_THRESHOLD = 2.6f
        // Margen (en distancia) para cambiar de hablante respecto al último:
        // evita parpadear en la zona de frontera entre voces parecidas.
        private const val SWITCH_MARGIN = 0.8f
        // Tope de hablantes distintos (después se reusa el más cercano).
        const val MAX_SPEAKERS = 8
    }

    /** Gaussiana diagonal adaptativa de un hablante en el espacio (pitch, spread). */
    private class Cluster(val index: Int, pitch: Float, spread: Float) {
        var meanPitch = pitch
        var meanSpread = spread
        var varPitch = INIT_VAR
        var varSpread = INIT_VAR

        /** Distancia de Mahalanobis diagonal (z-score) al vector dado. */
        fun distance(pitch: Float, spread: Float): Float {
            val sp = varPitch.coerceAtLeast(MIN_STD * MIN_STD)
            val ss = varSpread.coerceAtLeast(MIN_STD * MIN_STD)
            val dp = pitch - meanPitch
            val ds = spread - meanSpread
            // El pitch pesa el doble que la dispersión (es más discriminativo).
            return sqrt(2f * dp * dp / sp + ds * ds / ss)
        }

        /** Actualiza media y varianza por EMA hacia el nuevo vector. */
        fun update(pitch: Float, spread: Float) {
            val dp = pitch - meanPitch
            meanPitch += ADAPT * dp
            varPitch = (1f - ADAPT) * (varPitch + ADAPT * dp * dp)
            val ds = spread - meanSpread
            meanSpread += ADAPT * ds
            varSpread = (1f - ADAPT) * (varSpread + ADAPT * ds * ds)
        }
    }

    private val clusters = mutableListOf<Cluster>()
    private var lastSpeaker = Speaker.First
    private var manual: Speaker? = null

    val manualSpeaker: Speaker? get() = manual
    val currentSpeaker: Speaker get() = manual ?: lastSpeaker

    /** Hablantes ya descubiertos, en orden de aparición (para la UI). */
    val knownSpeakers: List<Speaker>
        get() = if (clusters.isEmpty()) listOf(Speaker.First)
        else clusters.map { Speaker(it.index) }

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

        // Arranque en frío: la primera voz define el Hablante 1.
        if (clusters.isEmpty()) {
            clusters.add(Cluster(0, pitch, spread))
            lastSpeaker = Speaker(0)
            return lastSpeaker
        }

        // Voz más cercana entre las conocidas.
        var nearest = clusters[0]
        var nearestDist = nearest.distance(pitch, spread)
        for (c in clusters) {
            val d = c.distance(pitch, spread)
            if (d < nearestDist) {
                nearest = c
                nearestDist = d
            }
        }

        // No se parece a nadie conocido y hay cupo → hablante nuevo.
        if (nearestDist > NEW_SPEAKER_THRESHOLD && clusters.size < MAX_SPEAKERS) {
            val nuevo = Cluster(clusters.size, pitch, spread)
            clusters.add(nuevo)
            lastSpeaker = Speaker(nuevo.index)
            return lastSpeaker
        }

        // Histéresis: para cambiar de hablante, el candidato debe ganarle por
        // margen al último; si no, conserva al último (evita parpadeo).
        val lastCluster = clusters.firstOrNull { it.index == lastSpeaker.index }
        val chosen = if (lastCluster != null && lastCluster !== nearest) {
            val dLast = lastCluster.distance(pitch, spread)
            if (nearestDist + SWITCH_MARGIN < dLast) nearest else lastCluster
        } else {
            nearest
        }

        chosen.update(pitch, spread)
        lastSpeaker = Speaker(chosen.index)
        return lastSpeaker
    }

    fun reset() {
        clusters.clear()
        lastSpeaker = manual ?: Speaker.First
    }
}
