package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoDiario
import org.costalonga.polso.motore.TipoPeriodo
import java.time.LocalDate
import kotlin.math.abs

/**
 * Relazioni esplorative fra coppie di serie giornaliere allineate nel tempo.
 * Ogni coppia dichiara lo sfasamento (per esempio: passi di un giorno e sonno
 * della notte successiva). Le correlazioni sono di Spearman con numerosità
 * efficace corretta per l'autocorrelazione, e i p-value di tutte le coppie
 * calcolate nello stesso periodo sono corretti insieme con Benjamini–Hochberg.
 * Sono associazioni, mai cause.
 */
object Relazioni {
    const val MINIMO_COPPIE = 14

    private const val MANCANTI_REL = "Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato."
    private const val LIMITI_REL = "Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze."

    data class Coppia(
        val id: String,
        val titolo: String,
        val etichettaX: String,
        val etichettaY: String,
        val datiRichiesti: String,
        val allineamento: String,
        val x: (Dati) -> Map<LocalDate, Double>,
        val y: (Dati) -> Map<LocalDate, Double>,
        /** Sfasamento in giorni: y viene preso al giorno d + sfasamento. */
        val sfasamento: Long,
        val settimanale: Boolean = false,
    )

    data class Risultato(val coppia: Coppia, val punti: List<Pair<Double, Double>>, val corr: Stat.Correlazione?, var q: Double? = null)

    // ---------------------------------------------------------- serie di base

    private fun serieMetrica(m: Metrica): (Dati) -> Map<LocalDate, Double> = { d -> d.serie(m).mapNotNull { (g, v) -> v.valore?.let { g to it } }.toMap() }
    private val sonno: (Dati) -> Map<LocalDate, Double> = { d -> d.notti().mapValues { it.value.minutiSonno } }
    private val minutiAllenamento: (Dati) -> Map<LocalDate, Double> = { d ->
        // Zero minuti solo nei giorni in cui l'orologio risulta usato (ci sono passi): altrimenti il giorno manca.
        val usati = d.serie(Metrica.PASSI).filterValues { it.valore != null }.keys
        val per = d.allenamenti.groupBy { Tempo.giorno(it.inizio, d.zona, it.scartoSec) }.mapValues { e -> e.value.sumOf { it.durataMs / 60_000.0 } }
        (usati + per.keys).associateWith { per[it] ?: 0.0 }
    }
    private val fcNotturna: (Dati) -> Map<LocalDate, Double> = { d ->
        d.notti().mapNotNull { (g, n) ->
            val c = d.campioni(Metrica.FREQUENZA_CARDIACA, n.inizio, n.fine)
            if (c.size >= 20) g to c.map { it.valore }.average() else null
        }.toMap()
    }
    private val fcRiposoOppureNotturna: (Dati) -> Map<LocalDate, Double> = { d ->
        val fonte = serieMetrica(Metrica.FC_RIPOSO)(d)
        if (fonte.size >= MINIMO_COPPIE) fonte else fcNotturna(d)
    }
    private val scartoPuntoMedio: (Dati) -> Map<LocalDate, Double> = { d ->
        val notti = d.notti().values
        val medi = notti.associate { it.giorno to AnalisiSonno.puntoMedio(it, d) }
        val m = Stat.mediaOraria(medi.values.toList())
        if (m == null) emptyMap() else medi.mapValues { (_, v) ->
            var diff = abs(v - m)
            if (diff > 720) diff = 1440 - diff
            diff
        }
    }
    private fun diario(t: TipoDiario): (Dati) -> Map<LocalDate, Double> = { d -> d.serieDiario(t) }

    /** Per il peso: variazione settimanale (kg) e media dei passi della stessa settimana. */
    private val pesoSettimanale: (Dati) -> Map<LocalDate, Double> = { d ->
        val perSett = d.seriePeso().entries.groupBy { Periodo.di(TipoPeriodo.SETTIMANA, it.key).da }.mapValues { e -> e.value.map { it.value }.average() }.toSortedMap()
        perSett.entries.zipWithNext().filter { (a, b) -> b.key == a.key.plusWeeks(1) }.associate { (a, b) -> b.key to (b.value - a.value) }
    }
    private val passiSettimanali: (Dati) -> Map<LocalDate, Double> = { d ->
        d.serie(Metrica.PASSI).filterValues { it.valore != null }.entries.groupBy { Periodo.di(TipoPeriodo.SETTIMANA, it.key).da }
            .filterValues { it.size >= 4 }.mapValues { e -> e.value.map { it.value.valore!! }.average() }
    }

    val coppie = listOf(
        Coppia("rel.passi_sonno", "Attività e sonno della notte successiva", "passi del giorno", "minuti di sonno della notte dopo", "Passi e sonno",
            "passi del giorno d con il sonno della notte che termina il giorno d+1", serieMetrica(Metrica.PASSI), sonno, 1),
        Coppia("rel.sonno_passi", "Sonno e attività del giorno dopo", "minuti di sonno", "passi del giorno", "Sonno e passi",
            "sonno della notte che termina il giorno d con i passi dello stesso giorno d", sonno, serieMetrica(Metrica.PASSI), 0),
        Coppia("rel.allenamento_riposo", "Allenamenti e frequenza a riposo del giorno dopo", "minuti di allenamento", "FC a riposo (o media notturna) del giorno dopo", "Allenamenti, passi (per i giorni senza allenamento) e FC a riposo della fonte o FC notturna",
            "minuti di allenamento del giorno d con la FC del giorno d+1", minutiAllenamento, fcRiposoOppureNotturna, 1),
        Coppia("rel.stress_sonno", "Stress e sonno della notte successiva", "stress medio (fonte)", "minuti di sonno della notte dopo", "Stress importato e sonno",
            "stress del giorno d con il sonno della notte che termina il giorno d+1", serieMetrica(Metrica.STRESS), sonno, 1),
        Coppia("rel.regolarita_passi", "Regolarità del sonno e attività", "scarto dal tuo orario medio di sonno (min)", "passi del giorno", "Sonno e passi",
            "scarto del punto medio della notte che termina il giorno d dal tuo punto medio abituale, con i passi del giorno d", scartoPuntoMedio, serieMetrica(Metrica.PASSI), 0),
        Coppia("rel.peso_movimento", "Peso e movimento", "passi medi della settimana", "variazione di peso rispetto alla settimana prima (kg)", "Peso (diario o bilancia) e passi",
            "media dei passi di una settimana (almeno 4 giorni) con la variazione del peso medio rispetto alla settimana precedente", passiSettimanali, pesoSettimanale, 0, settimanale = true),
        Coppia("rel.caffeina_sonno", "Caffeina e sonno", "tazze di caffeina", "minuti di sonno della notte dopo", "Diario caffeina e sonno",
            "caffeina del giorno d con il sonno della notte che termina il giorno d+1", diario(TipoDiario.CAFFEINA), sonno, 1),
        Coppia("rel.alcol_fc", "Alcol e frequenza notturna", "unità di alcol", "FC media della notte dopo", "Diario alcol, sonno e frequenza cardiaca",
            "alcol del giorno d con la FC media durante il sonno della notte che termina il giorno d+1", diario(TipoDiario.ALCOL), fcNotturna, 1),
        Coppia("rel.sonno_umore", "Sonno e umore", "minuti di sonno", "umore (1-5)", "Sonno e diario umore",
            "sonno della notte che termina il giorno d con l'umore annotato il giorno d", sonno, diario(TipoDiario.UMORE), 0),
        Coppia("rel.sonno_energia", "Sonno ed energia", "minuti di sonno", "energia (1-5)", "Sonno e diario energia",
            "sonno della notte che termina il giorno d con l'energia annotata il giorno d", sonno, diario(TipoDiario.ENERGIA), 0),
        Coppia("rel.sonno_riposo", "Sonno misurato e riposo percepito", "minuti di sonno", "riposo percepito (1-5)", "Sonno e diario riposo",
            "sonno della notte che termina il giorno d con il riposo percepito annotato il giorno d", sonno, diario(TipoDiario.RIPOSO_PERCEPITO), 0),
    )

    fun punti(c: Contesto, coppia: Coppia): List<Pair<Double, Double>> {
        val xs = coppia.x(c.dati)
        val ys = coppia.y(c.dati)
        val giorni = if (coppia.settimanale) c.giorni.map { Periodo.di(TipoPeriodo.SETTIMANA, it).da }.distinct() else c.giorni
        val passo = if (coppia.settimanale) 0L else coppia.sfasamento
        return giorni.mapNotNull { g ->
            val gy = g.plusDays(passo)
            if (gy > c.oggi) return@mapNotNull null
            val x = xs[g] ?: return@mapNotNull null
            val y = ys[gy] ?: return@mapNotNull null
            x to y
        }
    }

    private val cache = object : LinkedHashMap<Triple<Int, Periodo, LocalDate>, List<Risultato>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Periodo, LocalDate>, List<Risultato>>?) = size > 4
    }

    /** Calcola tutte le coppie e applica Benjamini–Hochberg a quelle con abbastanza dati. */
    @Synchronized
    fun tutte(c: Contesto): List<Risultato> = cache.getOrPut(Triple(System.identityHashCode(c.dati), c.periodo, c.oggi)) {
        val r = coppie.map { cp ->
            val p = punti(c, cp)
            val minimo = if (cp.settimanale) 6 else MINIMO_COPPIE
            Risultato(cp, p, if (p.size >= minimo) Stat.correlazione(p.map { it.first }, p.map { it.second }) else null)
        }
        val calcolate = r.filter { it.corr != null }
        Stat.benjaminiHochberg(calcolate.map { it.corr!!.p }).forEachIndexed { i, q -> calcolate[i].q = q }
        r
    }

    fun forza(rho: Double): String = when {
        abs(rho) < 0.1 -> "trascurabile"
        abs(rho) < 0.3 -> "debole"
        abs(rho) < 0.5 -> "moderata"
        else -> "forte"
    }

    fun descrivi(r: Risultato): String {
        val k = r.corr ?: return "non calcolata"
        val verso = if (k.rho > 0) "quando ${r.coppia.etichettaX} è più alto, ${r.coppia.etichettaY} tende a essere più alto" else "quando ${r.coppia.etichettaX} è più alto, ${r.coppia.etichettaY} tende a essere più basso"
        val distinguibile = (r.q ?: 1.0) < 0.1
        return if (distinguibile) "Associazione ${forza(k.rho)}: $verso." else "Nessuna associazione distinguibile dal caso con i dati disponibili (associazione ${forza(k.rho)}, q = ${Formato.pValore(r.q)})."
    }

    private fun analisiPer(cp: Coppia): Analisi = analisi(
        Definizione(cp.id, cp.titolo, Sezione.RELAZIONI, cp.datiRichiesti,
            "Correlazione di Spearman fra ${cp.allineamento}. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher.",
            "ρ (−1…1)", if (cp.settimanale) "6 settimane appaiate" else "$MINIMO_COPPIE giorni appaiati", MANCANTI_REL, LIMITI_REL, "RelazioniTest"),
    ) { c ->
        val r = tutte(c).first { it.coppia.id == cp.id }
        val minimo = if (cp.settimanale) 6 else MINIMO_COPPIE
        val k = r.corr ?: return@analisi nd("Servono almeno $minimo ${if (cp.settimanale) "settimane" else "giorni"} con entrambe le grandezze; ce ne sono ${r.punti.size}.", cp.datiRichiesti)
        Esito.Disponibile(
            listOf(
                Voce("Sintesi", descrivi(r)),
                Voce("ρ di Spearman", "${Formato.numero(k.rho, 2)} (IC 95%: ${Formato.numero(k.ic95.first, 2)} … ${Formato.numero(k.ic95.second, 2)})", k.rho),
                Voce("Coppie / numerosità efficace", "${k.n} / ${Formato.numero(k.nEfficace, 1)}"),
                Voce("p-value / q-value", "${Formato.pValore(k.p)} / ${Formato.pValore(r.q)}"),
            ),
            Copertura(k.n, c.giorni.size, null, null),
            listOf(Grafico.Dispersione(cp.titolo, "", cp.etichettaX, cp.etichettaY, r.punti)),
            listOf(LIMITI_REL, "Allineamento: ${cp.allineamento}."),
            principale = k.rho,
        )
    }

    val elenco: List<Analisi> = coppie.map { analisiPer(it) }
}
