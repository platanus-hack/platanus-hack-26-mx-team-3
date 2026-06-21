package com.voxi.captions.audio

import com.voxi.captions.model.Tone
import com.voxi.captions.model.VoiceProfile
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analiza la prosodia del audio crudo (spec §5): volumen (RMS), pitch
 * (autocorrelación) y ritmo (onsets por segundo). Suaviza con EMA y entrega
 * un [Tone] con valores normalizados a 0..1.
 *
 * Además, al cerrar cada intervención calcula un [VoiceProfile] (huella de voz)
 * con la mediana y la dispersión del pitch, que alimenta la diarización.
 *
 * Se alimenta desde el mismo loop de IO que reparte buffers a Vosk (spec §2);
 * el coste por buffer es bajo y no toca el hilo principal.
 */
class ProsodyAnalyzer {

    companion object {
        // --- Constantes ajustables (spec §5) ---
        // Volumen en dBFS → 0..1
        private const val DB_MIN = -55f   // por debajo: silencio / susurro
        private const val DB_MAX = -12f   // por encima: grito
        // Pitch (Hz) → 0..1 (rango útil de voz humana)
        private const val PITCH_MIN_HZ = 85f
        private const val PITCH_MAX_HZ = 255f
        // Ritmo (onsets por segundo) → 0..1
        private const val RHYTHM_MAX = 5f
        // Suavizado exponencial (0..1; más alto = reacciona más rápido)
        private const val EMA = 0.25f
        // Subida de energía que cuenta como onset (sílaba)
        private const val ONSET_RISE = 0.12f
        // Salto súbito de volumen que marca énfasis
        private const val EMPHASIS_JUMP = 0.28f
        // La autocorrelación normalizada debe superar esto para considerar "voz"
        private const val VOICED_THRESHOLD = 0.3f
        // Ventana para contar onsets recientes
        private const val RHYTHM_WINDOW_MS = 2_000L
        // Mínimo de tramas con voz para confiar en la huella de voz.
        private const val MIN_VOICED_FRAMES = 5
    }

    // EMA en vivo (para el parcial)
    private var emaVolume = 0f
    private var emaPitch = 0.5f
    private var emaRhythm = 0f

    // Estado para onset / énfasis
    private var prevVolume = 0f
    private var aboveOnset = false
    private val onsetTimestamps = ArrayDeque<Long>()

    // Acumuladores por intervención (se reinician en [finishUtterance])
    private var uttVolumeSum = 0f
    private var uttVolumeCount = 0
    private var uttEmphasis = false
    private val pitchTrack = ArrayList<Float>()

    // Huella de voz de la última intervención cerrada.
    private var lastProfile = VoiceProfile.Unknown

    /** Reinicio total (watchdog del spec §4). */
    fun reset() {
        emaVolume = 0f
        emaPitch = 0.5f
        emaRhythm = 0f
        prevVolume = 0f
        aboveOnset = false
        onsetTimestamps.clear()
        lastProfile = VoiceProfile.Unknown
        resetUtterance()
    }

    private fun resetUtterance() {
        uttVolumeSum = 0f
        uttVolumeCount = 0
        uttEmphasis = false
        pitchTrack.clear()
    }

    /** Procesa un buffer PCM16 y actualiza el tono en vivo. */
    fun feed(buffer: ShortArray, length: Int) {
        if (length <= 0) return
        val now = System.currentTimeMillis()

        val volume = normalizedVolume(buffer, length)
        emaVolume = ema(emaVolume, volume)

        // Énfasis: salto súbito de volumen respecto al buffer anterior.
        if (volume - prevVolume > EMPHASIS_JUMP && volume > 0.5f) uttEmphasis = true

        // Onset: cruce de subida → cuenta como sílaba.
        if (!aboveOnset && volume - prevVolume > ONSET_RISE && volume > 0.2f) {
            aboveOnset = true
            onsetTimestamps.addLast(now)
        } else if (volume < 0.15f) {
            aboveOnset = false
        }
        while (onsetTimestamps.isNotEmpty() && now - onsetTimestamps.first() > RHYTHM_WINDOW_MS) {
            onsetTimestamps.removeFirst()
        }
        val onsetsPerSec = onsetTimestamps.size.toFloat() / (RHYTHM_WINDOW_MS / 1_000f)
        val rhythm = (onsetsPerSec / RHYTHM_MAX).coerceIn(0f, 1f)
        emaRhythm = ema(emaRhythm, rhythm)

        // Pitch (solo cuando hay voz clara).
        val pitchHz = detectPitchHz(buffer, length)
        if (pitchHz > 0f) {
            val pitchNorm =
                ((pitchHz - PITCH_MIN_HZ) / (PITCH_MAX_HZ - PITCH_MIN_HZ)).coerceIn(0f, 1f)
            emaPitch = ema(emaPitch, pitchNorm)
            pitchTrack.add(pitchNorm)
        }

        uttVolumeSum += volume
        uttVolumeCount++
        prevVolume = volume
    }

    /** Tono en vivo (para la burbuja parcial). */
    fun current(): Tone = Tone(
        volume = emaVolume,
        pitch = emaPitch,
        rhythm = emaRhythm,
        emphasis = uttEmphasis,
        rising = false,
    )

    /** Cierra la intervención: devuelve el tono agregado y reinicia acumuladores. */
    fun finishUtterance(): Tone {
        val avgVolume = if (uttVolumeCount > 0) uttVolumeSum / uttVolumeCount else emaVolume
        val avgPitch = if (pitchTrack.isNotEmpty()) pitchTrack.average().toFloat() else emaPitch
        lastProfile = buildProfile(avgVolume)
        val tone = Tone(
            volume = avgVolume,
            pitch = avgPitch,
            rhythm = emaRhythm,
            emphasis = uttEmphasis,
            rising = risingIntonation(),
        )
        resetUtterance()
        return tone
    }

    /** Huella de voz de la última intervención cerrada (para diarización). */
    fun lastVoiceProfile(): VoiceProfile = lastProfile

    /**
     * Construye el vector de características de la voz a partir del recorrido de
     * pitch: mediana (robusta) y dispersión (IQR normalizado).
     */
    private fun buildProfile(avgVolume: Float): VoiceProfile {
        val voiced = pitchTrack.size >= MIN_VOICED_FRAMES
        if (!voiced) return VoiceProfile(emaPitch, 0f, avgVolume, voiced = false)
        val sorted = pitchTrack.sorted()
        val median = percentile(sorted, 0.5f)
        val q1 = percentile(sorted, 0.25f)
        val q3 = percentile(sorted, 0.75f)
        val spread = (q3 - q1).coerceIn(0f, 1f)
        return VoiceProfile(
            medianPitch = median,
            pitchSpread = spread,
            meanVolume = avgVolume,
            voiced = true,
        )
    }

    private fun percentile(sorted: List<Float>, p: Float): Float {
        if (sorted.isEmpty()) return 0f
        val idx = (p * (sorted.size - 1)).coerceIn(0f, (sorted.size - 1).toFloat())
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        val frac = idx - lo
        return sorted[lo] * (1f - frac) + sorted[hi] * frac
    }

    /** Detecta si el pitch del último tercio sube respecto al primero (pregunta). */
    private fun risingIntonation(): Boolean {
        if (pitchTrack.size < 6) return false
        val third = pitchTrack.size / 3
        val start = pitchTrack.take(third).average()
        val end = pitchTrack.takeLast(third).average()
        return end - start > 0.12
    }

    private fun ema(prev: Float, value: Float) = prev + EMA * (value - prev)

    private fun normalizedVolume(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / length)
        if (rms < 1.0) return 0f
        val db = 20.0 * log10(rms / 32768.0)
        return ((db - DB_MIN) / (DB_MAX - DB_MIN)).toFloat().coerceIn(0f, 1f)
    }

    /** Pitch por autocorrelación; devuelve Hz o 0 si no hay voz clara. */
    private fun detectPitchHz(buffer: ShortArray, length: Int): Float {
        val sr = AudioCapture.SAMPLE_RATE
        val minLag = (sr / PITCH_MAX_HZ).toInt()
        val maxLag = (sr / PITCH_MIN_HZ).toInt()
        if (length < maxLag + 1) return 0f

        var energy = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            energy += s * s
        }
        if (energy < 1.0) return 0f

        var bestLag = -1
        var bestCorr = 0.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            val n = length - lag
            for (i in 0 until n) {
                corr += buffer[i].toDouble() * buffer[i + lag].toDouble()
            }
            val norm = corr / energy
            if (norm > bestCorr) {
                bestCorr = norm
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestCorr < VOICED_THRESHOLD) return 0f
        return sr.toFloat() / bestLag
    }
}
