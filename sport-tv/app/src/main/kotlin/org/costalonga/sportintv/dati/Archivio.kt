package org.costalonga.sportintv.dati

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.Finestra
import org.costalonga.sportintv.raccolta.Fonte
import org.costalonga.sportintv.raccolta.Http
import org.costalonga.sportintv.raccolta.JsonSportTv
import org.costalonga.sportintv.raccolta.Pacchetto
import org.costalonga.sportintv.raccolta.Raccoglitore
import org.costalonga.sportintv.raccolta.StatoFonte
import org.costalonga.sportintv.raccolta.Unione
import java.time.Duration
import java.time.Instant

/** Esito di un aggiornamento, da mostrare all'utente. */
sealed interface EsitoAggiornamento {
    data class Riuscito(val provenienza: String, val eventi: Int, val fontiInDifficolta: List<String>) : EsitoAggiornamento
    data class Fallito(val motivo: String) : EsitoAggiornamento
}

/** Dati presenti sul telefono, pronti per l'interfaccia. */
data class Istantanea(val eventi: List<Evento>, val fonti: List<Fonte>)

/**
 * Unico punto di accesso ai dati degli eventi.
 *
 * L'aggiornamento prova, secondo le impostazioni, il servizio online e/o la
 * lettura diretta delle fonti. Se tutto fallisce, sul telefono restano gli
 * ultimi dati validi: non vengono mai cancellati da un aggiornamento fallito.
 */
class Archivio(
    private val db: SportDatabase,
    private val impostazioni: Impostazioni,
    private val http: Http = Http(),
    private val raccoglitore: Raccoglitore = Raccoglitore(http = http),
) {
    private val mutex = Mutex()

    val istantanea: Flow<Istantanea> = db.eventi().tutti().map { righe ->
        val fonti = db.eventi().elencoFonti().mapNotNull { leggiFonte(it.json) }
        Istantanea(righe.mapNotNull { leggiEvento(it.json) }, fonti)
    }

    val fonti: Flow<List<Fonte>> = db.eventi().fonti().map { r -> r.mapNotNull { leggiFonte(it.json) } }

    fun evento(id: String): Flow<Evento?> = db.eventi().uno(id).map { it?.let { r -> leggiEvento(r.json) } }

    suspend fun eventiSalvati(): List<Evento> = db.eventi().elenco().mapNotNull { leggiEvento(it.json) }

    /** Ricostruisce il pacchetto salvato: serve da "precedente" per la raccolta diretta. */
    private suspend fun pacchettoLocale(): Pacchetto? {
        val stato = impostazioni.statoAttuale()
        val generato = stato.generato ?: return null
        val eventi = eventiSalvati()
        val fonti = db.eventi().elencoFonti().mapNotNull { leggiFonte(it.json) }
        if (fonti.isEmpty()) return null
        val f = Finestra.standard()
        return Pacchetto(generato = Instant.ofEpochMilli(generato), da = f.da, a = f.a, fonti = fonti, eventi = eventi)
    }

    suspend fun aggiorna(): EsitoAggiornamento = withContext(Dispatchers.IO) { mutex.withLock { aggiornaOra() } }

    private suspend fun aggiornaOra(): EsitoAggiornamento {
        val adesso = Instant.now()
        val pref = impostazioni.attuali()
        val errori = mutableListOf<String>()

        var pacchetto: Pacchetto? = null
        var provenienza = ""
        var dalServizioMaVecchio: Pacchetto? = null

        if (pref.origine != Origine.DIRETTA) {
            try {
                val p = JsonSportTv.decodeFromString(Pacchetto.serializer(), http.testo(pref.urlServizio))
                when {
                    p.versione > Pacchetto.VERSIONE -> errori += "Il servizio usa un formato più recente: aggiorna l'app"
                    Duration.between(p.generato, adesso) > SERVIZIO_VECCHIO && pref.origine == Origine.AUTOMATICA -> {
                        dalServizioMaVecchio = p
                        errori += "Il servizio non si aggiorna da oltre 12 ore"
                    }
                    else -> { pacchetto = p; provenienza = "servizio" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errori += "Servizio online non raggiungibile (${Raccoglitore.descrivi(e)})"
            }
        }

        if (pacchetto == null && pref.origine != Origine.SERVIZIO) {
            try {
                val p = raccoglitore.raccogli(Finestra.standard(adesso), pacchettoLocale() ?: dalServizioMaVecchio, adesso)
                if (p.eventi.isNotEmpty() && p.fonti.any { it.stato == StatoFonte.OK || it.stato == StatoFonte.PARZIALE || it.stato == StatoFonte.VUOTA }) {
                    pacchetto = p; provenienza = "fonti"
                } else {
                    errori += "Nessuna fonte ha risposto"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errori += "Lettura diretta delle fonti non riuscita (${e.javaClass.simpleName})"
            }
        }

        if (pacchetto == null && dalServizioMaVecchio != null) {
            pacchetto = dalServizioMaVecchio; provenienza = "servizio"
        }

        val finale = pacchetto
        if (finale == null) {
            val motivo = errori.joinToString(". ").ifEmpty { "Aggiornamento non riuscito" }
            impostazioni.fallito(adesso.toEpochMilli(), motivo)
            return EsitoAggiornamento.Fallito(motivo)
        }
        importa(finale, provenienza, adesso)
        return EsitoAggiornamento.Riuscito(
            provenienza = provenienza,
            eventi = finale.eventi.size,
            fontiInDifficolta = finale.fonti.filter { it.stato != StatoFonte.OK && it.stato != StatoFonte.VUOTA }.map { it.nome },
        )
    }

    /** Salva un pacchetto valido sul telefono: da qui in poi è consultabile anche senza rete. */
    suspend fun importa(p: Pacchetto, provenienza: String, ricevuto: Instant = Instant.now()) {
        salva(p)
        impostazioni.riuscito(ricevuto.toEpochMilli(), p.generato.toEpochMilli(), provenienza)
    }

    private suspend fun salva(p: Pacchetto) {
        val eventi = p.eventi.map {
            EventoRiga(it.id, it.inizio.toEpochMilli(), it.sport, it.trasmissioneVerificata, JsonSportTv.encodeToString(Evento.serializer(), it))
        }
        val fonti = p.fonti.mapIndexed { i, f -> FonteRiga(f.id, i, JsonSportTv.encodeToString(Fonte.serializer(), f)) }
        db.eventi().sostituisci(eventi, fonti)
    }

    companion object {
        val SERVIZIO_VECCHIO: Duration = Duration.ofHours(12)

        fun leggiEvento(json: String): Evento? = runCatching { JsonSportTv.decodeFromString(Evento.serializer(), json) }.getOrNull()
        fun leggiFonte(json: String): Fonte? = runCatching { JsonSportTv.decodeFromString(Fonte.serializer(), json) }.getOrNull()

        /** Chiave dell'evento senza il giorno: ritrova un evento spostato ad altra data. */
        fun impronta(e: Evento): String = Unione.chiaveBase(e).substringBeforeLast('|')
    }
}
