package com.voxi.captions.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.Utterance
import com.voxi.captions.ui.components.ComposeBar
import com.voxi.captions.ui.components.SpeechBubble
import com.voxi.captions.ui.theme.VoxiBackground
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiBrandGradient
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.speakerColor
import com.voxi.captions.viewmodel.ConversationUiState

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
    onSelectSpeaker: (Speaker?) -> Unit = {},
    onSend: (String) -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxiBackground)
            // safeDrawing incluye barras del sistema + IME: la barra de escritura
            // se eleva con el teclado y nada se rompe (fix responsive).
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Header(
            isListening = state.isListening,
            canExport = state.utterances.isNotEmpty(),
            onExport = onExport,
            onToggleCamera = onToggleCamera,
        )

        Spacer(Modifier.size(12.dp))

        SpeakerSelector(
            manualSpeaker = state.manualSpeaker,
            onSelect = onSelectSpeaker,
        )

        Spacer(Modifier.size(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isModelLoading -> LoadingState()
                state.utterances.isEmpty() && state.partialText.isEmpty() ->
                    EmptyState(state.statusMessage)
                else -> Conversation(state)
            }
        }

        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = VoxiSlate,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.size(10.dp))

        ComposeBar(onSend = onSend)
    }
}

@Composable
private fun Header(
    isListening: Boolean,
    canExport: Boolean,
    onExport: () -> Unit,
    onToggleCamera: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VoxiBrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                style = MaterialTheme.typography.labelLarge,
                color = VoxiBg,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Voxi",
            style = MaterialTheme.typography.titleLarge,
            color = VoxiTeal,
        )
        Spacer(Modifier.weight(1f))
        if (isListening) {
            ListeningIndicator()
            Spacer(Modifier.width(12.dp))
        }
        if (canExport) {
            PillButton(label = "Exportar", onClick = onExport)
            Spacer(Modifier.width(8.dp))
        }
        PillButton(label = "Camara", onClick = onToggleCamera)
    }
}

/** Boton tipo pildora con borde teal, para acciones del header. */
@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = VoxiTeal,
        modifier = Modifier
            .clip(shape)
            .background(VoxiSurfaceHigh)
            .border(1.dp, VoxiTeal.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Selector de carril (spec §6, Modo A): Auto = diarización por pitch. */
@Composable
private fun SpeakerSelector(
    manualSpeaker: Speaker?,
    onSelect: (Speaker?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = manualSpeaker == null,
            onClick = { onSelect(null) },
            label = { Text("Auto") },
        )
        Speaker.entries.forEach { speaker ->
            val color = speakerColor(speaker)
            FilterChip(
                selected = manualSpeaker == speaker,
                onClick = { onSelect(speaker) },
                label = { Text(speaker.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.25f),
                    selectedLabelColor = color,
                ),
            )
        }
    }
}

@Composable
private fun ListeningIndicator() {
    val transition = rememberInfiniteTransition(label = "listening")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulse)
                .background(VoxiTeal, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Escuchando",
            style = MaterialTheme.typography.labelMedium,
            color = VoxiSlate,
        )
    }
}

@Composable
private fun Conversation(state: ConversationUiState) {
    val listState = rememberLazyListState()

    // Auto-scroll al final cuando llega texto nuevo.
    LaunchedEffect(state.utterances.size, state.partialText) {
        val total = state.utterances.size + if (state.partialText.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.utterances, key = Utterance::id) { utterance ->
            SpeakerRow(speaker = utterance.speaker, modifier = Modifier.animateItem()) {
                SpeechBubble(
                    text = utterance.text,
                    tone = utterance.tone,
                    speakerName = utterance.speaker.displayName,
                    speakerColor = speakerColor(utterance.speaker),
                    alignEnd = utterance.speaker == Speaker.TWO,
                )
            }
        }
        if (state.partialText.isNotEmpty()) {
            item(key = "partial") {
                SpeakerRow(speaker = state.partialSpeaker, modifier = Modifier.animateItem()) {
                    SpeechBubble(
                        text = state.partialText,
                        tone = state.partialTone,
                        isPartial = true,
                        speakerName = state.partialSpeaker.displayName,
                        speakerColor = speakerColor(state.partialSpeaker),
                        alignEnd = state.partialSpeaker == Speaker.TWO,
                    )
                }
            }
        }
    }
}

/** Coloca la burbuja en el carril del hablante: Hablante 1 a la izquierda, 2 a la derecha. */
@Composable
private fun SpeakerRow(
    speaker: Speaker,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        if (speaker == Speaker.TWO) Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.weight(5f, fill = false)) { content() }
        if (speaker == Speaker.ONE) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = VoxiTeal)
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Preparando el modelo de voz…",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
        )
    }
}

@Composable
private fun EmptyState(statusMessage: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.9f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = statusMessage ?: "Habla y verás los subtítulos aquí.",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
        )
    }
}
