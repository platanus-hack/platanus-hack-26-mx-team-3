// Wrapper JNI de sherpa-onnx (v1.13.3), copiado del proyecto oficial:
// https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.3/sherpa-onnx/kotlin-api/SpeakerEmbeddingExtractorConfig.kt
//
// IMPORTANTE: el package DEBE seguir siendo com.k2fsa.sherpa.onnx.
package com.k2fsa.sherpa.onnx

data class SpeakerEmbeddingExtractorConfig(
    val model: String = "",
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
)
