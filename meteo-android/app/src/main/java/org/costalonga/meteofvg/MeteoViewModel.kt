package org.costalonga.meteofvg

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.costalonga.meteofvg.data.Comune
import org.costalonga.meteofvg.data.Istantanea
import org.costalonga.meteofvg.data.MeteoNonDisponibile
import org.costalonga.meteofvg.data.MeteoRete
import org.costalonga.meteofvg.data.Preferenze
import org.costalonga.meteofvg.data.Previsione
import org.costalonga.meteofvg.data.comunePerSlug
import org.costalonga.meteofvg.widget.MeteoWidgetProvider
import java.io.IOException
import java.time.LocalDate

/** Ogni quanto la pagina si aggiorna da sola: quindici minuti, come sul sito. */
private const val RINFRESCO_MS = 15 * 60 * 1000L

class MeteoViewModel(applicazione: Application) : AndroidViewModel(applicazione) {

    var comune by mutableStateOf(Preferenze.comune(applicazione))
        private set

    var previsione by mutableStateOf<Previsione?>(null)
        private set

    var caricamento by mutableStateOf(false)
        private set

    var errore by mutableStateOf<String?>(null)
        private set

    var giornoScelto by mutableIntStateOf(0)
        private set

    private var lavoro: Job? = null

    /** L'app aggiorna da sola solo mentre è davvero sotto gli occhi. */
    var inPrimoPiano: Boolean = true

    init {
        carica()
        viewModelScope.launch {
            while (isActive) {
                delay(RINFRESCO_MS)
                if (inPrimoPiano) carica()
            }
        }
    }

    fun scegli(nuovo: Comune) {
        if (nuovo.nome == comune.nome) return
        comune = nuovo
        giornoScelto = 0
        previsione = null
        Preferenze.salvaComune(getApplication<Application>(), nuovo)
        carica()
    }

    /** Apre l'app sul Comune indicato da un collegamento della pagina. */
    fun scegliDaSlug(slug: String?) {
        val trovato = comunePerSlug(slug) ?: return
        scegli(trovato)
    }

    fun giorno(indice: Int) {
        giornoScelto = indice
    }

    fun carica() {
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            caricamento = true
            errore = null
            try {
                val richiesto = comune
                val esito = MeteoRete.previsione(richiesto)
                // Se nel frattempo il Comune è cambiato, questo esito non serve più.
                if (richiesto.nome == comune.nome) {
                    previsione = esito
                    salvaPerIlWidget(esito)
                }
            } catch (e: MeteoNonDisponibile) {
                errore = "Open-Meteo non sta fornendo le previsioni: ${e.motivo}."
            } catch (e: IOException) {
                errore = "Non è stato possibile leggere le previsioni. " +
                    "Controlla il collegamento a internet e riprova."
            } catch (e: Exception) {
                errore = "La risposta di Open-Meteo non è stata compresa. Riprova fra poco."
            } finally {
                caricamento = false
            }
        }
    }

    /** Aggiorna se il dato in mano ha più di un quarto d'ora. */
    fun aggiornaSeVecchia() {
        val attuale = previsione
        if (attuale == null || System.currentTimeMillis() - attuale.aggiornato > RINFRESCO_MS) {
            carica()
        }
    }

    /**
     * Il widget legge quello che l'app ha appena scaricato: cosi' appena si
     * apre l'app il widget risulta aggiornato, senza una seconda chiamata.
     */
    private fun salvaPerIlWidget(esito: Previsione) {
        val oggi = esito.giorni.firstOrNull { it.data == LocalDate.now() } ?: esito.giorni.firstOrNull()
        Preferenze.salvaIstantanea(
            getApplication<Application>(),
            Istantanea(
                comune = esito.comune,
                temperatura = esito.temperatura,
                codice = esito.codice,
                diGiorno = esito.diGiorno,
                minima = oggi?.minima ?: 0.0,
                massima = oggi?.massima ?: 0.0,
                pioggia = oggi?.pioggia ?: 0,
                quando = esito.aggiornato,
            ),
        )
        MeteoWidgetProvider.aggiornaTutti(getApplication<Application>())
    }
}
