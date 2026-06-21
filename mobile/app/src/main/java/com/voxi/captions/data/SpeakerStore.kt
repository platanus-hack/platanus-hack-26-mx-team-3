package com.voxi.captions.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Memoria de voces estilo Alexa: guarda en disco la huella acústica de cada
 * persona descubierta para reconocerla de nuevo en sesiones futuras (spec §6).
 *
 * Cada perfil es el centroide + varianza de la gaussiana de un hablante en el
 * espacio (pitch, dispersión, brillo). Se serializa con org.json (sin libs
 * extra). Si el archivo no existe o está corrupto, se arranca de cero sin
 * romper la app.
 */
class SpeakerStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("voxi_speakers", Context.MODE_PRIVATE)

    /** Snapshot serializable de una voz aprendida. */
    data class Profile(
        val index: Int,
        val name: String?,
        val meanPitch: Float,
        val meanSpread: Float,
        val meanBright: Float,
        val varPitch: Float,
        val varSpread: Float,
        val varBright: Float,
    )

    fun load(): List<Profile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Profile(
                    index = o.getInt("index"),
                    name = if (o.isNull("name")) null else o.getString("name"),
                    meanPitch = o.getDouble("mp").toFloat(),
                    meanSpread = o.getDouble("ms").toFloat(),
                    meanBright = o.getDouble("mb").toFloat(),
                    varPitch = o.getDouble("vp").toFloat(),
                    varSpread = o.getDouble("vs").toFloat(),
                    varBright = o.getDouble("vb").toFloat(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("index", p.index)
                    .put("name", p.name ?: JSONObject.NULL)
                    .put("mp", p.meanPitch.toDouble())
                    .put("ms", p.meanSpread.toDouble())
                    .put("mb", p.meanBright.toDouble())
                    .put("vp", p.varPitch.toDouble())
                    .put("vs", p.varSpread.toDouble())
                    .put("vb", p.varBright.toDouble()),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val KEY = "profiles"
    }
}
