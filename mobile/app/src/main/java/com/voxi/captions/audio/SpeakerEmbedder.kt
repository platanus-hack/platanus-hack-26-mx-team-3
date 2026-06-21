package com.voxi.captions.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlin.math.sqrt

/**
 * Extractor de "huella neuronal" de voz (spec §6, mejora de diarizacion).
 *
 * Envuelve el modelo de speaker-embedding de sherpa-onnx (3D-Speaker ERes2Net):
 * convierte un fragmento de voz en un vector que identifica a la persona, mucho
 * mas robusto que las heuristicas de pitch/timbre. Los embeddings son casi
 * independientes del idioma, asi que un modelo entrenado en ingles separa bien
 * voces en espanol.
 *
 * Es OPCIONAL: si el modelo no carga (por memoria o ABI no incluida), Voxi sigue
 * funcionando con la diarizacion heuristica de [com.voxi.captions.viewmodel.SpeakerTracker].
 */
class SpeakerEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "SpeakerEmbedder"
        // Debe estar directamente en assets/ (no en subcarpeta), lo exige sherpa.
        private const val MODEL = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        // Minimo de audio para un embedding fiable (~0.6 s de voz).
        private const val MIN_SAMPLES = (0.6f * AudioCapture.SAMPLE_RATE).toInt()
    }

    @Volatile
    private var extractor: SpeakerEmbeddingExtractor? = null

    @Volatile
    var dim: Int = 0
        private set

    val isReady: Boolean get() = extractor != null

    /**
     * Carga el modelo desde assets. Es pesado (~25 MB): llamar SIEMPRE fuera del
     * hilo principal. Devuelve true si quedo listo.
     */
    fun load(): Boolean {
        if (extractor != null) return true
        return runCatching {
            val ex = SpeakerEmbeddingExtractor(
                assetManager = context.assets,
                config = SpeakerEmbeddingExtractorConfig(
                    model = MODEL,
                    numThreads = 2,
                    provider = "cpu",
                ),
            )
            dim = ex.dim()
            extractor = ex
            Log.i(TAG, "Modelo de embeddings listo (dim=$dim)")
            true
        }.getOrElse {
            Log.w(TAG, "No se pudo cargar el modelo de embeddings: ${it.message}")
            false
        }
    }

    /**
     * Calcula el embedding L2-normalizado de un fragmento PCM16 mono a 16 kHz.
     * Devuelve null si el modelo no esta listo, el audio es muy corto o falla.
     */
    fun embed(pcm: ShortArray, length: Int): FloatArray? {
        val ex = extractor ?: return null
        if (length < MIN_SAMPLES) return null
        val n = length.coerceAtMost(pcm.size)
        val samples = FloatArray(n)
        for (i in 0 until n) samples[i] = pcm[i] / 32768f
        return runCatching {
            val stream = ex.createStream()
            stream.acceptWaveform(samples, AudioCapture.SAMPLE_RATE)
            stream.inputFinished()
            if (!ex.isReady(stream)) {
                stream.release()
                return null
            }
            val emb = ex.compute(stream)
            stream.release()
            normalize(emb)
        }.getOrNull()
    }

    private fun normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val norm = sqrt(s)
        if (norm < 1e-6f) return v
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / norm
        return out
    }

    fun shutdown() {
        runCatching { extractor?.release() }
        extractor = null
    }
}
