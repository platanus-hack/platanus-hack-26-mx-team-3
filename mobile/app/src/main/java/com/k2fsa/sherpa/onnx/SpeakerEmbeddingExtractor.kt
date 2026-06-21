// Wrapper JNI de sherpa-onnx (v1.13.3), copiado del proyecto oficial:
// https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.3/sherpa-onnx/kotlin-api/Speaker.kt
//
// IMPORTANTE: el package DEBE seguir siendo com.k2fsa.sherpa.onnx; el codigo
// nativo (libsherpa-onnx-jni.so) registra los metodos por este nombre exacto.
// Solo conservamos el extractor de embeddings (lo unico que usa Voxi). El
// emparejamiento de voces se hace en Kotlin (SpeakerEmbedder) para tener
// control de la persistencia y de la fusion de hablantes.
package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

class SpeakerEmbeddingExtractor(
    assetManager: AssetManager? = null,
    config: SpeakerEmbeddingExtractorConfig,
) {
    private var ptr: Long

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
    }

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    fun createStream(): OnlineStream {
        val p = createStream(ptr)
        return OnlineStream(p)
    }

    fun isReady(stream: OnlineStream) = isReady(ptr, stream.ptr)

    fun compute(stream: OnlineStream) = compute(ptr, stream.ptr)

    fun dim() = dim(ptr)

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: SpeakerEmbeddingExtractorConfig,
    ): Long

    private external fun newFromFile(
        config: SpeakerEmbeddingExtractorConfig,
    ): Long

    private external fun delete(ptr: Long)
    private external fun createStream(ptr: Long): Long
    private external fun isReady(ptr: Long, streamPtr: Long): Boolean
    private external fun compute(ptr: Long, streamPtr: Long): FloatArray
    private external fun dim(ptr: Long): Int

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
