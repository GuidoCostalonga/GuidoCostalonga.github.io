package org.costalonga.sportintv.dati

import android.content.Context

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.costalonga.sportintv.BuildConfig

private val Context.datastore by preferencesDataStore(name = "impostazioni")

enum class Tema(val etichetta: String) { SISTEMA("Come il telefono"), CHIARO("Chiaro"), SCURO("Scuro") }

enum class Origine(val etichetta: String, val spiegazione: String) {
    AUTOMATICA("Automatica", "Servizio online; se non risponde o è fermo da oltre 12 ore, l'app legge direttamente le fonti"),
    SERVIZIO("Solo servizio online", "Meno traffico e batteria; senza servizio l'app mostra gli ultimi dati salvati"),
    DIRETTA("Solo fonti dirette", "L'app interroga da sé ogni fonte: circa cento richieste per aggiornamento"),
}

data class Preferenze(
    val anticipoMinuti: Int = 15,
    val promemoriaPreferiti: Boolean = false,
    val tema: Tema = Tema.SISTEMA,
    val coloriDinamici: Boolean = false,
    val origine: Origine = Origine.AUTOMATICA,
    val urlServizio: String = BuildConfig.URL_SERVIZIO,
    val aggiornamentoAutomatico: Boolean = true,
)

/** Momento e provenienza dell'ultimo aggiornamento riuscito. */
data class StatoAggiornamento(
    /** Quando il telefono ha ricevuto i dati. */
    val ricevuto: Long? = null,
    /** Quando i dati sono stati raccolti dalle fonti (può essere prima, se arrivano dal servizio). */
    val generato: Long? = null,
    val provenienza: String? = null,
    val ultimoTentativo: Long? = null,
    val ultimoErrore: String? = null,
)

class Impostazioni(private val context: Context) {
    private object K {
        val anticipo = intPreferencesKey("anticipo_minuti")
        val promemoriaPreferiti = booleanPreferencesKey("promemoria_preferiti")
        val tema = stringPreferencesKey("tema")
        val dinamici = booleanPreferencesKey("colori_dinamici")
        val origine = stringPreferencesKey("origine")
        val url = stringPreferencesKey("url_servizio")
        val automatico = booleanPreferencesKey("aggiornamento_automatico")
        val ricevuto = longPreferencesKey("ricevuto")
        val generato = longPreferencesKey("generato")
        val provenienza = stringPreferencesKey("provenienza")
        val tentativo = longPreferencesKey("ultimo_tentativo")
        val errore = stringPreferencesKey("ultimo_errore")
    }

    val preferenze: Flow<Preferenze> = context.datastore.data.map { p ->
        Preferenze(
            anticipoMinuti = p[K.anticipo] ?: 15,
            promemoriaPreferiti = p[K.promemoriaPreferiti] ?: false,
            tema = p[K.tema]?.let { runCatching { Tema.valueOf(it) }.getOrNull() } ?: Tema.SISTEMA,
            coloriDinamici = p[K.dinamici] ?: false,
            origine = p[K.origine]?.let { runCatching { Origine.valueOf(it) }.getOrNull() } ?: Origine.AUTOMATICA,
            urlServizio = p[K.url]?.takeIf { it.isNotBlank() } ?: BuildConfig.URL_SERVIZIO,
            aggiornamentoAutomatico = p[K.automatico] ?: true,
        )
    }

    val stato: Flow<StatoAggiornamento> = context.datastore.data.map { p ->
        StatoAggiornamento(p[K.ricevuto], p[K.generato], p[K.provenienza], p[K.tentativo], p[K.errore])
    }

    suspend fun attuali(): Preferenze = preferenze.first()
    suspend fun statoAttuale(): StatoAggiornamento = stato.first()

    private suspend fun scrivi(blocco: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.datastore.edit { blocco(it) }
    }

    suspend fun anticipo(minuti: Int) = scrivi { it[K.anticipo] = minuti }
    suspend fun promemoriaPreferiti(attivo: Boolean) = scrivi { it[K.promemoriaPreferiti] = attivo }
    suspend fun tema(t: Tema) = scrivi { it[K.tema] = t.name }
    suspend fun coloriDinamici(attivi: Boolean) = scrivi { it[K.dinamici] = attivi }
    suspend fun origine(o: Origine) = scrivi { it[K.origine] = o.name }
    suspend fun urlServizio(url: String) = scrivi { if (url.isBlank()) it.remove(K.url) else it[K.url] = url.trim() }
    suspend fun aggiornamentoAutomatico(attivo: Boolean) = scrivi { it[K.automatico] = attivo }

    suspend fun riuscito(ricevuto: Long, generato: Long, provenienza: String) = scrivi {
        it[K.ricevuto] = ricevuto
        it[K.generato] = generato
        it[K.provenienza] = provenienza
        it[K.tentativo] = ricevuto
        it.remove(K.errore)
    }

    suspend fun fallito(quando: Long, errore: String) = scrivi {
        it[K.tentativo] = quando
        it[K.errore] = errore
    }

}
