package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import java.time.LocalDate

enum class Sezione(val nome: String) {
    ATTIVITA("Attività"), CUORE("Cuore"), SONNO("Sonno"), ALLENAMENTI("Allenamenti"),
    ALTRI("Altri parametri"), RELAZIONI("Relazioni"), INDICI("Indici descrittivi"), QUALITA("Qualità dei dati"),
}

/** Scheda tecnica di un'elaborazione: è anche la sua documentazione. */
data class Definizione(
    val id: String,
    val titolo: String,
    val sezione: Sezione,
    val datiRichiesti: String,
    val metodo: String,
    val unita: String,
    val minimo: String,
    val mancanti: String,
    val limiti: String,
    /** Nome della prova automatica che la verifica. */
    val prova: String,
)

/** Distingue la misura della fonte dal valore calcolato dall'app. */
enum class Provenienza(val etichetta: String) { FONTE("Misura della fonte"), CALCOLATO("Valore calcolato") }

data class Voce(
    val etichetta: String,
    val testo: String,
    val valore: Double? = null,
    val unita: String = "",
    val provenienza: Provenienza = Provenienza.CALCOLATO,
)

data class Copertura(
    val giorniConDati: Int,
    val giorniNelPeriodo: Int,
    val primo: LocalDate?,
    val ultimo: LocalDate?,
    val sospetti: Int = 0,
    val nota: String? = null,
) {
    val percentuale: Double get() = if (giorniNelPeriodo == 0) 0.0 else 100.0 * giorniConDati / giorniNelPeriodo
}

/** Dati per i grafici: l'interfaccia li disegna senza ricalcolare nulla. */
sealed interface Grafico {
    val titolo: String
    val unita: String

    data class Linea(
        override val titolo: String,
        override val unita: String,
        val punti: List<Pair<LocalDate, Double?>>,
        val mediaMobile: List<Double?> = emptyList(),
        val riferimento: Double? = null,
        val etichettaRiferimento: String? = null,
    ) : Grafico

    data class Barre(
        override val titolo: String,
        override val unita: String,
        val barre: List<Pair<String, Double?>>,
        /** Parti impilate opzionali (es. fasi del sonno), stesse etichette delle barre. */
        val parti: List<Pair<String, List<Double?>>> = emptyList(),
        val riferimento: Double? = null,
    ) : Grafico

    data class Dispersione(
        override val titolo: String,
        override val unita: String,
        val etichettaX: String,
        val etichettaY: String,
        val punti: List<Pair<Double, Double>>,
    ) : Grafico

    data class Calendario(
        override val titolo: String,
        override val unita: String,
        val valori: Map<LocalDate, Double?>,
        val da: LocalDate,
        val a: LocalDate,
    ) : Grafico
}

sealed interface Esito {
    data class Disponibile(
        val voci: List<Voce>,
        val copertura: Copertura,
        val grafici: List<Grafico> = emptyList(),
        val note: List<String> = emptyList(),
        /** Valore principale normalizzato, usato da evidenze e IA. */
        val principale: Double? = null,
    ) : Esito

    data class NonDisponibile(val motivo: String, val mancano: List<String> = emptyList()) : Esito
}

/** Contesto di un calcolo: dati, periodo e giorno di riferimento. */
data class Contesto(val dati: Dati, val periodo: Periodo, val oggi: LocalDate) {
    /** Giorni del periodo già trascorsi: i giorni futuri non sono "mancanti". */
    val giorni: List<LocalDate> get() = periodo.giorniTrascorsi(oggi)
    val parziale: Boolean get() = periodo.parziale(oggi)

    fun copertura(m: Metrica): Copertura {
        val s = dati.serie(m)
        val con = giorni.filter { s[it]?.valore != null }
        return Copertura(
            giorniConDati = con.size, giorniNelPeriodo = giorni.size,
            primo = con.firstOrNull(), ultimo = con.lastOrNull(),
            sospetti = giorni.sumOf { s[it]?.sospetti ?: 0 },
            nota = if (parziale) "Periodo in corso: l'ultimo giorno è parziale." else null,
        )
    }
}

abstract class Analisi(val def: Definizione) {
    abstract fun calcola(c: Contesto): Esito

    /** Esegue proteggendo l'interfaccia da qualsiasi errore imprevisto. */
    fun esegui(c: Contesto): Esito = try {
        calcola(c)
    } catch (e: Exception) {
        Esito.NonDisponibile("Calcolo non riuscito: ${e.javaClass.simpleName}")
    }
}

object Catalogo {
    val tutte: List<Analisi> by lazy {
        AnalisiAttivita.elenco + AnalisiCuore.elenco + AnalisiSonno.elenco + AnalisiAllenamenti.elenco +
            AnalisiAltri.elenco + Relazioni.elenco + Indici.elenco + Qualita.elenco
    }

    fun perId(id: String): Analisi? = tutte.firstOrNull { it.def.id == id }
    fun perSezione(s: Sezione): List<Analisi> = tutte.filter { it.def.sezione == s }
}

internal fun nd(motivo: String, vararg mancano: String) = Esito.NonDisponibile(motivo, mancano.toList())

internal fun Contesto.richiedi(m: Metrica, minimoGiorni: Int): Esito.NonDisponibile? {
    if (!dati.presente(m)) return nd("Nessun dato di «${m.nome}» nell'archivio: la fonte non lo fornisce o non è stato importato.", m.nome)
    val cop = copertura(m)
    if (cop.giorniConDati < minimoGiorni) return nd("Servono almeno $minimoGiorni giorni con «${m.nome}» nel periodo; ce ne sono ${cop.giorniConDati}.")
    return null
}
