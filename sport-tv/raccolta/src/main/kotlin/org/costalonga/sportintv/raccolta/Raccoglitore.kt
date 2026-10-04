package org.costalonga.sportintv.raccolta

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.costalonga.sportintv.raccolta.connettori.tuttiIConnettori
import java.time.Instant

/**
 * Interroga tutte le fonti in parallelo, unisce i risultati e produce il
 * [Pacchetto]. Una fonte che non risponde non blocca le altre: se esiste un
 * pacchetto precedente, i suoi dati di quella fonte vengono conservati e
 * segnalati come possibilmente superati.
 */
class Raccoglitore(
    private val connettori: List<Connettore> = tuttiIConnettori(),
    private val http: Http = Http(),
    private val tempoMassimoFonteMs: Long = 180_000,
) {
    data class Esito(val connettore: Connettore, val risultato: RisultatoFonte?, val errore: String?)

    suspend fun raccogli(
        finestra: Finestra = Finestra.standard(),
        precedente: Pacchetto? = null,
        adesso: Instant = Instant.now(),
    ): Pacchetto = coroutineScope {
        val esiti = connettori.map { c ->
            async {
                try {
                    Esito(c, withTimeout(tempoMassimoFonteMs) { c.raccogli(finestra, http) }, null)
                } catch (e: CancellationException) {
                    if (e is kotlinx.coroutines.TimeoutCancellationException) Esito(c, null, "Tempo scaduto") else throw e
                } catch (e: Exception) {
                    Esito(c, null, descrivi(e))
                }
            }
        }.awaitAll()
        componi(esiti, finestra, precedente, adesso)
    }

    companion object {
        fun descrivi(e: Exception): String = when (e) {
            is RispostaNonValida -> "La fonte ha risposto con il codice ${e.codice}"
            is java.net.SocketTimeoutException -> "Tempo scaduto"
            is java.net.UnknownHostException -> "Indirizzo non raggiungibile"
            is java.io.IOException -> "Errore di rete (${e.message ?: e.javaClass.simpleName})"
            else -> "Dati non leggibili (${e.javaClass.simpleName})"
        }

        /**
         * Completa un pacchetto (di solito quello del servizio) con le fonti
         * raccolte altrove (di solito dal telefono): per ogni fonte presente
         * in [aggiunta] e riuscita, i suoi dati sostituiscono quelli di [base].
         */
        fun integra(base: Pacchetto, aggiunta: Pacchetto, finestra: Finestra): Pacchetto {
            val buone = aggiunta.fonti.filter { it.stato == StatoFonte.OK || it.stato == StatoFonte.VUOTA || it.stato == StatoFonte.PARZIALE }
                .associateBy { it.id }
            if (buone.isEmpty()) return base
            val elementi = base.fonti.filter { it.id !in buone }.flatMap { Unione.elementiDi(it.id, base) } +
                buone.keys.flatMap { Unione.elementiDi(it, aggiunta) }
            return base.copy(
                generato = minOf(base.generato, aggiunta.generato),
                fonti = base.fonti.map { buone[it.id] ?: it },
                eventi = Unione.unisci(elementi.filter { finestra.contiene(it.inizio) }, finestra),
            )
        }

        fun componi(esiti: List<Esito>, finestra: Finestra, precedente: Pacchetto?, adesso: Instant): Pacchetto {
            val elementi = mutableListOf<Elemento>()
            val fonti = esiti.map { (c, r, errore) ->
                val prima = precedente?.fonti?.firstOrNull { it.id == c.id }
                val fallitaDelTutto = r == null || (r.riuscite == 0 && r.fallite > 0)
                if (!fallitaDelTutto) {
                    elementi += r!!.elementi
                    Fonte(
                        id = c.id, nome = c.nome, tipo = c.tipo, url = c.url, copertura = c.copertura, canali = c.canali,
                        stato = when {
                            r.fallite > 0 -> StatoFonte.PARZIALE
                            r.elementi.isEmpty() -> StatoFonte.VUOTA
                            else -> StatoFonte.OK
                        },
                        ultimaVerifica = adesso, ultimoSuccesso = adesso, eventi = r.elementi.size,
                        messaggio = if (r.fallite > 0) "${r.fallite} richieste su ${r.fallite + r.riuscite} non riuscite: possono mancare giorni o canali." else r.messaggio,
                        condizioni = c.condizioni,
                    )
                } else {
                    val conservati = precedente?.let { Unione.elementiDi(c.id, it) }
                        ?.filter { finestra.contiene(it.inizio) } ?: emptyList()
                    elementi += conservati
                    val motivo = errore ?: r?.messaggio ?: "Nessuna richiesta riuscita"
                    Fonte(
                        id = c.id, nome = c.nome, tipo = c.tipo, url = c.url, copertura = c.copertura, canali = c.canali,
                        stato = if (prima?.ultimoSuccesso != null) StatoFonte.DATI_PRECEDENTI else StatoFonte.ERRORE,
                        ultimaVerifica = adesso, ultimoSuccesso = prima?.ultimoSuccesso, eventi = conservati.size,
                        messaggio = if (prima?.ultimoSuccesso != null) "$motivo. Restano i dati dell'ultimo aggiornamento riuscito, che potrebbero essere superati." else motivo,
                        condizioni = c.condizioni,
                    )
                }
            }
            val anno = adesso.atZone(ROMA).year
            val corretti = elementi.map { e ->
                if (e.tipo == TipoTrasmissione.NON_INDICATO && !e.calendario && Testo.soloAnniPassati(e.titoloOriginale, anno)) {
                    e.copy(
                        tipo = TipoTrasmissione.REPLICA,
                        nota = listOfNotNull(e.nota, "Indicata come replica perché il titolo cita solo annate passate").joinToString(". "),
                    )
                } else e
            }
            return Pacchetto(
                generato = adesso, da = finestra.da, a = finestra.a, fonti = fonti,
                eventi = Unione.unisci(corretti, finestra),
            )
        }
    }
}
