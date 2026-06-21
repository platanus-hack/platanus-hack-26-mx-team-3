package com.voxi.captions.model

/**
 * Origen de una intervención en el chat.
 *
 * - [HEARD]: lo dijo una persona y Voxi lo transcribió → va al lado izquierdo.
 * - [SELF]: lo escribió el usuario y el teléfono lo dijo en voz alta (spec §7)
 *   → va al lado derecho.
 */
enum class Origin { HEARD, SELF }
