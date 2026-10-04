package org.costalonga.sportintv.promemoria

import org.costalonga.sportintv.dati.Archivio
import org.costalonga.sportintv.dati.Preferiti
import org.costalonga.sportintv.dati.Promemoria
import org.costalonga.sportintv.dati.Seguito
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.StatoEvento
import java.time.Duration
import java.time.Instant

/** Avviso da dare all'utente su un promemoria che cambia. */
data class Avviso(val eventoId: String, val titolo: String, val testo: String)

data class Piano(
    /** Promemoria da salvare e (ri)pianificare. */
    val daSalvare: List<Promemoria>,
    /** Identificativi da cancellare (promemoria e allarme). */
    val daTogliere: List<String>,
    val avvisi: List<Avviso>,
)

/**
 * Decide che cosa fare dei promemoria dopo un aggiornamento dei dati.
 * Logica pura, senza Android: è verificata dalle prove automatiche.
 *
 * - evento con nuovo orario: il promemoria si sposta e l'utente viene avvisato;
 * - evento rinviato o annullato: il promemoria si cancella e l'utente viene avvisato;
 * - evento non più presente nei dati: il promemoria resta com'era (la fonte
 *   potrebbe essere solo momentaneamente assente);
 * - evento ritrovato con un altro identificativo (spostato ad altro giorno):
 *   il promemoria passa al nuovo identificativo;
 * - preferiti con promemoria automatici: si aggiungono quelli mancanti e si
 *   tolgono quelli automatici che non corrispondono più a nulla di seguito.
 */
object PianoPromemoria {

    private val RAGGIO_RICERCA: Duration = Duration.ofDays(21)

    fun allarme(inizio: Instant, anticipoMinuti: Int): Instant = inizio.minus(Duration.ofMinutes(anticipoMinuti.toLong()))

    fun nuovo(e: Evento, anticipoMinuti: Int, automatico: Boolean) = Promemoria(
        eventoId = e.id,
        impronta = Archivio.impronta(e),
        titolo = e.titolo,
        inizio = e.inizio.toEpochMilli(),
        allarme = allarme(e.inizio, anticipoMinuti).toEpochMilli(),
        automatico = automatico,
    )

    fun sincronizza(
        promemoria: List<Promemoria>,
        eventi: List<Evento>,
        seguiti: List<Seguito>,
        anticipoMinuti: Int,
        automaticiAttivi: Boolean,
        adesso: Instant,
    ): Piano {
        val perId = eventi.associateBy { it.id }
        val daSalvare = mutableListOf<Promemoria>()
        val daTogliere = mutableListOf<String>()
        val avvisi = mutableListOf<Avviso>()
        val coperti = mutableSetOf<String>()

        for (p in promemoria) {
            val e = perId[p.eventoId] ?: ritrova(p, eventi)
            if (e == null) {
                // Dati momentaneamente assenti: si lascia tutto com'è.
                coperti += p.eventoId
                continue
            }
            if (p.automatico && (!automaticiAttivi || !Preferiti.corrisponde(e, seguiti))) {
                daTogliere += p.eventoId
                continue
            }
            when (e.stato) {
                StatoEvento.ANNULLATO, StatoEvento.RINVIATO -> {
                    daTogliere += p.eventoId
                    val parola = if (e.stato == StatoEvento.ANNULLATO) "annullato" else "rinviato"
                    if (!p.notificato) avvisi += Avviso(e.id, e.titolo, "L'evento risulta $parola: il promemoria è stato cancellato.")
                    continue
                }
                else -> Unit
            }
            coperti += e.id
            val nuovoInizio = e.inizio.toEpochMilli()
            val cambiato = nuovoInizio != p.inizio || e.id != p.eventoId
            if (!cambiato && p.allarme == allarme(e.inizio, anticipoMinuti).toEpochMilli()) continue
            if (e.id != p.eventoId) daTogliere += p.eventoId
            val aggiornato = p.copy(
                eventoId = e.id,
                impronta = Archivio.impronta(e),
                titolo = e.titolo,
                inizio = nuovoInizio,
                allarme = allarme(e.inizio, anticipoMinuti).toEpochMilli(),
                // Se l'evento è stato spostato più avanti, il promemoria torna a suonare.
                notificato = p.notificato && nuovoInizio <= p.inizio,
            )
            daSalvare += aggiornato
            if (nuovoInizio != p.inizio && e.inizio.isAfter(adesso)) {
                avvisi += Avviso(e.id, e.titolo, "Nuovo orario: il promemoria è stato spostato.")
            }
        }

        if (automaticiAttivi && seguiti.isNotEmpty()) {
            for (e in eventi) {
                if (e.id in coperti || !e.inizio.isAfter(adesso)) continue
                if (e.stato == StatoEvento.ANNULLATO || e.stato == StatoEvento.RINVIATO) continue
                if (Preferiti.corrisponde(e, seguiti)) {
                    daSalvare += nuovo(e, anticipoMinuti, automatico = true)
                    coperti += e.id
                }
            }
        }
        return Piano(daSalvare.distinctBy { it.eventoId }, daTogliere.distinct().filter { id -> daSalvare.none { it.eventoId == id } }, avvisi)
    }

    /** Stesso evento con un altro identificativo: stessa impronta, data vicina. */
    private fun ritrova(p: Promemoria, eventi: List<Evento>): Evento? {
        val vecchio = Instant.ofEpochMilli(p.inizio)
        return eventi.filter { Archivio.impronta(it) == p.impronta && Duration.between(vecchio, it.inizio).abs() <= RAGGIO_RICERCA }
            .minByOrNull { Duration.between(vecchio, it.inizio).abs() }
    }
}
