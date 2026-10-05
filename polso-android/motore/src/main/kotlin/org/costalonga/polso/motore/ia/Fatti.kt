package org.costalonga.polso.motore.ia

import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Contesto
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Evidenze
import org.costalonga.polso.motore.analisi.Sezione
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * I fatti passati al modello linguistico: solo risultati già calcolati dal
 * motore deterministico, in testo compatto. Il modello non riceve misure
 * grezze e non deve fare conti.
 */
data class Fatti(val periodo: Periodo, val testo: String, val analisiUsate: List<String>, val mancanti: List<String>) {
    /** Numeri presenti nei fatti: l'unica fonte ammessa per i numeri della risposta. */
    val numeri: Set<Double> by lazy { Verifica.numeri(testo).toSet() }
}

object CostruttoreFatti {
    val RIEPILOGO_GIORNO = listOf("att.passi", "att.distanza", "att.calorie", "fc.giornaliera", "fc.riposo", "sonno.durata", "sonno.orari", "sonno.fasi", "all.riepilogo", "altri.spo2", "altri.stress")
    val RIEPILOGO_SETTIMANA = listOf("att.passi", "att.confronto", "att.obiettivo", "att.settimana", "fc.giornaliera", "fc.notturna", "fc.riposo", "sonno.durata", "sonno.orari", "sonno.obiettivo", "sonno.weekend", "all.riepilogo", "all.frequenza", "altri.spo2", "altri.peso", "ind.recupero")

    fun costruisci(d: Dati, p: Periodo, oggi: LocalDate, ids: List<String>, massimoCaratteri: Int = 6000): Fatti {
        val c = Contesto(d, p, oggi)
        val sb = StringBuilder()
        sb.appendLine("PERIODO: ${p.descrizione()}${if (p.parziale(oggi)) " (in corso: l'ultimo giorno è parziale)" else ""}")
        sb.appendLine("OGGI: ${Tempo.etichetta(oggi)}")
        val usate = mutableListOf<String>()
        val mancanti = mutableListOf<String>()
        for (id in ids) {
            val a = Catalogo.perId(id) ?: continue
            when (val e = a.esegui(c)) {
                is Esito.Disponibile -> {
                    val blocco = buildString {
                        appendLine("## ${a.def.titolo} [${a.def.id}] — dati in ${e.copertura.giorniConDati} giorni su ${e.copertura.giorniNelPeriodo}")
                        e.voci.forEach { appendLine("- ${it.etichetta}: ${it.testo}${if (it.provenienza.name == "FONTE") " (misura della fonte)" else ""}") }
                        appendLine("- Limiti: ${a.def.limiti}")
                    }
                    if (sb.length + blocco.length > massimoCaratteri) break
                    sb.append(blocco)
                    usate += id
                }
                is Esito.NonDisponibile -> mancanti += "${a.def.titolo}: ${e.motivo}"
            }
        }
        val ev = Evidenze.calcola(c)
        if (ev.isNotEmpty()) {
            sb.appendLine("## Cosa emerge (regole fisse)")
            ev.take(6).forEach { sb.appendLine("- ${it.titolo}: ${it.testo}") }
        }
        if (mancanti.isNotEmpty()) {
            sb.appendLine("## Non disponibile")
            mancanti.take(8).forEach { sb.appendLine("- $it") }
        }
        return Fatti(p, sb.toString().take(massimoCaratteri + 1500), usate, mancanti)
    }

    fun perRiepilogo(d: Dati, tipo: TipoPeriodo, rif: LocalDate, oggi: LocalDate): Fatti =
        costruisci(d, Periodo.di(tipo, rif), oggi, if (tipo == TipoPeriodo.GIORNO) RIEPILOGO_GIORNO else RIEPILOGO_SETTIMANA)
}

object Prompt {
    val SISTEMA = """
Sei l'assistente di Polso, un'app personale che analizza i dati di uno smartwatch. Rispondi sempre in italiano semplice e cordiale.
Regole obbligatorie:
1. Usa SOLO i numeri presenti nei FATTI, scritti esattamente come compaiono. Non fare calcoli, somme, medie o arrotondamenti nuovi. Non inventare valori.
2. Se un'informazione non è nei FATTI, dillo chiaramente («non ho questo dato»).
3. Indica sempre il periodo e quanti giorni di dati ci sono.
4. Non fare diagnosi, non nominare malattie come possibili cause, non dare indicazioni su farmaci o terapie. Lo smartwatch non sostituisce il medico.
5. Le relazioni fra dati sono associazioni, non cause.
6. I suggerimenti sono di benessere generale e prudenti (sonno regolare, movimento, pause). Se un valore preoccupa la persona, invita a parlarne con il medico.
7. Niente elenchi lunghi: al massimo 8 frasi.
""".trimIndent()

    fun riepilogo(f: Fatti, tipo: String): String = """
FATTI (calcolati dall'app, $tipo):
${f.testo}

Scrivi un riepilogo $tipo in 4-7 frasi: cosa è andato bene, cosa è cambiato, eventuali dati mancanti, e un solo suggerimento prudente di benessere.
""".trimIndent()

    fun domanda(f: Fatti, domanda: String): String = """
FATTI (calcolati dall'app):
${f.testo}

DOMANDA: $domanda
Rispondi usando solo i FATTI. Cita il periodo.
""".trimIndent()

    fun spiegazione(f: Fatti, titolo: String): String = """
FATTI (calcolati dall'app):
${f.testo}

Spiega in parole semplici il grafico «$titolo»: cosa mostra, la tendenza principale e i limiti dei dati. Massimo 5 frasi.
""".trimIndent()
}

/** Controlli sull'uscita del modello prima di mostrarla. */
object Verifica {
    private val reNumero = Regex("(?<![\\p{L}\\d])[-−]?\\d{1,3}(?:[.\\u00a0 ]\\d{3})+(?:,\\d+)?|(?<![\\p{L}\\d])[-−]?\\d+(?:[,.]\\d+)?")

    fun numeri(testo: String): List<Double> = reNumero.findAll(testo).mapNotNull { m ->
        var s = m.value.replace("−", "-").replace(" ", "").replace(" ", "")
        s = if (Regex("\\d{1,3}(\\.\\d{3})+(,\\d+)?").matches(s.trimStart('-'))) s.replace(".", "").replace(',', '.') else s.replace(',', '.')
        s.toDoubleOrNull()
    }.toList()

    /** Parole che indicano diagnosi o indicazioni terapeutiche: la risposta viene scartata. */
    private val vietate = listOf("diagnos", "sei malat", "hai una malattia", "aritmia", "fibrillazione", "apnea notturna", "ipertensione", "diabete", "aumenta la dose", "riduci la dose", "smetti di prendere", "sospendi il farmaco", "cambia terapia")

    data class Esame(val valida: Boolean, val numeriNonTrovati: List<Double>, val paroleVietate: List<String>)

    /**
     * Ogni numero della risposta deve comparire nei fatti (tolleranza
     * dell'1% o di mezza unità, per formati diversi). Fanno eccezione gli
     * interi da 0 a 10, usati nel linguaggio comune («due notti», «7 giorni»
     * compare comunque nei fatti) e gli anni del periodo.
     */
    fun esamina(risposta: String, f: Fatti): Esame {
        val ammessi = f.numeri + (f.periodo.da.year..f.periodo.a.year).map { it.toDouble() }
        val fuori = numeri(risposta).filter { n ->
            if (n == Math.floor(n) && n in 0.0..10.0) false
            else ammessi.none { a -> kotlin.math.abs(a - n) <= maxOf(0.5, kotlin.math.abs(a) * 0.01) }
        }.distinct()
        val minuscolo = risposta.lowercase()
        val parole = vietate.filter { it in minuscolo }
        return Esame(fuori.isEmpty() && parole.isEmpty(), fuori, parole)
    }
}

/** Comprensione deterministica delle domande più comuni, senza modello. */
data class Domanda(val metriche: List<Metrica>, val sonno: Boolean, val allenamenti: Boolean, val periodo: Periodo, val tipo: String)

object Interprete {
    private val mesi = Tempo.nomiMesi

    fun interpreta(testo: String, oggi: LocalDate): Domanda {
        val t = testo.lowercase()
        val metriche = buildList {
            if (Regex("pass|cammin|attiv|distan|chilometr|\\bkm\\b").containsMatchIn(t)) { add(Metrica.PASSI); add(Metrica.DISTANZA) }
            if (Regex("cuor|battit|frequenza|\\bfc\\b|pulsaz").containsMatchIn(t)) { add(Metrica.FREQUENZA_CARDIACA); add(Metrica.FC_RIPOSO) }
            if (Regex("ossigen|satura|spo2").containsMatchIn(t)) add(Metrica.SPO2)
            if (Regex("stress").containsMatchIn(t)) add(Metrica.STRESS)
            if (Regex("peso|chili|bilancia").containsMatchIn(t)) add(Metrica.PESO)
            if (Regex("calori").containsMatchIn(t)) add(Metrica.CALORIE_ATTIVE)
        }
        val sonno = Regex("sonn|dorm|nott|svegli|letto").containsMatchIn(t)
        val all = Regex("allenam|corsa|corso|sport|bici|nuot|palestra|uscit").containsMatchIn(t)
        val tipo = when {
            Regex("record|massim|miglior|più alt|di più").containsMatchIn(t) -> "record"
            Regex("tendenz|andament|miglior|peggior|cresc|calat|aument|diminu").containsMatchIn(t) -> "tendenza"
            Regex("confront|rispetto|prima|precedente").containsMatchIn(t) -> "confronto"
            Regex("relazion|influ|dipend|legat|quando .* allora").containsMatchIn(t) -> "relazione"
            else -> "riepilogo"
        }
        return Domanda(metriche, sonno, all, periodo(t, oggi), tipo)
    }

    fun periodo(t: String, oggi: LocalDate): Periodo {
        Regex("ultim[ie] (\\d{1,3}) giorni").find(t)?.let { return Periodo.ultimiGiorni(it.groupValues[1].toInt().coerceIn(1, 366), oggi) }
        Regex("ultim[ie] (\\d{1,2}) settimane").find(t)?.let { return Periodo.ultimiGiorni(it.groupValues[1].toInt().coerceIn(1, 52) * 7, oggi) }
        Regex("ultim[ie] (\\d{1,2}) mesi").find(t)?.let { return Periodo(TipoPeriodo.PERSONALIZZATO, oggi.minusMonths(it.groupValues[1].toLong()).plusDays(1), oggi) }
        return when {
            "ieri" in t -> Periodo.di(TipoPeriodo.GIORNO, oggi.minusDays(1))
            "oggi" in t || "stanotte" in t || "questa notte" in t -> Periodo.di(TipoPeriodo.GIORNO, oggi)
            "settimana scorsa" in t || "scorsa settimana" in t -> Periodo.di(TipoPeriodo.SETTIMANA, oggi.minusWeeks(1))
            "settimana" in t -> Periodo.di(TipoPeriodo.SETTIMANA, oggi)
            "mese scorso" in t || "scorso mese" in t -> Periodo.di(TipoPeriodo.MESE, oggi.minusMonths(1))
            "anno scorso" in t -> Periodo.di(TipoPeriodo.ANNO, oggi.minusYears(1))
            "quest'anno" in t || "anno" in t -> Periodo.di(TipoPeriodo.ANNO, oggi)
            mesi.any { it in t } -> {
                val m = mesi.indexOfFirst { it in t } + 1
                val anno = if (m > oggi.monthValue) oggi.year - 1 else oggi.year
                Periodo.di(TipoPeriodo.MESE, LocalDate.of(anno, m, 1))
            }
            "mese" in t -> Periodo.di(TipoPeriodo.MESE, oggi)
            else -> Periodo.ultimiGiorni(30, oggi)
        }
    }

    /** Analisi pertinenti per la domanda (sempre con la copertura). */
    fun analisi(d: Domanda): List<String> {
        val ids = mutableListOf<String>()
        if (Metrica.PASSI in d.metriche) ids += when (d.tipo) {
            "record" -> listOf("att.record", "att.passi")
            "tendenza" -> listOf("att.tendenza", "att.passi")
            "confronto" -> listOf("att.confronto", "att.passi")
            else -> listOf("att.passi", "att.obiettivo", "att.distanza")
        }
        if (Metrica.FREQUENZA_CARDIACA in d.metriche) ids += listOf("fc.giornaliera", "fc.riposo", "fc.notturna", "fc.storico")
        if (Metrica.SPO2 in d.metriche) ids += "altri.spo2"
        if (Metrica.STRESS in d.metriche) ids += "altri.stress"
        if (Metrica.PESO in d.metriche) ids += "altri.peso"
        if (Metrica.CALORIE_ATTIVE in d.metriche) ids += "att.calorie"
        if (d.sonno) ids += if (d.tipo == "tendenza") listOf("sonno.tendenza", "sonno.durata") else listOf("sonno.durata", "sonno.orari", "sonno.obiettivo", "sonno.fasi")
        if (d.allenamenti) ids += listOf("all.riepilogo", "all.sport", "all.passo", "all.progressi")
        if (d.tipo == "relazione") ids += org.costalonga.polso.motore.analisi.Relazioni.coppie.map { it.id }
        if (ids.isEmpty()) ids += CostruttoreFatti.RIEPILOGO_SETTIMANA
        return ids.distinct()
    }
}

/** Testi scritti dal motore deterministico: funzionano sempre, anche senza modello. */
object TestiDeterministici {
    fun riepilogo(f: Fatti): String = buildString {
        appendLine("Riepilogo calcolato (senza IA) — ${f.periodo.descrizione()}.")
        f.testo.lineSequence().filter { it.startsWith("## ") || it.startsWith("- ") }.filterNot { it.startsWith("- Limiti") }.take(40).forEach { appendLine(it.removePrefix("## ").let { l -> if (it.startsWith("## ")) "\n$l" else l }) }
    }.trim()

    fun risposta(d: Domanda, f: Fatti): String = buildString {
        append("Ecco cosa risulta dai dati per il periodo ${f.periodo.descrizione()}")
        val giorni = ChronoUnit.DAYS.between(f.periodo.da, f.periodo.a) + 1
        appendLine(" ($giorni giorni):")
        if (f.analisiUsate.isEmpty()) appendLine("Non ci sono dati sufficienti per rispondere.")
        f.testo.lineSequence().filter { it.startsWith("## ") || it.startsWith("- ") }.filterNot { it.startsWith("- Limiti") }.take(30).forEach { appendLine(it.removePrefix("## ")) }
        if (f.mancanti.isNotEmpty()) appendLine("Alcune analisi non sono disponibili: vedi sopra.")
    }.trim()

    fun intestazione(f: Fatti, motore: String): String =
        "Basato su: ${f.periodo.descrizione()} · analisi ${f.analisiUsate.size} · motore: $motore · ${Formato.numero(f.numeri.size.toDouble())} valori di riferimento"
}
