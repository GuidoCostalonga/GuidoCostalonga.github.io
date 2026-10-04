package org.costalonga.sportintv.raccolta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val ROMA: ZoneId = ZoneId.of("Europe/Rome")

/**
 * Un elemento grezzo letto da una fonte: una messa in onda (palinsesto) o una
 * data di calendario. L'unione li trasforma in [Evento].
 */
data class Elemento(
    val fonte: String,
    val sport: Sport,
    val genere: String,
    val competizione: String?,
    val titolo: String,
    val partecipanti: List<String>,
    val inizio: Instant,
    val fine: Instant?,
    val verificato: Instant,
    /** Valorizzati solo per i palinsesti. */
    val canale: String? = null,
    val piattaforma: String? = null,
    val tipo: TipoTrasmissione = TipoTrasmissione.NON_INDICATO,
    val accesso: Accesso = Accesso.NON_INDICATO,
    val link: String? = null,
    val titoloOriginale: String = titolo,
    val nota: String? = null,
    /** Pagina ufficiale dell'evento (calendari). */
    val linkUfficiale: String? = null,
    val stato: StatoEvento? = null,
    val notaEvento: String? = null,
    /** Nota da mostrare solo se nessun palinsesto conferma la trasmissione. */
    val notaSeNonTrasmesso: String? = null,
) {
    val calendario: Boolean get() = canale == null
}

/** La finestra di raccolta: da oggi a mezzanotte (ora italiana) per [giorni] giorni. */
data class Finestra(val primoGiorno: LocalDate, val giorni: Int) {
    val da: Instant = primoGiorno.atStartOfDay(ROMA).toInstant()
    val a: Instant = primoGiorno.plusDays(giorni.toLong()).atStartOfDay(ROMA).toInstant()
    val date: List<LocalDate> get() = (0 until giorni).map { primoGiorno.plusDays(it.toLong()) }
    fun contiene(i: Instant) = !i.isBefore(da) && i.isBefore(a)

    companion object {
        /** Oggi più i 14 giorni successivi. */
        fun standard(adesso: Instant = Instant.now()) = Finestra(adesso.atZone(ROMA).toLocalDate(), 15)
    }
}

class RisultatoFonte(
    val elementi: List<Elemento>,
    /** Richieste riuscite e richieste fallite: servono a distinguere i dati parziali. */
    val riuscite: Int,
    val fallite: Int,
    val messaggio: String? = null,
)

interface Connettore {
    val id: String
    val nome: String
    val tipo: TipoFonte
    val url: String
    val copertura: String
    val canali: List<String>
    val condizioni: String
    suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte
}

class RispostaNonValida(val codice: Int, url: String) : IOException("HTTP $codice per $url")

/**
 * Accesso alla rete condiviso fra i connettori.
 *
 * - tempo massimo per richiesta;
 * - al massimo due richieste contemporanee per ciascun sito;
 * - due nuovi tentativi, distanziati, solo per errori di rete e risposte 5xx/429;
 * - nessun nuovo tentativo per 404 e altri 4xx: la risposta è definitiva.
 */
class Http(
    private val client: OkHttpClient = predefinito(),
    private val tentativi: Int = 3,
    private val attesaBaseMs: Long = 1_500,
) {
    private val semafori = ConcurrentHashMap<String, Semaphore>()

    suspend fun testo(url: String, intestazioni: Map<String, String> = emptyMap()): String {
        val host = url.substringAfter("://").substringBefore('/')
        val semaforo = semafori.getOrPut(host) { Semaphore(2) }
        var ultimoErrore: IOException? = null
        repeat(tentativi) { n ->
            if (n > 0) delay(attesaBaseMs * n * n)
            try {
                return semaforo.withPermit { scarica(url, intestazioni) }
            } catch (e: RispostaNonValida) {
                if (e.codice != 429 && e.codice < 500) throw e
                ultimoErrore = e
            } catch (e: IOException) {
                ultimoErrore = e
            }
        }
        throw ultimoErrore ?: IOException("Errore sconosciuto per $url")
    }

    private suspend fun scarica(url: String, intestazioni: Map<String, String>): String = withContext(Dispatchers.IO) {
        val richiesta = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "it-IT,it;q=0.9")
            .apply { intestazioni.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(richiesta).execute().use { r ->
            if (!r.isSuccessful) throw RispostaNonValida(r.code, url)
            r.body?.string() ?: ""
        }
    }

    companion object {
        /** Ci si presenta con un nome riconoscibile e un recapito pubblico. */
        const val USER_AGENT = "SportInTVItalia/1.0 (+https://costalonga.org/sport-tv/; guida non commerciale)"

        fun predefinito(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
