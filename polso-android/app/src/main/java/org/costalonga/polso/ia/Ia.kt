package org.costalonga.polso.ia

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.costalonga.polso.dati.ProfiloIa
import org.costalonga.polso.motore.ia.IaNonDisponibile
import org.costalonga.polso.motore.ia.MotoreTesto
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Modello locale con LiteRT-LM. Il motore viene caricato alla prima richiesta
 * (fino a una decina di secondi) e resta in memoria finché l'app è aperta.
 * Prova prima la GPU, poi la CPU. Nessun dato esce dal telefono.
 */
class IaLocale(private val context: Context, private val modelli: GestoreModelli) : MotoreTesto {
    private var engine: Engine? = null
    private var caricato: ProfiloIa? = null
    private val mutex = Mutex()
    override var nome: String = "IA locale"
        private set

    suspend fun pronto(profilo: ProfiloIa): Boolean = modelli.installato(profilo)

    private fun apri(profilo: ProfiloIa): Engine {
        val percorso = modelli.file(profilo).absolutePath
        val tentativi = listOf<Backend>(Backend.GPU(), Backend.CPU())
        var ultimo: Throwable? = null
        for (b in tentativi) {
            try {
                val e = Engine(EngineConfig(modelPath = percorso, backend = b, cacheDir = context.cacheDir.path))
                e.initialize()
                nome = "${profilo.nome} (${if (b is Backend.GPU) "GPU" else "CPU"}, sul telefono)"
                return e
            } catch (t: Throwable) {
                ultimo = t
            }
        }
        throw IaNonDisponibile("Il modello non si avvia su questo telefono: ${ultimo?.javaClass?.simpleName}. Prova un profilo più leggero.")
    }

    suspend fun genera(profilo: ProfiloIa, sistema: String, richiesta: String): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (!modelli.installato(profilo)) throw IaNonDisponibile("Modello non scaricato.")
            if (caricato != profilo) { chiudi(); engine = apri(profilo); caricato = profilo }
            val conf = ConversationConfig(systemInstruction = Contents.of(sistema), samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3))
            engine!!.createConversation(conf).use { conv ->
                val m = conv.sendMessage(richiesta)
                m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
            }
        }
    }

    var profiloAttivo: ProfiloIa? = null
    override suspend fun genera(sistema: String, richiesta: String): String =
        genera(profiloAttivo ?: throw IaNonDisponibile("Nessun modello scelto."), sistema, richiesta)

    fun chiudi() {
        runCatching { engine?.close() }
        engine = null
        caricato = null
    }
}

/**
 * Servizio online facoltativo con interfaccia compatibile OpenAI (per
 * esempio Google Gemini, che ha un piano gratuito con limiti giornalieri).
 * Disattivato all'inizio; ogni invio richiede il consenso esplicito
 * mostrando il testo esatto che parte. Mai usato in automatico.
 */
class IaOnline(private val indirizzo: String, private val modello: String, private val chiave: String) : MotoreTesto {
    override val nome: String = "servizio online ($modello)"

    override suspend fun genera(sistema: String, richiesta: String): String = withContext(Dispatchers.IO) {
        val corpo = JsonObject(mapOf(
            "model" to JsonPrimitive(modello),
            "temperature" to JsonPrimitive(0.3),
            "messages" to JsonArray(listOf(
                JsonObject(mapOf("role" to JsonPrimitive("system"), "content" to JsonPrimitive(sistema))),
                JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(richiesta))),
            )),
        )).toString()
        val c = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $chiave")
        }
        try {
            c.outputStream.use { it.write(corpo.toByteArray()) }
            val codice = c.responseCode
            if (codice !in 200..299) {
                throw IaNonDisponibile(when (codice) {
                    401, 403 -> "Chiave non valida o non autorizzata (errore $codice)."
                    429 -> "Limite del piano gratuito raggiunto (errore 429): riprova più tardi o usa l'IA locale."
                    else -> "Il servizio ha risposto con l'errore $codice."
                })
            }
            val testo = c.inputStream.bufferedReader().readText()
            Json.parseToJsonElement(testo).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content.trim()
        } catch (e: IOException) {
            throw IaNonDisponibile("Rete non disponibile o servizio irraggiungibile.")
        } finally {
            c.disconnect()
        }
    }
}

sealed interface StatoScaricamento {
    data object Fermo : StatoScaricamento
    data class InCorso(val profilo: ProfiloIa, val scaricati: Long, val totale: Long) : StatoScaricamento
    data class Verifica(val profilo: ProfiloIa) : StatoScaricamento
    data class Errore(val profilo: ProfiloIa, val messaggio: String) : StatoScaricamento
}

/**
 * Scaricamento esplicito dei modelli, con dimensione dichiarata, ripresa da
 * dove si era interrotto, annullamento (cancellando la coroutine) e verifica
 * dell'impronta SHA-256 pubblicata su Hugging Face.
 */
class GestoreModelli(private val context: Context) {
    private val cartella get() = File(context.filesDir, "modelli").apply { mkdirs() }
    private val _stato = MutableStateFlow<StatoScaricamento>(StatoScaricamento.Fermo)
    val stato: StateFlow<StatoScaricamento> = _stato

    fun file(p: ProfiloIa) = File(cartella, p.file)
    private fun parziale(p: ProfiloIa) = File(cartella, p.file + ".parziale")
    fun installato(p: ProfiloIa) = file(p).let { it.exists() && it.length() == p.byte }
    fun spazioLibero(): Long = cartella.usableSpace

    suspend fun scarica(p: ProfiloIa) = withContext(Dispatchers.IO) {
        if (installato(p)) return@withContext
        if (spazioLibero() < p.byte - parziale(p).length() + 200_000_000L) {
            _stato.value = StatoScaricamento.Errore(p, "Spazio insufficiente: servono circa ${p.byte / 1_000_000} MB liberi.")
            return@withContext
        }
        try {
            val dest = parziale(p)
            var gia = dest.length()
            val c = (URL(p.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
                if (gia > 0) setRequestProperty("Range", "bytes=$gia-")
            }
            if (c.responseCode == 200) gia = 0 else if (c.responseCode != 206) throw IOException("risposta ${c.responseCode}")
            c.inputStream.use { ing ->
                java.io.FileOutputStream(dest, gia > 0).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var letti = gia
                    var ultimo = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ing.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        letti += n
                        if (letti - ultimo > 4_000_000) { _stato.value = StatoScaricamento.InCorso(p, letti, p.byte); ultimo = letti }
                    }
                }
            }
            _stato.value = StatoScaricamento.Verifica(p)
            if (dest.length() != p.byte) throw IOException("dimensione inattesa (${dest.length()} invece di ${p.byte})")
            val md = MessageDigest.getInstance("SHA-256")
            dest.inputStream().use { s -> val b = ByteArray(1 shl 20); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            val h = md.digest().joinToString("") { "%02x".format(it) }
            if (h != p.sha256) { dest.delete(); throw IOException("impronta SHA-256 non corrispondente: file scartato") }
            dest.renameTo(file(p))
            _stato.value = StatoScaricamento.Fermo
        } catch (e: kotlinx.coroutines.CancellationException) {
            _stato.value = StatoScaricamento.Fermo
            throw e
        } catch (e: Exception) {
            _stato.value = StatoScaricamento.Errore(p, "Scaricamento non riuscito: ${e.message ?: e.javaClass.simpleName}. Puoi riprendere più tardi.")
        }
    }

    fun elimina(p: ProfiloIa) {
        file(p).delete(); parziale(p).delete()
        _stato.value = StatoScaricamento.Fermo
    }

    fun scaricatoParziale(p: ProfiloIa): Long = parziale(p).length()
}
