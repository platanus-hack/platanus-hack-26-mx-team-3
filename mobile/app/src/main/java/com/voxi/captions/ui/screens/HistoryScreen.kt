package com.voxi.captions.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxi.captions.data.SessionMeta
import com.voxi.captions.ui.components.ChatBubble
import com.voxi.captions.ui.components.VoxiBadge
import com.voxi.captions.ui.theme.VoxiBackground
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiBorder
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.viewmodel.ConversationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historial de conversaciones guardadas (spec §10). Muestra la lista de
 * sesiones y, al tocar una, la conversación completa en modo solo lectura.
 */
@Composable
fun HistoryScreen(
    state: ConversationUiState,
    onClose: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onBackToList: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewing = state.viewingUtterances
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxiBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChip(label = "Volver", onClick = { if (viewing != null) onBackToList() else onClose() })
            Spacer(Modifier.width(12.dp))
            VoxiBadge(size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (viewing != null) "Conversación" else "Historial",
                style = MaterialTheme.typography.titleLarge,
                color = VoxiTeal,
            )
        }

        Spacer(Modifier.size(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                viewing != null -> SessionTranscript(viewing)
                state.historySessions.isEmpty() -> EmptyHistory()
                else -> SessionList(
                    sessions = state.historySessions,
                    onOpenSession = onOpenSession,
                    onDeleteSession = onDeleteSession,
                )
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionMeta>,
    onOpenSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                onOpen = { onOpenSession(session.id) },
                onDelete = { onDeleteSession(session.id) },
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionMeta,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VoxiSurfaceHigh)
            .border(1.dp, VoxiBorder, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDate(session.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = VoxiTeal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${session.count} frases",
                style = MaterialTheme.typography.labelSmall,
                color = VoxiSlate,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Borrar",
                style = MaterialTheme.typography.labelMedium,
                color = VoxiSlate,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = session.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = VoxiMint,
            maxLines = 2,
        )
    }
}

@Composable
private fun SessionTranscript(utterances: List<com.voxi.captions.model.Utterance>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(utterances, key = { _, u -> u.id }) { index, utterance ->
            ChatBubble(
                utterance = utterance,
                previous = utterances.getOrNull(index - 1),
            )
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Aún no hay conversaciones guardadas.\nLo que hables se guarda solo.",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BackChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = VoxiBg,
        modifier = Modifier
            .clip(shape)
            .background(VoxiTeal)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "Sin fecha"
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es"))
    return fmt.format(Date(millis))
}
