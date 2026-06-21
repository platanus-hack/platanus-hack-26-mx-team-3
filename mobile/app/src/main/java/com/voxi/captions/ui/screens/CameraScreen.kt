package com.voxi.captions.ui.screens

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.voxi.captions.ui.components.ComposeBar
import com.voxi.captions.ui.components.SmartReplyChips
import com.voxi.captions.ui.components.VoxiBadge
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.speakerColor
import com.voxi.captions.vision.DetectedFace
import com.voxi.captions.vision.FaceMeshAnalyzer
import com.voxi.captions.viewmodel.ConversationUiState
import java.util.concurrent.Executors

/**
 * Capa 3 Modo B (spec §6): muestra la cámara y ancla el subtítulo junto a la
 * cara que está hablando. Si ML Kit o la cámara fallan, el overlay cae a un
 * subtítulo centrado y la app nunca crashea (spec §10).
 */
@Composable
fun CameraScreen(
    state: ConversationUiState,
    onFacesDetected: (List<DetectedFace>) -> Unit,
    onToggleCamera: () -> Unit,
    onSend: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, FaceMeshAnalyzer(onFacesDetected)) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(VoxiBg)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        CaptionOverlay(state = state, modifier = Modifier.fillMaxSize())

        TopBar(
            faceCount = state.faces.size,
            onToggleCamera = onToggleCamera,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        )

        // Via de regreso tambien en camara (spec §7): antes solo se veian las
        // caras y no se podia escribir. Incluye respuestas sugeridas con IA y la
        // barra de escritura, que se eleva con el teclado.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (state.suggestedReplies.isNotEmpty()) {
                SmartReplyChips(replies = state.suggestedReplies, onPick = onSend)
                Spacer(Modifier.size(10.dp))
            }
            ComposeBar(onSend = onSend)
        }
    }
}

/**
 * Subtítulo anclado a la cara que habló. Mientras alguien habla mostramos el
 * parcial pegado a la cara activa; cuando termina, queda anclado el último
 * texto en la posición guardada (spec §6, Modo B). Si no hay nada, no pinta.
 */
@Composable
private fun CaptionOverlay(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
) {
    val partial = state.partialText
    val anchored = state.anchoredCaption
    val active = state.activeFace

    BoxWithConstraints(modifier = modifier) {
        // Aura/silueta (spec 6/8): UNICAMENTE la cara que esta hablando se ilumina,
        // como un halo del color de su hablante. Las caras en reposo no llevan
        // ningun marco, para no saturar el encuadre.
        FaceAura(
            face = active,
            color = active?.let { speakerColor(it.speaker ?: state.partialSpeaker) },
            modifier = Modifier.fillMaxSize(),
        )

        val text: String
        val cx: Float
        val cy: Float
        val faceW: Float
        val accent: Color
        val name: String
        when {
            partial.isNotBlank() && active != null -> {
                text = partial; cx = active.cx; cy = active.cy; faceW = active.widthRatio
                accent = speakerColor(state.partialSpeaker); name = state.partialSpeaker.displayName
            }
            partial.isNotBlank() && anchored != null -> {
                text = partial; cx = anchored.cx; cy = anchored.cy; faceW = anchored.faceWidth
                accent = speakerColor(state.partialSpeaker); name = state.partialSpeaker.displayName
            }
            anchored != null -> {
                text = anchored.text; cx = anchored.cx; cy = anchored.cy; faceW = anchored.faceWidth
                accent = speakerColor(anchored.speaker); name = anchored.speaker.displayName
            }
            else -> {
                // Hay audio pero nadie en cuadro mueve la boca: alguien habla
                // fuera de cuadro. Modo C (spec §6): si el microfono es estereo,
                // el paneo L/R ubica el aviso del lado correcto; si no hay
                // direccion (mono), cae al centro de forma honesta.
                if (partial.isNotBlank()) {
                    val dir = state.soundDirection
                    val align = when {
                        dir == null -> Alignment.TopCenter
                        dir < -0.2f -> Alignment.TopStart
                        dir > 0.2f -> Alignment.TopEnd
                        else -> Alignment.TopCenter
                    }
                    OffscreenBanner(
                        text = partial,
                        direction = dir,
                        modifier = Modifier.align(align),
                    )
                }
                return@BoxWithConstraints
            }
        }

        // Ancla la burbuja POR ENCIMA de la cabeza para no tapar la cara.
        val (ax, ay) = anchorAboveHead(cx, cy, faceW)
        val bias by animateFloatAsState(
            targetValue = (ax * 2f - 1f).coerceIn(-1f, 1f),
            animationSpec = tween(250),
            label = "captionBias",
        )
        val verticalBias = (ay * 2f - 1f).coerceIn(-0.96f, 0.96f)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(BiasAlignment(bias, verticalBias)),
            contentAlignment = Alignment.Center,
        ) {
            FloatingCaption(text = text, accent = accent, speakerName = name)
        }
    }
}

/**
 * Punto por encima de la cabeza a partir del centro de la cara y su ancho. Si
 * no cabe arriba (cara muy alta en el encuadre) cae al pecho.
 */
private fun anchorAboveHead(cx: Float, cy: Float, faceWidth: Float): Pair<Float, Float> {
    val up = faceWidth.coerceIn(0.05f, 0.5f) * 0.9f + 0.06f
    val above = cy - up
    val y = if (above < 0.05f) (cy + up) else above
    return cx.coerceIn(0.04f, 0.96f) to y.coerceIn(0.05f, 0.95f)
}

@Composable
private fun FloatingCaption(text: String, accent: Color, speakerName: String) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .background(VoxiSurface.copy(alpha = 0.92f), shape)
            .border(1.dp, accent.copy(alpha = 0.85f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = speakerName,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoxiMint,
        )
    }
}

/**
 * Silueta-aura del que habla (spec 6/8). En vez de una caja, dibuja el contorno
 * real del rostro (FACE_OVAL de ML Kit) como un halo del color del hablante:
 * varias capas de trazo de grueso/transparente a fino/nitido, mas un relleno
 * radial tenue, con un latido suave mientras habla. Solo se pinta la cara
 * activa; las caras en reposo quedan limpias. Si no hubiera contorno, cae a un
 * ovalo del tamano de la cara.
 */
@Composable
private fun FaceAura(
    face: DetectedFace?,
    color: Color?,
    modifier: Modifier = Modifier,
) {
    if (face == null || color == null) return

    // Latido suave: el aura "respira" mientras la persona habla.
    val pulse = rememberInfiniteTransition(label = "aura")
    val glow by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "auraGlow",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val contour = face.contour

        val path = Path()
        if (contour != null && contour.size >= 6) {
            path.moveTo(contour[0] * w, contour[1] * h)
            var i = 2
            while (i + 1 < contour.size) {
                path.lineTo(contour[i] * w, contour[i + 1] * h)
                i += 2
            }
            path.close()
        } else {
            // Respaldo: si ML Kit no entrega contorno, un ovalo de la cara.
            val boxW = (face.widthRatio * w).coerceAtLeast(8f)
            val boxH = (face.heightRatio * h).coerceAtLeast(8f) * 1.18f
            val left = face.cx * w - boxW / 2f
            val top = face.cy * h - boxH / 2f
            path.addOval(Rect(left, top, left + boxW, top + boxH))
        }

        val cx = face.cx * w
        val cy = face.cy * h
        val radius = maxOf(face.widthRatio * w, face.heightRatio * h).coerceAtLeast(40f)

        // Halo de relleno: degradado radial tenue dentro de la silueta.
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f * glow), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
        )
        // Capas de trazo: de grueso/transparente (resplandor) a fino/nitido.
        drawPath(path, color = color.copy(alpha = 0.10f * glow), style = Stroke(width = 16.dp.toPx()))
        drawPath(path, color = color.copy(alpha = 0.20f * glow), style = Stroke(width = 9.dp.toPx()))
        drawPath(path, color = color.copy(alpha = 0.45f * glow), style = Stroke(width = 4.5f.dp.toPx()))
        drawPath(path, color = color.copy(alpha = 0.95f), style = Stroke(width = 2.dp.toPx()))
    }
}

/** Aviso cuando se escucha a alguien que no esta en el encuadre (Modo C). */
@Composable
private fun OffscreenBanner(text: String, direction: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val label = when {
        direction == null -> "Alguien fuera de cuadro"
        direction < -0.2f -> "← Alguien a tu izquierda"
        direction > 0.2f -> "Alguien a tu derecha →"
        else -> "Alguien al frente, fuera de cuadro"
    }
    Column(
        modifier = modifier
            .padding(top = 72.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth(0.7f)
            .background(VoxiSurface.copy(alpha = 0.92f), shape)
            .border(1.dp, VoxiSlate.copy(alpha = 0.7f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoxiSlate,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoxiMint,
        )
    }
}

@Composable
private fun TopBar(
    faceCount: Int,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoxiBadge(size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(VoxiTeal, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (faceCount > 0) "$faceCount cara(s)" else "Buscando caras…",
                style = MaterialTheme.typography.labelMedium,
                color = VoxiMint,
            )
        }
        BackChip(onClick = onToggleCamera)
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = "Ver chat",
        style = MaterialTheme.typography.labelLarge,
        color = VoxiBg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(VoxiTeal, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Click simple sin depender de Material ripple, para el chip del overlay. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/** Alineación con sesgo arbitrario en X/Y (−1..1) para anclar la burbuja. */
private class BiasAlignment(
    private val horizontalBias: Float,
    private val verticalBias: Float,
) : Alignment {
    override fun align(
        size: androidx.compose.ui.unit.IntSize,
        space: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    ): androidx.compose.ui.unit.IntOffset {
        val centerX = (space.width - size.width) / 2f
        val centerY = (space.height - size.height) / 2f
        val x = centerX * (1 + horizontalBias)
        val y = centerY * (1 + verticalBias)
        return androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
    }
}
