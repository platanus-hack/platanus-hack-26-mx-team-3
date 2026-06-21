package com.voxi.captions.model

/**
 * Tipo de voz que usa la vía de regreso (spec §7): el teléfono "presta" una voz
 * a la persona sorda. Se elige al inicio entre masculina, femenina o neutral.
 *
 * Hoy se aplica con el TTS nativo de Android ajustando tono ([ttsPitch]) y
 * velocidad ([ttsRate]) — es offline y confiable. [genderHint] se conserva para
 * mapear esta elección a una voz de ElevenLabs cuando se integre.
 */
enum class VoiceType(
    val displayName: String,
    val tagline: String,
    val ttsPitch: Float,
    val ttsRate: Float,
    val genderHint: String,
) {
    MALE("Masculina", "Tono más grave", 0.82f, 1.0f, "male"),
    FEMALE("Femenina", "Tono más agudo", 1.28f, 1.02f, "female"),
    NEUTRAL("Neutral", "Tono intermedio", 1.0f, 1.0f, "neutral");

    companion object {
        val Default = NEUTRAL
        fun fromName(name: String?): VoiceType =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
