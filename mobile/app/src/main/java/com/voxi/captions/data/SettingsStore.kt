package com.voxi.captions.data

import android.content.Context
import com.voxi.captions.model.VoiceType

/**
 * Preferencias ligeras de la app (SharedPreferences). Por ahora guarda el tipo
 * de voz del TTS y si el usuario ya lo eligió, para mostrar el selector solo la
 * primera vez (spec §7).
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("voxi_settings", Context.MODE_PRIVATE)

    var voiceChosen: Boolean
        get() = prefs.getBoolean(KEY_CHOSEN, false)
        set(value) = prefs.edit().putBoolean(KEY_CHOSEN, value).apply()

    var voiceType: VoiceType
        get() = VoiceType.fromName(prefs.getString(KEY_VOICE, null))
        set(value) = prefs.edit().putString(KEY_VOICE, value.name).apply()

    companion object {
        private const val KEY_VOICE = "voice_type"
        private const val KEY_CHOSEN = "voice_chosen"
    }
}
