package com.voxi.captions.ai

import com.voxi.captions.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Respuestas sugeridas con IA (estilo "smart replies" de LinkedIn): a partir de
 * lo último que se escuchó en la conversación, propone 3 respuestas cortas que
 * la persona sorda puede tocar para que el teléfono las diga en voz alta.
 *
 * Usa la API de Gemini (REST, sin SDK, vía [HttpURLConnection] para no agregar
 * dependencias). Es online; si no hay clave, no hay internet o falla, devuelve
 * lista vacía y la UI cae a plantillas offline (ver [OfflineReplies]).
 *
 * Cuidado con la CUOTA (spec §7): el free tier de Gemini es muy limitado por
 * dia/modelo. Por eso (1) usamos un modelo con cuota razonable, (2) la app llama
 * solo cuando el contexto cambia (dedup en el ViewModel) y (3) si Gemini
 * responde 429 (cuota agotada) entramos en un cooldown para no insistir y la UI
 * usa respuestas offline inteligentes mientras tanto.
 */
object SmartReplyService {

    // gemini-2.5-flash: tiene cuota disponible en el free tier (2.0-flash y
    // 2.5-flash-lite dan 429/limite muy bajo en este proyecto). thinkingBudget=0
    // evita que el "pensamiento" del modelo consuma todos los tokens de salida.
    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    // Cuando Gemini contesta 429 (cuota agotada), no insistimos hasta este
    // instante (epoch ms). Asi no quemamos llamadas inutiles ni bloqueamos la UI.
    @Volatile
    private var cooldownUntil = 0L

    /** ¿Está configurada la clave de Gemini? */
    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Genera hasta 3 respuestas a partir del contexto (frases escuchadas, de la
     * más antigua a la más reciente). Nunca lanza: devuelve vacío ante error o
     * durante el cooldown por cuota.
     */
    suspend fun suggest(context: List<String>): List<String> = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || context.isEmpty()) return@withContext emptyList()
        if (System.currentTimeMillis() < cooldownUntil) return@withContext emptyList()
        runCatching {
            val payload = buildRequest(context)
            val url = URL(ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                // Nuevo esquema de Gemini: la clave va en el header, no en ?key=
                setRequestProperty("X-goog-api-key", key)
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            when {
                code in 200..299 -> parseReplies(raw)
                // Cuota agotada: descansa antes de volver a intentar.
                code == 429 -> {
                    cooldownUntil = System.currentTimeMillis() + cooldownFrom(raw)
                    emptyList()
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /** Lee retryDelay del error 429 (segundos) o usa 60 s por defecto, con tope. */
    private fun cooldownFrom(raw: String): Long = runCatching {
        val secs = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(raw)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 60L
        secs.coerceIn(30L, 300L) * 1_000L
    }.getOrDefault(60_000L)

    private fun buildRequest(context: List<String>): String {
        val conversation = context.takeLast(6).joinToString("\n") { "- $it" }
        val prompt = buildString {
            append("Eres el asistente de una persona sorda en una conversacion presencial. ")
            append("Esto es lo ultimo que dijeron las personas a su alrededor (transcrito):\n")
            append(conversation)
            append("\n\nSugiere EXACTAMENTE 3 respuestas breves (maximo 8 palabras cada una), ")
            append("naturales, en espanol, que la persona sorda podria responder ahora. ")
            append("Que sean variadas (por ejemplo: una afirmativa, una pregunta y una neutral). ")
            append("Responde SOLO con un arreglo JSON de 3 cadenas de texto, sin nada mas.")
        }
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 200)
                    put("responseMimeType", "application/json")
                    // Desactiva el "thinking" de los modelos 2.5: sin esto, el
                    // presupuesto de tokens se va en razonamiento y la respuesta
                    // sale vacia.
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                },
            )
        }.toString()
    }

    /** Extrae el texto del primer candidato y lo interpreta como arreglo JSON. */
    private fun parseReplies(raw: String): List<String> = runCatching {
        val root = JSONObject(raw)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
        val arr = when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> JSONObject(text).optJSONArray("replies") ?: JSONArray()
            else -> JSONArray()
        }
        (0 until arr.length())
            .map { arr.getString(it).trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }.getOrDefault(emptyList())
}

/**
 * Plan B sin internet (o con la cuota de Gemini agotada): respuestas utiles
 * elegidas segun lo ultimo que se escucho. No es IA, pero detecta saludos,
 * preguntas, agradecimientos y despedidas para que los chips sean relevantes y
 * nunca queden vacios.
 */
object OfflineReplies {

    val defaults = listOf("Si, claro", "¿Puedes repetir?", "No estoy seguro")

    /** Respuestas adaptadas a la ultima frase escuchada. */
    fun forContext(heard: List<String>): List<String> {
        val last = heard.lastOrNull()?.trim().orEmpty()
        if (last.isEmpty()) return defaults
        val n = strip(last.lowercase())
        return when {
            isQuestion(last, n) -> listOf("Si, claro", "No, gracias", "Dejame pensarlo")
            has(n, "gracias") -> listOf("De nada", "Con gusto", "No hay de que")
            has(n, "hola", "buenos dias", "buenas tardes", "buenas noches", "buenas", "que tal", "como estas", "como esta", "mucho gusto") ->
                listOf("Hola, mucho gusto", "Bien, ¿y tu?", "Todo bien, gracias")
            has(n, "adios", "nos vemos", "hasta luego", "hasta pronto", "hasta manana", "bye", "chao", "cuidate") ->
                listOf("Nos vemos", "Cuidate", "Hasta pronto")
            has(n, "perdon", "disculpa", "lo siento", "perdona") ->
                listOf("No te preocupes", "Esta bien", "Tranquilo")
            has(n, "felicidades", "felicitaciones", "feliz cumple") ->
                listOf("¡Muchas gracias!", "Que amable", "Lo aprecio mucho")
            else -> listOf("Si, claro", "Entiendo", "¿Puedes repetir?")
        }
    }

    private fun isQuestion(raw: String, n: String): Boolean {
        if (raw.contains("?") || raw.contains("¿")) return true
        val starters = listOf(
            "que ", "qué ", "como ", "cuando ", "donde ", "por que", "porque ", "quien ",
            "cual ", "cuanto ", "puedes", "podrias", "quieres", "te gustaria", "vienes",
            "vas a", "tienes", "sabes", "vamos", "te parece",
        )
        return starters.any { n.startsWith(it) }
    }

    private fun has(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }

    /** Quita acentos para comparar de forma tolerante. */
    private fun strip(s: String): String = s
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
}
