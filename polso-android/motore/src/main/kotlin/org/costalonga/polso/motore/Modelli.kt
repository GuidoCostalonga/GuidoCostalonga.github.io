package org.costalonga.polso.motore

import kotlinx.serialization.Serializable

/**
 * Come si combinano più valori della stessa metrica nello stesso giorno.
 * SOMMA vale solo per quantità che la fonte fornisce come incrementi su un
 * intervallo (passi, distanza, calorie): mai per contatori cumulativi, che
 * vanno prima convertiti in incrementi (vedi [Cumulativi]).
 */
enum class Combinazione { SOMMA, MEDIA, ULTIMO }

enum class Natura {
    /** Quantità su un intervallo [inizio, fine): si può ripartire fra i giorni. */
    INTERVALLO,
    /** Misura in un istante. */
    ISTANTANEA,
}

/**
 * Le grandezze che l'app sa conservare. La presenza nell'elenco non significa
 * che lo smartwatch le misuri o che la fonte le condivida: lo dice la matrice
 * di compatibilità e, sul telefono, la pagina Fonti dati.
 */
enum class Metrica(
    val codice: String,
    val nome: String,
    val unita: String,
    val natura: Natura,
    val combinazione: Combinazione,
    /** Intervallo di valori plausibili: fuori da qui il dato è "sospetto". */
    val minimoPlausibile: Double,
    val massimoPlausibile: Double,
    /** Punteggio proprietario della fonte, non una grandezza fisica. */
    val proprietaria: Boolean = false,
    val decimali: Int = 0,
) {
    PASSI("passi", "Passi", "passi", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 100_000.0),
    DISTANZA("distanza", "Distanza", "m", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 300_000.0),
    CALORIE_ATTIVE("calorie_attive", "Calorie attive", "kcal", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 10_000.0),
    CALORIE_TOTALI("calorie_totali", "Calorie totali", "kcal", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 15_000.0),
    MINUTI_ATTIVI("minuti_attivi", "Minuti attivi", "min", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 1_440.0),
    PIANI("piani", "Piani saliti", "piani", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 500.0),
    DISLIVELLO("dislivello", "Dislivello positivo", "m", Natura.INTERVALLO, Combinazione.SOMMA, 0.0, 10_000.0),
    FREQUENZA_CARDIACA("fc", "Frequenza cardiaca", "bpm", Natura.ISTANTANEA, Combinazione.MEDIA, 25.0, 230.0),
    FC_RIPOSO("fc_riposo", "Frequenza a riposo (fonte)", "bpm", Natura.ISTANTANEA, Combinazione.ULTIMO, 25.0, 130.0),
    HRV_RMSSD("hrv_rmssd", "HRV (RMSSD, fonte)", "ms", Natura.ISTANTANEA, Combinazione.MEDIA, 1.0, 300.0),
    SPO2("spo2", "Saturazione (SpO₂)", "%", Natura.ISTANTANEA, Combinazione.MEDIA, 70.0, 100.0, decimali = 1),
    RESPIRAZIONE("respirazione", "Frequenza respiratoria", "atti/min", Natura.ISTANTANEA, Combinazione.MEDIA, 4.0, 60.0, decimali = 1),
    TEMPERATURA_CUTANEA("temp_cute_delta", "Temperatura cutanea (variazione)", "°C", Natura.ISTANTANEA, Combinazione.MEDIA, -5.0, 5.0, decimali = 2),
    TEMPERATURA_CORPOREA("temp_corpo", "Temperatura corporea", "°C", Natura.ISTANTANEA, Combinazione.MEDIA, 33.0, 43.0, decimali = 1),
    STRESS("stress", "Stress (punteggio della fonte)", "punti", Natura.ISTANTANEA, Combinazione.MEDIA, 0.0, 100.0, proprietaria = true),
    VO2MAX("vo2max", "VO₂max (stima della fonte)", "ml/kg/min", Natura.ISTANTANEA, Combinazione.ULTIMO, 10.0, 95.0, proprietaria = true, decimali = 1),
    PESO("peso", "Peso", "kg", Natura.ISTANTANEA, Combinazione.ULTIMO, 20.0, 350.0, decimali = 1),
    GRASSO("grasso", "Massa grassa", "%", Natura.ISTANTANEA, Combinazione.ULTIMO, 2.0, 75.0, decimali = 1),
    PRESSIONE_SISTOLICA("pa_sistolica", "Pressione sistolica", "mmHg", Natura.ISTANTANEA, Combinazione.MEDIA, 60.0, 260.0),
    PRESSIONE_DIASTOLICA("pa_diastolica", "Pressione diastolica", "mmHg", Natura.ISTANTANEA, Combinazione.MEDIA, 30.0, 160.0),
    ;

    fun plausibile(v: Double): Boolean = !v.isNaN() && v >= minimoPlausibile && v <= massimoPlausibile

    companion object {
        private val perCodice = entries.associateBy { it.codice }
        fun daCodice(c: String): Metrica? = perCodice[c.trim().lowercase()]
    }
}

/** Una misura originale, così come arriva dalla fonte (o dall'utente). */
@Serializable
data class Misura(
    val metrica: String,
    /** Millisecondi dall'epoca, UTC. */
    val inizio: Long,
    /** Uguale a [inizio] per le misure istantanee. */
    val fine: Long,
    val valore: Double,
    val unita: String,
    /** Scarto dal tempo universale in secondi, se la fonte lo indica. */
    val scartoSec: Int? = null,
    /** Fonte di acquisizione: health_connect, file, manuale, demo. */
    val fonte: String,
    /** Applicazione o dispositivo d'origine (es. com.hihonor.health). */
    val origine: String = "",
    val dispositivo: String = "",
    /** Identificativo della fonte, se esiste: serve alla deduplicazione. */
    val idEsterno: String? = null,
    val modificataIl: Long? = null,
) {
    val metricaEnum: Metrica? get() = Metrica.daCodice(metrica)

    /**
     * Chiave stabile per riconoscere la stessa misura importata due volte.
     * Con identificativo della fonte si usa quello; altrimenti il contenuto.
     */
    fun chiave(): String = if (!idEsterno.isNullOrBlank()) "$fonte|$metrica|$idEsterno"
    else "$fonte|$origine|$metrica|$inizio|$fine|${valore.toBits()}"
}

enum class FaseSonno(val codice: String, val nome: String, val dormendo: Boolean) {
    SVEGLIO("sveglio", "Sveglio", false),
    FUORI_LETTO("fuori_letto", "Fuori dal letto", false),
    SONNO("sonno", "Sonno (fase non indicata)", true),
    LEGGERO("leggero", "Sonno leggero", true),
    PROFONDO("profondo", "Sonno profondo", true),
    REM("rem", "REM", true),
    SCONOSCIUTO("sconosciuto", "Non classificato", false),
    ;

    companion object {
        fun daCodice(c: String): FaseSonno = entries.firstOrNull { it.codice == c } ?: SCONOSCIUTO
    }
}

@Serializable
data class IntervalloSonno(val inizio: Long, val fine: Long, val fase: String)

@Serializable
data class SessioneSonno(
    val inizio: Long,
    val fine: Long,
    val scartoSec: Int? = null,
    val fonte: String,
    val origine: String = "",
    val idEsterno: String? = null,
    val fasi: List<IntervalloSonno> = emptyList(),
    val note: String = "",
) {
    fun chiave(): String = if (!idEsterno.isNullOrBlank()) "$fonte|sonno|$idEsterno" else "$fonte|$origine|sonno|$inizio|$fine"
}

@Serializable
data class PuntoPercorso(
    val istante: Long,
    val lat: Double,
    val lon: Double,
    val altitudine: Double? = null,
)

@Serializable
data class Allenamento(
    val inizio: Long,
    val fine: Long,
    val scartoSec: Int? = null,
    /** Codice dello sport (vedi [Sport]). */
    val sport: String,
    val titolo: String = "",
    val fonte: String,
    val origine: String = "",
    val idEsterno: String? = null,
    /** Valori forniti dalla fonte; null se assenti (mai zero per "mancante"). */
    val distanzaM: Double? = null,
    val calorieKcal: Double? = null,
    val dislivelloM: Double? = null,
    val passi: Double? = null,
    val fcMedia: Double? = null,
    val fcMax: Double? = null,
    val percorso: List<PuntoPercorso> = emptyList(),
) {
    val durataMs: Long get() = fine - inizio
    fun chiave(): String = if (!idEsterno.isNullOrBlank()) "$fonte|allenamento|$idEsterno" else "$fonte|$origine|allenamento|$inizio|$fine"
}

object Sport {
    val nomi: Map<String, String> = linkedMapOf(
        "camminata" to "Camminata", "corsa" to "Corsa", "corsa_tapis" to "Corsa su tapis roulant",
        "ciclismo" to "Ciclismo", "cyclette" to "Cyclette", "nuoto_piscina" to "Nuoto in piscina",
        "nuoto_libero" to "Nuoto in acque libere", "escursionismo" to "Escursionismo", "ellittica" to "Ellittica",
        "vogatore" to "Vogatore", "forza" to "Allenamento di forza", "yoga" to "Yoga", "pilates" to "Pilates",
        "calcio" to "Calcio", "tennis" to "Tennis", "padel" to "Padel", "sci" to "Sci", "ballo" to "Ballo",
        "hiit" to "Allenamento intervallato", "altro" to "Altro",
    )

    fun nome(codice: String): String = nomi[codice] ?: codice.replaceFirstChar { it.uppercase() }

    /** Sport in cui distanza e passo hanno senso. */
    val conDistanza = setOf("camminata", "corsa", "corsa_tapis", "ciclismo", "nuoto_piscina", "nuoto_libero", "escursionismo", "sci")
}

/** Tipi di voci del diario manuale. Nessuna è obbligatoria. */
enum class TipoDiario(val codice: String, val nome: String, val unita: String?, val metricaCollegata: Metrica? = null) {
    PESO("peso", "Peso", "kg", Metrica.PESO),
    CIRCONFERENZA_VITA("vita", "Circonferenza vita", "cm"),
    CIRCONFERENZA_FIANCHI("fianchi", "Circonferenza fianchi", "cm"),
    PRESSIONE("pressione", "Pressione (dispositivo esterno)", "mmHg"),
    PASTO("pasto", "Alimentazione (sintesi)", null),
    ACQUA("acqua", "Idratazione", "ml"),
    CAFFEINA("caffeina", "Caffeina", "tazze"),
    ALCOL("alcol", "Alcol", "unità"),
    UMORE("umore", "Umore", "1-5"),
    ENERGIA("energia", "Energia", "1-5"),
    RIPOSO_PERCEPITO("riposo", "Riposo percepito", "1-5"),
    SINTOMO("sintomo", "Sintomo", null),
    EVENTO("evento", "Evento", null),
    FARMACO("farmaco", "Farmaco (diario)", null),
    NOTA("nota", "Nota", null),
    ;

    companion object {
        fun daCodice(c: String): TipoDiario? = entries.firstOrNull { it.codice == c }
    }
}

@Serializable
data class VoceDiario(
    val id: Long = 0,
    val tipo: String,
    val istante: Long,
    val valore: Double? = null,
    /** Secondo valore: la diastolica per la pressione. */
    val valore2: Double? = null,
    val testo: String = "",
    val creataIl: Long,
    val modificataIl: Long,
)
