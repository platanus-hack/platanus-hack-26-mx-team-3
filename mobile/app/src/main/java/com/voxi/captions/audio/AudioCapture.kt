package com.voxi.captions.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Captura de audio cruda con [AudioRecord]: 16 kHz, PCM 16-bit.
 *
 * Modo C (spec §6): intenta abrir el micrófono en ESTÉREO para estimar de qué
 * lado viene cada voz (paneo L/R). Si el dispositivo no soporta estéreo en la
 * fuente VOICE_RECOGNITION (muy común), cae a MONO sin dirección. En ambos
 * casos entrega siempre un buffer MONO a 16 kHz: Vosk y el ProsodyAnalyzer no
 * cambian, solo reciben además un [AudioFrame.pan] cuando se puede calcular.
 */
class AudioCapture {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // Ventana de lectura ~ varios buffers de 20-30 ms.
        private const val READ_SAMPLES = 1_600 // 100 ms a 16 kHz (frames mono)

        // Energía RMS mínima por canal para confiar en la dirección (evita que
        // el ruido de fondo invente un paneo en los silencios).
        private const val PAN_RMS_FLOOR = 180.0
    }

    private class Source(val record: AudioRecord, val stereo: Boolean)

    /**
     * Emite [AudioFrame] (PCM16 mono + paneo opcional) de forma continua en
     * [Dispatchers.IO]. Requiere que el permiso RECORD_AUDIO ya esté concedido.
     */
    @SuppressLint("MissingPermission")
    fun frames(): Flow<AudioFrame> = callbackFlow {
        // Primero intenta estéreo (Modo C); si falla, mono.
        val source = open(AudioFormat.CHANNEL_IN_STEREO, stereo = true)
            ?: open(AudioFormat.CHANNEL_IN_MONO, stereo = false)

        if (source == null) {
            close(IllegalStateException("No se pudo inicializar AudioRecord"))
            return@callbackFlow
        }

        val recorder = source.record
        recorder.startRecording()

        val worker = thread(name = "voxi-audio") {
            // En estéreo el buffer viene intercalado L,R,L,R...
            val raw = ShortArray(if (source.stereo) READ_SAMPLES * 2 else READ_SAMPLES)
            while (isActive) {
                val read = recorder.read(raw, 0, raw.size)
                if (read <= 0) continue
                val frame = if (source.stereo) {
                    val frames = read / 2
                    val mono = ShortArray(frames)
                    var sumL = 0.0
                    var sumR = 0.0
                    for (i in 0 until frames) {
                        val l = raw[2 * i].toInt()
                        val r = raw[2 * i + 1].toInt()
                        mono[i] = ((l + r) / 2).toShort()
                        sumL += (l * l).toDouble()
                        sumR += (r * r).toDouble()
                    }
                    AudioFrame(mono, frames, pan = computePan(sumL, sumR, frames))
                } else {
                    AudioFrame(raw.copyOf(read), read, pan = null)
                }
                trySend(frame)
            }
        }

        awaitClose {
            worker.interrupt()
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Paneo −1..1 (izq..der) o null si no hay energía suficiente. */
    private fun computePan(sumL: Double, sumR: Double, frames: Int): Float? {
        if (frames <= 0) return null
        val rmsL = sqrt(sumL / frames)
        val rmsR = sqrt(sumR / frames)
        val total = rmsL + rmsR
        if (rmsL < PAN_RMS_FLOOR && rmsR < PAN_RMS_FLOOR) return null
        if (total < 1.0) return null
        return ((rmsR - rmsL) / total).toFloat().coerceIn(-1f, 1f)
    }

    @SuppressLint("MissingPermission")
    private fun open(channelConfig: Int, stereo: Boolean): Source? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, ENCODING)
        if (minBuffer <= 0) return null
        val bufferSize = maxOf(minBuffer, READ_SAMPLES * (if (stereo) 4 else 2))
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                channelConfig,
                ENCODING,
                bufferSize,
            )
        }.getOrNull() ?: return null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        // Algunos equipos "aceptan" estéreo pero entregan un solo canal: aun así
        // el cálculo de paneo dará ~0 (centro), lo cual es correcto y no estorba.
        return Source(recorder, stereo)
    }
}
