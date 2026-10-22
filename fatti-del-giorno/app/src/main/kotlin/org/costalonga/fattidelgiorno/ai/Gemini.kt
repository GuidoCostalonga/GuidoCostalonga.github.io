package org.costalonga.fattidelgiorno.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.costalonga.fattidelgiorno.dati.rete.ClientHttp
import org.costalonga.fattidelgiorno.dati.rete.EsitoHttp

/**
 * Servizio facoltativo: l'API Gemini di Google (piano gratuito con chiave
 * personale). La chiave viaggia solo nell'intestazione della richiesta e solo
 * quando chi usa l'app preme il pulsante. Senza chiave l'app non lo chiama mai.
 */
class Gemini(
    private val client: ClientHttp,
    private val base: String = "https://generativelanguage.googleapis.com/v1beta/models/",
) {
    sealed class Esito {
        data class Ok(val testo: String) : Esito()
        data class Limite(val messaggio: String) : Esito()
        data class Errore(val messaggio: String) : Esito()
    }

    suspend fun genera(chiave: String, modello: String, prompt: String): Esito = withContext(Dispatchers.IO) {
        val corpo = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                })
            })
        }
        val url = base + modello.trim() + ":generateContent"
        when (val esito = client.invia(url, corpo.toString(), mapOf("x-goog-api-key" to chiave.trim(), "Content-Type" to "application/json"))) {
            is EsitoHttp.Ok -> estrai(esito.corpo)
            is EsitoHttp.LimiteRaggiunto -> Esito.Limite("Quota del piano gratuito esaurita per ora" + (esito.riprovaFraSecondi?.let { ": riprova fra $it secondi" } ?: ". Riprova più tardi"))
            EsitoHttp.Timeout -> Esito.Errore("Il servizio non ha risposto in tempo")
            is EsitoHttp.Errore -> Esito.Errore(esito.descrizione)
        }
    }

    fun estrai(json: String): Esito = runCatching {
        val radice = Json.parseToJsonElement(json).jsonObject
        val candidati = radice["candidates"] as? JsonArray ?: return Esito.Errore("Risposta senza contenuto")
        val parti = (candidati.firstOrNull() as? JsonObject)?.get("content")?.jsonObject?.get("parts") as? JsonArray
        val testo = parti?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("\n")?.trim()
        if (testo.isNullOrEmpty()) Esito.Errore("Risposta vuota dal servizio") else Esito.Ok(testo)
    }.getOrElse { Esito.Errore("Risposta non leggibile: ${it.message}") }

    companion object {
        const val MODELLO_PREDEFINITO = "gemini-2.5-flash"
    }
}
