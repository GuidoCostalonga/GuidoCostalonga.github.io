package org.costalonga.polso.dati

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.costalonga.polso.motore.ConfigNotifiche
import org.costalonga.polso.motore.MetodoZone
import org.costalonga.polso.motore.Preferenze
import java.time.ZoneId

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

enum class ProfiloIa(val nome: String, val descrizione: String, val repo: String, val file: String, val byte: Long, val sha256: String) {
    LEGGERO("Leggero · Qwen3 0,6B", "614 MB su disco, circa 1,5 GB di memoria. Veloce, italiano essenziale.",
        "litert-community/Qwen3-0.6B", "Qwen3-0.6B.litertlm", 614_236_160L, "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"),
    EQUILIBRATO("Equilibrato · Qwen2.5 1,5B", "1,6 GB su disco, circa 3 GB di memoria.",
        "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", 1_597_931_520L, "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"),
    QUALITA("Qualità · Gemma 4 E2B", "2,6 GB su disco, circa 4 GB di memoria. Italiano migliore; consigliato sul Magic7 Pro.",
        "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_588_147_712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"),
    ;

    val url: String get() = "https://huggingface.co/$repo/resolve/main/$file"
    val licenza: String get() = "Apache 2.0 (scheda del modello su huggingface.co/$repo)"
}

@Serializable
data class Impostazioni(
    val zona: String = "Europe/Rome",
    val obiettivoPassi: Int = 8000,
    val obiettivoSonnoMin: Int = 450,
    val annoNascita: Int? = null,
    val fcMax: Int? = null,
    val fcRiposo: Int? = null,
    val metodoZone: String = MetodoZone.PERCENTUALE_FCMAX.name,
    val prioritaOrigini: List<String> = listOf("com.hihonor.health"),
    val notifiche: ConfigNotifiche = ConfigNotifiche(),
    val sincronizzazioneAutomatica: Boolean = true,
    val oreSincronizzazione: Int = 6,
    val profiloIa: String? = null,
    val onlineAttivo: Boolean = false,
    val onlineIndirizzo: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    val onlineModello: String = "gemini-2.5-flash",
    val bloccoBiometrico: Boolean = false,
    val tema: String = "sistema",
    val ordineSchede: List<String> = listOf("oggi", "passi", "sonno", "cuore", "allenamenti", "emerge", "calendario", "qualita"),
    val modalitaDemo: Boolean = false,
    val primoAvvioFatto: Boolean = false,
    /** Token delle modifiche di Health Connect per tipo di dato. */
    val tokenHc: Map<String, String> = emptyMap(),
    val primaSincronizzazioneHc: Long? = null,
) {
    fun preferenze(): Preferenze = Preferenze(
        zona = runCatching { ZoneId.of(zona) }.getOrDefault(ZoneId.of("Europe/Rome")),
        prioritaOrigini = prioritaOrigini, obiettivoPassi = obiettivoPassi, obiettivoSonnoMin = obiettivoSonnoMin,
        annoNascita = annoNascita, fcMaxManuale = fcMax, fcRiposoManuale = fcRiposo,
        metodoZone = runCatching { MetodoZone.valueOf(metodoZone) }.getOrDefault(MetodoZone.PERCENTUALE_FCMAX),
    )
}

/** Impostazioni in DataStore, salvate come un unico JSON versionato dal suo schema. */
class ArchivioImpostazioni(private val context: Context) {
    private val chiave = stringPreferencesKey("impostazioni_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flusso: Flow<Impostazioni> = context.store.data.map { p -> p[chiave]?.let { runCatching { json.decodeFromString(Impostazioni.serializer(), it) }.getOrNull() } ?: Impostazioni() }

    suspend fun attuali(): Impostazioni = flusso.first()

    suspend fun modifica(f: (Impostazioni) -> Impostazioni) {
        context.store.edit { p ->
            val ora = p[chiave]?.let { runCatching { json.decodeFromString(Impostazioni.serializer(), it) }.getOrNull() } ?: Impostazioni()
            p[chiave] = json.encodeToString(Impostazioni.serializer(), f(ora))
        }
    }

    /** Per il backup: le impostazioni senza i token della fonte (validi solo su questo telefono). */
    suspend fun perBackup(): Map<String, String> = mapOf("impostazioni" to json.encodeToString(Impostazioni.serializer(), attuali().copy(tokenHc = emptyMap(), primaSincronizzazioneHc = null)))

    suspend fun ripristina(m: Map<String, String>) {
        val testo = m["impostazioni"] ?: return
        val i = runCatching { json.decodeFromString(Impostazioni.serializer(), testo) }.getOrNull() ?: return
        modifica { ora -> i.copy(tokenHc = ora.tokenHc, primaSincronizzazioneHc = ora.primaSincronizzazioneHc, modalitaDemo = false) }
    }
}
