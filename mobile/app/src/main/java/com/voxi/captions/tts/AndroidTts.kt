package com.voxi.captions.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voz de regreso (spec §7): la persona sorda escribe y el teléfono lo dice en
 * voz alta con [TextToSpeech] nativo en español. Es offline y confiable.
 *
 * Robustez: si el español no está disponible cae al idioma por defecto, y si el
 * motor falla nunca lanza excepción hacia la UI.
 *
 * Fix (primer mensaje): el motor TTS tarda en inicializar. Si el usuario manda
 * el primer mensaje antes de que termine [onInit], se encola y se reproduce en
 * cuanto esté listo, en vez de perderse.
 *
 * Fix (eco): mientras el teléfono habla, su voz entra por el micrófono y la
 * diarización la tomaría como un "hablante nuevo". [isSpeaking] expone si el
 * motor está reproduciendo (más una cola corta tras terminar) para que el
 * ViewModel silencie el reconocimiento durante ese tiempo.
 */
class AndroidTts(context: Context) {

    companion object {
        // Cola tras terminar de hablar: cubre el eco/reverb que sigue llegando
        // al micrófono un instante después de que el motor reporta "done".
        private const val TAIL_MS = 700L
    }

    @Volatile
    private var ready = false

    @Volatile
    private var speaking = false

    @Volatile
    private var quietUntil = 0L

    /** ¿El teléfono está hablando ahora (o acaba de hablar dentro de la cola)? */
    val isSpeaking: Boolean
        get() = speaking || System.currentTimeMillis() < quietUntil

    // Mensajes pedidos antes de que el motor estuviera listo.
    private val pending = ArrayDeque<String>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            configureLanguage()
            attachProgressListener()
            flushPending()
        }
    }

    private fun configureLanguage() {
        val result = runCatching { tts.setLanguage(Locale.forLanguageTag("es-ES")) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { tts.language = Locale.getDefault() }
        }
        ready = true
    }

    /** Marca [isSpeaking] mientras el motor reproduce cada frase. */
    private fun attachProgressListener() {
        runCatching {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }

                override fun onDone(utteranceId: String?) {
                    speaking = false
                    quietUntil = System.currentTimeMillis() + TAIL_MS
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                    quietUntil = System.currentTimeMillis() + TAIL_MS
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking = false
                    quietUntil = System.currentTimeMillis() + TAIL_MS
                }
            })
        }
    }

    /** Dice el texto en voz alta. Encola para no cortar lo que ya esté sonando. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            synchronized(pending) { pending.addLast(clean) }
            return
        }
        enqueue(clean)
    }

    /** Reproduce lo que se acumuló mientras el motor inicializaba. */
    private fun flushPending() {
        val backlog = synchronized(pending) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        backlog.forEach { enqueue(it) }
    }

    private fun enqueue(clean: String) {
        // Anticipa el estado "hablando": evita una ventana en la que el audio
        // propio entraría antes de que dispare onStart.
        speaking = true
        runCatching {
            tts.speak(clean, TextToSpeech.QUEUE_ADD, null, clean.hashCode().toString())
        }.onFailure {
            speaking = false
            quietUntil = System.currentTimeMillis() + TAIL_MS
        }
    }

    fun stop() {
        runCatching { tts.stop() }
        speaking = false
        quietUntil = System.currentTimeMillis() + TAIL_MS
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        speaking = false
    }
}
