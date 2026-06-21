package com.voxi.captions.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.voxi.captions.BuildConfig
import com.voxi.captions.model.VoiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Voz humana de ElevenLabs (spec §7, opcional). Si hay internet y clave, sintetiza
 * con una voz expresiva (masculina/femenina/neutral según [VoiceType]); si falla
 * la red o no hay clave, llama a [onFallback] para que el TTS nativo lo diga.
 *
 * Igual que [AndroidTts], expone [isSpeaking] (con cola tras terminar) para que el
 * ViewModel silencie el micrófono mientras el teléfono habla y no se transcriba a
 * sí mismo (fix del eco).
 */
class ElevenLabsTts(context: Context) {

    companion object {
        private const val TAIL_MS = 700L
        // Modelo multilingüe: buena pronunciación en español.
        private const val MODEL_ID = "eleven_multilingual_v2"

        /** ¿Hay clave de ElevenLabs configurada en local.properties? */
        val isConfigured: Boolean get() = BuildConfig.ELEVENLABS_API_KEY.isNotBlank()
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Serializa la síntesis/reproducción para que dos frases no se encimen.
    private val mutex = Mutex()

    @Volatile
    private var speaking = false

    @Volatile
    private var quietUntil = 0L

    @Volatile
    private var voiceType: VoiceType = VoiceType.Default

    @Volatile
    private var player: MediaPlayer? = null

    val isSpeaking: Boolean
        get() = speaking || System.currentTimeMillis() < quietUntil

    fun setVoiceType(type: VoiceType) {
        voiceType = type
    }

    /**
     * Dice [text] con la voz de ElevenLabs. Si no se puede (sin clave, sin red o
     * error del servidor), invoca [onFallback] con el mismo texto.
     */
    fun speak(text: String, onFallback: (String) -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!isConfigured) {
            onFallback(clean)
            return
        }
        scope.launch {
            mutex.withLock {
                val audio = fetch(clean)
                if (audio == null) {
                    onFallback(clean)
                } else {
                    play(audio)
                }
            }
        }
    }

    private fun voiceId(): String = when (voiceType) {
        VoiceType.MALE -> BuildConfig.ELEVEN_VOICE_MALE
        VoiceType.FEMALE -> BuildConfig.ELEVEN_VOICE_FEMALE
        VoiceType.NEUTRAL -> BuildConfig.ELEVEN_VOICE_NEUTRAL
    }

    /** Pide el audio (mp3) a ElevenLabs. Devuelve null ante cualquier error. */
    private fun fetch(text: String): ByteArray? = runCatching {
        val url = URL("https://api.elevenlabs.io/v1/text-to-speech/${voiceId()}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg")
        }
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", MODEL_ID)
            put(
                "voice_settings",
                JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                },
            )
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return@runCatching null
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        bytes
    }.getOrNull()

    private suspend fun play(bytes: ByteArray) {
        val file = File(appContext.cacheDir, "voxi_eleven.mp3")
        runCatching { file.writeBytes(bytes) }.onFailure { return }
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp

            fun finish() {
                speaking = false
                quietUntil = System.currentTimeMillis() + TAIL_MS
                runCatching { mp.release() }
                if (player === mp) player = null
                if (cont.isActive) cont.resume(Unit)
            }

            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { finish() }
                mp.setOnErrorListener { _, _, _ -> finish(); true }
                mp.prepare()
                speaking = true
                mp.start()
            }.onFailure { finish() }

            cont.invokeOnCancellation { finish() }
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        speaking = false
        quietUntil = System.currentTimeMillis() + TAIL_MS
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
