package com.voxi.captions.data

import android.content.Context
import com.voxi.captions.model.Origin
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.Tone
import com.voxi.captions.model.Utterance
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Resumen de una conversación guardada, para listarla en el historial.
 */
data class SessionMeta(
    val id: Long,
    val updatedAt: Long,
    val count: Int,
    val preview: String,
)

/**
 * Guardado automático de conversaciones en almacenamiento interno (spec §10).
 *
 * Cada sesión se serializa a un .json en `filesDir/conversations/<id>.json`
 * usando `org.json` (sin dependencias extra). Esto permite cerrar la app y
 * volver a consultar lo conversado más tarde.
 */
class ConversationStore(context: Context) {

    private val dir: File =
        File(context.applicationContext.filesDir, "conversations").apply { mkdirs() }

    /** Guarda (o sobrescribe) una sesión. No lanza excepción hacia la UI. */
    fun save(id: Long, utterances: List<Utterance>) {
        if (utterances.isEmpty()) return
        runCatching {
            val root = JSONObject()
                .put("id", id)
                .put("updatedAt", System.currentTimeMillis())
            val arr = JSONArray()
            utterances.forEach { arr.put(toJson(it)) }
            root.put("utterances", arr)
            File(dir, "$id.json").writeText(root.toString())
        }
    }

    /** Lista las sesiones guardadas, de la más reciente a la más antigua. */
    fun list(): List<SessionMeta> = runCatching {
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { readMeta(it) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** Carga las intervenciones de una sesión. */
    fun load(id: Long): List<Utterance> = runCatching {
        val text = File(dir, "$id.json").takeIf { it.exists() }?.readText() ?: return emptyList()
        val arr = JSONObject(text).optJSONArray("utterances") ?: return emptyList()
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun delete(id: Long) {
        runCatching { File(dir, "$id.json").delete() }
    }

    private fun readMeta(file: File): SessionMeta? = runCatching {
        val root = JSONObject(file.readText())
        val arr = root.optJSONArray("utterances") ?: JSONArray()
        val preview = (0 until arr.length())
            .asSequence()
            .map { arr.getJSONObject(it).optString("text") }
            .firstOrNull { it.isNotBlank() }
            ?: "(sin texto)"
        SessionMeta(
            id = root.optLong("id"),
            updatedAt = root.optLong("updatedAt"),
            count = arr.length(),
            preview = preview,
        )
    }.getOrNull()

    private fun toJson(u: Utterance): JSONObject = JSONObject()
        .put("id", u.id)
        .put("text", u.text)
        .put("origin", u.origin.name)
        .put("speaker", u.speaker.index)
        .put("volume", u.tone.volume.toDouble())
        .put("pitch", u.tone.pitch.toDouble())
        .put("rhythm", u.tone.rhythm.toDouble())
        .put("emphasis", u.tone.emphasis)
        .put("rising", u.tone.rising)

    private fun fromJson(o: JSONObject): Utterance = Utterance(
        id = o.optLong("id"),
        text = o.optString("text"),
        tone = Tone(
            volume = o.optDouble("volume", 0.5).toFloat(),
            pitch = o.optDouble("pitch", 0.5).toFloat(),
            rhythm = o.optDouble("rhythm", 0.5).toFloat(),
            emphasis = o.optBoolean("emphasis", false),
            rising = o.optBoolean("rising", false),
        ),
        speaker = Speaker(o.optInt("speaker", 0).coerceAtLeast(0)),
        origin = runCatching { Origin.valueOf(o.optString("origin", "HEARD")) }
            .getOrDefault(Origin.HEARD),
    )
}
