package org.costalonga.fattidelgiorno.dati.rete

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Esito di una chiamata in rete, già tradotto in casi che l'interfaccia sa mostrare. */
sealed class EsitoHttp {
    data class Ok(val corpo: String) : EsitoHttp()
    data class LimiteRaggiunto(val riprovaFraSecondi: Int?) : EsitoHttp()
    data object Timeout : EsitoHttp()
    data class Errore(val descrizione: String) : EsitoHttp()
}

/**
 * Un solo client per tutte le fonti: identifica l'app come chiede la
 * politica di Wikimedia sullo User-Agent e usa tempi massimi brevi.
 */
class ClientHttp(
    private val userAgent: String = USER_AGENT,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    fun scarica(url: String, intestazioni: Map<String, String> = emptyMap()): EsitoHttp {
        val richiesta = Request.Builder().url(url).header("User-Agent", userAgent).apply {
            intestazioni.forEach { (nome, valore) -> header(nome, valore) }
        }.build()
        return try {
            client.newCall(richiesta).execute().use { risposta ->
                when {
                    risposta.code == 429 -> EsitoHttp.LimiteRaggiunto(risposta.header("Retry-After")?.toIntOrNull())
                    risposta.isSuccessful -> EsitoHttp.Ok(risposta.body?.string().orEmpty())
                    else -> EsitoHttp.Errore("la fonte ha risposto con il codice ${risposta.code}")
                }
            }
        } catch (e: SocketTimeoutException) {
            EsitoHttp.Timeout
        } catch (e: IOException) {
            if (e.message?.contains("timeout", ignoreCase = true) == true) EsitoHttp.Timeout
            else EsitoHttp.Errore(e.message ?: "errore di rete")
        }
    }

    fun invia(url: String, corpoJson: String, intestazioni: Map<String, String> = emptyMap()): EsitoHttp {
        val tipo = "application/json; charset=utf-8".toMediaTypeOrNull()
        val richiesta = Request.Builder().url(url).header("User-Agent", userAgent)
            .post(corpoJson.toRequestBody(tipo)).apply {
                intestazioni.forEach { (nome, valore) -> header(nome, valore) }
            }.build()
        return try {
            client.newCall(richiesta).execute().use { risposta ->
                val corpo = risposta.body?.string().orEmpty()
                when {
                    risposta.code == 429 -> EsitoHttp.LimiteRaggiunto(risposta.header("Retry-After")?.toIntOrNull())
                    risposta.isSuccessful -> EsitoHttp.Ok(corpo)
                    else -> EsitoHttp.Errore("codice ${risposta.code}: ${corpo.take(300)}")
                }
            }
        } catch (e: SocketTimeoutException) {
            EsitoHttp.Timeout
        } catch (e: IOException) {
            EsitoHttp.Errore(e.message ?: "errore di rete")
        }
    }

    companion object {
        /** Formato richiesto da Wikimedia: nome/versione (contatto) libreria/versione. */
        const val USER_AGENT = "FattiDelGiorno/1.0 (https://costalonga.org/fatti-del-giorno) okhttp/4.12.0"
    }
}

/** Dice se il telefono ha una connessione utilizzabile. Sostituibile nelle prove. */
fun interface ControlloRete {
    fun online(): Boolean
}
