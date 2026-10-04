package org.costalonga.sportintv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.costalonga.sportintv.SportApp
import org.costalonga.sportintv.dati.EsitoAggiornamento
import org.costalonga.sportintv.dati.Filtri
import org.costalonga.sportintv.dati.Istantanea
import org.costalonga.sportintv.dati.Origine
import org.costalonga.sportintv.dati.Preferenze
import org.costalonga.sportintv.dati.Salvato
import org.costalonga.sportintv.dati.Seguito
import org.costalonga.sportintv.dati.StatoAggiornamento
import org.costalonga.sportintv.dati.Tema
import org.costalonga.sportintv.dati.TipoSeguito
import org.costalonga.sportintv.dati.filtra
import org.costalonga.sportintv.dati.giorno
import org.costalonga.sportintv.dati.gruppoPiattaforma
import org.costalonga.sportintv.lavoro.LavoroAggiornamento
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.dati.Archivio
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Le due liste principali: trasmissioni confermate e trasmissioni da confermare. */
enum class Vista { CONFERMATI, DA_CONFERMARE }

data class OpzioniFiltri(
    val sport: List<String> = emptyList(),
    val competizioni: List<String> = emptyList(),
    val canali: List<String> = emptyList(),
    val piattaforme: List<String> = emptyList(),
    val giorni: List<LocalDate> = emptyList(),
)

class Modello(app: Application) : AndroidViewModel(app) {
    private val sportApp = app as SportApp
    private val archivio = sportApp.archivio
    private val impostazioni = sportApp.impostazioni
    private val prefDao = sportApp.db.preferiti()

    /** Orologio a minuti per "In corso" e per l'età dei dati. */
    val adesso: StateFlow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Instant.now())

    val istantanea: StateFlow<Istantanea?> = archivio.istantanea
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferenze: StateFlow<Preferenze> = impostazioni.preferenze.stateIn(viewModelScope, SharingStarted.Eagerly, Preferenze())
    val statoAggiornamento: StateFlow<StatoAggiornamento> = impostazioni.stato.stateIn(viewModelScope, SharingStarted.Eagerly, StatoAggiornamento())

    val seguiti: StateFlow<List<Seguito>> = prefDao.seguiti().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val salvati: StateFlow<List<Salvato>> = prefDao.salvati().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val promemoria: StateFlow<Set<String>> = prefDao.promemoria().map { l -> l.map { it.eventoId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _filtri = mutableMapOf(Vista.CONFERMATI to MutableStateFlow(Filtri()), Vista.DA_CONFERMARE to MutableStateFlow(Filtri()))
    fun filtri(v: Vista): StateFlow<Filtri> = _filtri.getValue(v)
    fun impostaFiltri(v: Vista, f: Filtri) { _filtri.getValue(v).value = f }

    val inAggiornamento = MutableStateFlow(false)
    val messaggio = MutableStateFlow<String?>(null)

    private val cacheOpzioni by lazy { Vista.entries.associateWith { creaOpzioni(it) } }
    private val cacheRisultati by lazy { Vista.entries.associateWith { creaRisultati(it) } }
    fun opzioni(v: Vista): StateFlow<OpzioniFiltri> = cacheOpzioni.getValue(v)
    fun risultati(v: Vista): StateFlow<List<Evento>?> = cacheRisultati.getValue(v)

    private fun creaOpzioni(v: Vista): StateFlow<OpzioniFiltri> = istantanea.map { i ->
        val eventi = i?.eventi.orEmpty().filter { (v == Vista.CONFERMATI) == it.trasmissioneVerificata }
        OpzioniFiltri(
            sport = eventi.map { it.sport }.distinct().sortedBy { nomeSport(it) },
            competizioni = eventi.mapNotNull { it.competizione }.distinct().sorted(),
            canali = eventi.flatMap { e -> e.trasmissioni.map { it.canale } }.distinct().sorted(),
            piattaforme = eventi.flatMap { e -> e.trasmissioni.map { it.gruppoPiattaforma } }.distinct().sorted(),
            giorni = eventi.map { it.giorno() }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpzioniFiltri())

    private fun creaRisultati(v: Vista): StateFlow<List<Evento>?> = combine(istantanea, filtri(v), seguiti, salvati, adesso) { i, f, seg, sal, ora ->
        i?.eventi?.filter { (v == Vista.CONFERMATI) == it.trasmissioneVerificata }
            ?.filtra(f, seg, sal.map { it.eventoId }.toSet(), ora)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun evento(id: String) = archivio.evento(id)

    init {
        // All'apertura: aggiorna se non ci sono dati o se sono più vecchi di 3 ore.
        viewModelScope.launch {
            val s = impostazioni.statoAttuale()
            val ultimo = s.ricevuto?.let { Instant.ofEpochMilli(it) }
            if (ultimo == null || Duration.between(ultimo, Instant.now()) > Duration.ofHours(3)) aggiorna(silenzioso = true)
        }
    }

    fun aggiorna(silenzioso: Boolean = false) {
        if (inAggiornamento.value) return
        viewModelScope.launch {
            inAggiornamento.value = true
            try {
                when (val esito = archivio.aggiorna()) {
                    is EsitoAggiornamento.Riuscito -> {
                        sportApp.promemoria.sincronizza()
                        if (!silenzioso || esito.fontiInDifficolta.isNotEmpty()) {
                            messaggio.value = if (esito.fontiInDifficolta.isEmpty()) {
                                "Dati aggiornati: ${esito.eventi} eventi"
                            } else {
                                "Aggiornato, ma con problemi su: ${esito.fontiInDifficolta.joinToString()}"
                            }
                        }
                    }
                    is EsitoAggiornamento.Fallito -> messaggio.value = "Aggiornamento non riuscito: restano i dati salvati sul telefono"
                }
            } finally {
                inAggiornamento.value = false
            }
        }
    }

    // ---------------------------------------------------------------- preferiti

    fun segui(tipo: TipoSeguito, nome: String) = viewModelScope.launch {
        if (nome.isBlank()) return@launch
        prefDao.segui(Seguito(tipo, nome.trim()))
        sportApp.promemoria.sincronizza()
        messaggio.value = "Ora segui ${nome.trim()}"
    }

    fun smetti(s: Seguito) = viewModelScope.launch {
        prefDao.smetti(s.tipo, s.nome)
        sportApp.promemoria.sincronizza()
    }

    fun salvaEvento(e: Evento, salva: Boolean) = viewModelScope.launch {
        if (salva) prefDao.salva(Salvato(e.id, Archivio.impronta(e), e.titolo, e.inizio.toEpochMilli()))
        else prefDao.togliSalvato(e.id)
    }

    fun promemoria(e: Evento, attivo: Boolean) = viewModelScope.launch {
        if (attivo) {
            sportApp.promemoria.attiva(e)
            messaggio.value = "Promemoria attivato: ${preferenze.value.anticipoMinuti} minuti prima"
        } else {
            sportApp.promemoria.disattiva(e.id)
        }
    }

    fun togliSalvato(id: String) = viewModelScope.launch { prefDao.togliSalvato(id) }

    // ------------------------------------------------------------- impostazioni

    fun anticipo(minuti: Int) = viewModelScope.launch {
        impostazioni.anticipo(minuti)
        sportApp.promemoria.sincronizza()
    }

    fun promemoriaPreferiti(attivo: Boolean) = viewModelScope.launch {
        impostazioni.promemoriaPreferiti(attivo)
        sportApp.promemoria.sincronizza()
    }

    fun tema(t: Tema) = viewModelScope.launch { impostazioni.tema(t) }
    fun coloriDinamici(a: Boolean) = viewModelScope.launch { impostazioni.coloriDinamici(a) }
    fun origine(o: Origine) = viewModelScope.launch { impostazioni.origine(o) }
    fun urlServizio(u: String) = viewModelScope.launch { impostazioni.urlServizio(u) }

    fun aggiornamentoAutomatico(a: Boolean) = viewModelScope.launch {
        impostazioni.aggiornamentoAutomatico(a)
        LavoroAggiornamento.programma(getApplication(), a)
    }
}
