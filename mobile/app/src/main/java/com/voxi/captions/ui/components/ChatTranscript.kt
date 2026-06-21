package com.voxi.captions.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voxi.captions.model.Origin
import com.voxi.captions.model.Utterance
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.speakerColor

/**
 * Lógica de chat compartida por la conversación en vivo y el historial:
 *  - Lo que se escucha ([Origin.HEARD]) va a la izquierda; lo que dicta el
 *    usuario ([Origin.SELF]) va a la derecha.
 *  - El avatar/nombre del hablante solo aparece cuando cambia respecto a la
 *    frase anterior (mensajes consecutivos del mismo hablante se agrupan).
 */

/** ¿Esta frase abre un grupo nuevo (cambió el hablante o el lado)? */
fun startsNewGroup(previous: Utterance?, current: Utterance): Boolean {
    if (previous == null) return true
    if (previous.origin != current.origin) return true
    return current.origin == Origin.HEARD && previous.speaker != current.speaker
}

/** Coloca la burbuja en su lado: izquierda (escuchado) o derecha (tú). */
@Composable
fun ChatRow(
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        if (alignEnd) Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.weight(5f, fill = false)) { content() }
        if (!alignEnd) Spacer(Modifier.weight(1f))
    }
}

/** Una burbuja del chat, con lado, color y agrupación ya resueltos. */
@Composable
fun ChatBubble(
    utterance: Utterance,
    previous: Utterance?,
    modifier: Modifier = Modifier,
    isPartial: Boolean = false,
) {
    val isSelf = utterance.origin == Origin.SELF
    val showSpeaker = startsNewGroup(previous, utterance)
    val label = if (isSelf) "Tú" else utterance.speaker.displayName
    val color = if (isSelf) VoxiTeal else speakerColor(utterance.speaker)
    ChatRow(alignEnd = isSelf, modifier = modifier) {
        SpeechBubble(
            text = utterance.text,
            tone = utterance.tone,
            isPartial = isPartial,
            speakerName = label,
            speakerColor = color,
            alignEnd = isSelf,
            showSpeaker = showSpeaker,
        )
    }
}
