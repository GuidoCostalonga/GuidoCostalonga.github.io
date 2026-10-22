package org.costalonga.fattidelgiorno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.costalonga.fattidelgiorno.FattiApp
import org.costalonga.fattidelgiorno.ai.Gemini
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.RisultatoRicerca
import org.costalonga.fattidelgiorno.generazione.GeneratoreBozza
import org.costalonga.fattidelgiorno.generazione.GeneratorePrompt
import org.costalonga.fattidelgiorno.generazione.OpzioniContenuto
import org.costalonga.fattidelgiorno.sicurezza.ChiaveSegreta
import java.time.LocalDate

data class StatoSchermata(
    val data: LocalDate,
    val caricamento: Boolean = false,
    val risultato: RisultatoRicerca? = null,
    val erroreGrave: String? = null,
    val areeAttive: Set<Area> = Area.entries.toSet(),
    val categorieAttive: Set<Categoria> = Categoria.entries.toSet(),
    val fattoScelto: Fatto? = null,
    val opzioni: OpzioniContenuto = OpzioniContenuto(),
    val bozza: String = "",
    val prompt: String = "",
    val generazioneAi: Boolean = false,
    val messaggioAi: String? = null,
    val geminiConfigurato: Boolean = false,
) {
    val fattiFiltrati: List<Fatto>
        get() = risultato?.fatti?.filter { it.area in areeAttive && it.categoria in categorieAttive }.orEmpty()
}

class VistaModello(private val app: FattiApp) : ViewModel() {

    private val _stato = MutableStateFlow(
        StatoSchermata(
            data = Date.oggi(),
            opzioni = OpzioniContenuto(piattaforma = app.impostazioni.piattaformaPreferita).coerente(),
            geminiConfigurato = app.chiavi.presente(ChiaveSegreta.GEMINI),
        ),
    )
    val stato: StateFlow<StatoSchermata> = _stato

    private var ricerca: Job? = null

    init {
        cerca()
    }

    fun scegliData(data: LocalDate) {
        if (data == _stato.value.data) return
        _stato.update { it.copy(data = data, fattoScelto = null, bozza = "", prompt = "", messaggioAi = null) }
        cerca()
    }

    fun oggi() = scegliData(Date.oggi())

    fun cerca(forza: Boolean = false) {
        ricerca?.cancel()
        val data = _stato.value.data
        _stato.update { it.copy(caricamento = true, erroreGrave = null) }
        ricerca = viewModelScope.launch {
            val esito = runCatching { app.repository().cerca(data.dayOfMonth, data.monthValue, forza) }
            _stato.update { s ->
                esito.fold(
                    onSuccess = { r -> s.copy(caricamento = false, risultato = r) },
                    onFailure = { e -> s.copy(caricamento = false, erroreGrave = "Ricerca non riuscita: ${e.message ?: e::class.simpleName}") },
                )
            }
        }
    }

    fun alternaArea(area: Area) = _stato.update { s ->
        val nuove = if (area in s.areeAttive) s.areeAttive - area else s.areeAttive + area
        s.copy(areeAttive = nuove)
    }

    fun alternaCategoria(categoria: Categoria) = _stato.update { s ->
        val nuove = if (categoria in s.categorieAttive) s.categorieAttive - categoria else s.categorieAttive + categoria
        s.copy(categorieAttive = nuove)
    }

    fun tutteLeCategorie() = _stato.update { it.copy(categorieAttive = Categoria.entries.toSet()) }

    fun scegliFatto(fatto: Fatto) {
        _stato.update { it.copy(fattoScelto = fatto, messaggioAi = null) }
        rigenera()
    }

    fun aggiornaOpzioni(trasforma: (OpzioniContenuto) -> OpzioniContenuto) {
        _stato.update { it.copy(opzioni = trasforma(it.opzioni).coerente()) }
        app.impostazioni.piattaformaPreferita = _stato.value.opzioni.piattaforma
        rigenera()
    }

    fun rigenera() {
        val s = _stato.value
        val fatto = s.fattoScelto ?: return
        val bozza = GeneratoreBozza.genera(fatto, s.opzioni)
        val prompt = GeneratorePrompt.genera(fatto, s.opzioni, bozza)
        _stato.update { it.copy(bozza = bozza, prompt = prompt, messaggioAi = null) }
    }

    fun modificaBozza(testo: String) = _stato.update { it.copy(bozza = testo) }

    fun modificaPrompt(testo: String) = _stato.update { it.copy(prompt = testo) }

    fun aggiornaStatoGemini() = _stato.update { it.copy(geminiConfigurato = app.chiavi.presente(ChiaveSegreta.GEMINI)) }

    /** Chiede a Gemini di rifinire la bozza a partire dal prompt. Solo con chiave personale. */
    fun generaConGemini() {
        val s = _stato.value
        val chiave = app.chiavi.leggi(ChiaveSegreta.GEMINI)
        if (chiave.isNullOrBlank()) {
            _stato.update { it.copy(messaggioAi = "Nessuna chiave Gemini configurata: vai nelle impostazioni") }
            return
        }
        if (s.prompt.isBlank()) return
        if (!app.rete.online()) {
            _stato.update { it.copy(messaggioAi = "Serve una connessione per usare Gemini") }
            return
        }
        _stato.update { it.copy(generazioneAi = true, messaggioAi = null) }
        viewModelScope.launch {
            val esito = app.gemini.genera(chiave, app.impostazioni.modelloGemini, s.prompt)
            _stato.update { st ->
                when (esito) {
                    is Gemini.Esito.Ok -> st.copy(generazioneAi = false, bozza = esito.testo, messaggioAi = "Bozza rifinita da Gemini: rileggi e controlla i fatti prima di pubblicare")
                    is Gemini.Esito.Limite -> st.copy(generazioneAi = false, messaggioAi = esito.messaggio)
                    is Gemini.Esito.Errore -> st.copy(generazioneAi = false, messaggioAi = "Gemini non ha risposto: ${esito.messaggio}")
                }
            }
        }
    }
}
