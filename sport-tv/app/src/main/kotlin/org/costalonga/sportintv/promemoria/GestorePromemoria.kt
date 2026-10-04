package org.costalonga.sportintv.promemoria

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.costalonga.sportintv.dati.Archivio
import org.costalonga.sportintv.dati.Impostazioni
import org.costalonga.sportintv.dati.SportDatabase
import org.costalonga.sportintv.raccolta.Evento
import java.time.Instant

/** Collega la logica dei promemoria al database e agli allarmi di Android. */
class GestorePromemoria(
    private val context: Context,
    private val db: SportDatabase,
    private val archivio: Archivio,
    private val impostazioni: Impostazioni,
) {
    private val pianificatore = Pianificatore(context)
    private val mutex = Mutex()

    suspend fun attiva(e: Evento) = mutex.withLock {
        val p = PianoPromemoria.nuovo(e, impostazioni.attuali().anticipoMinuti, automatico = false)
        db.preferiti().salvaPromemoria(p)
        pianificatore.pianifica(p)
    }

    suspend fun disattiva(eventoId: String) = mutex.withLock {
        db.preferiti().togliPromemoria(eventoId)
        pianificatore.cancella(eventoId)
    }

    /** Dopo ogni aggiornamento dei dati o cambio di preferiti e impostazioni. */
    suspend fun sincronizza() = mutex.withLock {
        val pref = impostazioni.attuali()
        val piano = PianoPromemoria.sincronizza(
            promemoria = db.preferiti().elencoPromemoria(),
            eventi = archivio.eventiSalvati(),
            seguiti = db.preferiti().elencoSeguiti(),
            anticipoMinuti = pref.anticipoMinuti,
            automaticiAttivi = pref.promemoriaPreferiti,
            adesso = Instant.now(),
        )
        for (id in piano.daTogliere) {
            db.preferiti().togliPromemoria(id)
            pianificatore.cancella(id)
        }
        for (p in piano.daSalvare) {
            db.preferiti().salvaPromemoria(p)
            pianificatore.pianifica(p)
        }
        for (a in piano.avvisi) Pianificatore.mostra(context, a.eventoId, a.titolo, a.testo)
    }

    suspend fun ripianificaTutti() = mutex.withLock {
        val adesso = Instant.now()
        for (p in db.preferiti().elencoPromemoria()) {
            // Promemoria di eventi ormai passati: si tolgono.
            if (p.inizio < adesso.minusSeconds(6 * 3600).toEpochMilli()) {
                db.preferiti().togliPromemoria(p.eventoId)
                pianificatore.cancella(p.eventoId)
            } else {
                pianificatore.pianifica(p, adesso)
            }
        }
    }
}
