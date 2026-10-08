package org.costalonga.polso.motore

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Notifiche solo per variazioni importanti e persistenti rispetto al TUO
 * storico. Regole fisse, verificabili, senza soglie cliniche e senza IA.
 * Ogni regola richiede uno storico minimo e più giorni consecutivi fuori
 * dall'intervallo abituale; i giorni mancanti interrompono la persistenza.
 */
@Serializable
data class ConfigNotifiche(
    val attive: Boolean = true,
    val regoleAttive: Set<String> = Regole.tutte.map { it.id }.toSet(),
    /** Intervallo minimo fra due notifiche qualsiasi. */
    val intervalloMinimoOre: Int = 24,
    /** Una stessa regola non si ripete prima di questo tempo. */
    val ripetizioneRegolaOre: Int = 72,
    val silenzioDa: Int = 22,
    val silenzioA: Int = 8,
    val giorniPersistenza: Int = 3,
)

data class Segnalazione(
    val regola: String,
    val titolo: String,
    val testo: String,
    val dato: String,
    val periodo: String,
    val motivo: String,
)

data class Regola(val id: String, val nome: String, val descrizione: String, val valuta: (Dati, LocalDate, ConfigNotifiche) -> Segnalazione?)

object Regole {
    private const val MAD_NORMALE = 1.4826

    /** Riferimento personale: mediana e dispersione robusta di una finestra di 28 giorni. */
    data class Riferimento(val mediana: Double, val dispersione: Double, val n: Int)

    fun riferimento(valori: List<Double>, minimo: Int): Riferimento? {
        if (valori.size < minimo) return null
        return Riferimento(Stat.mediana(valori)!!, (Stat.mad(valori) ?: 0.0) * MAD_NORMALE, valori.size)
    }

    /**
     * Controllo comune: gli ultimi [k] giorni (tutti presenti) sono oltre la
     * soglia; il riferimento sono i 28 giorni precedenti.
     */
    private fun persistente(serie: Map<LocalDate, Double>, ultimo: LocalDate, k: Int, minimoRif: Int, oltre: (Double, Riferimento) -> Boolean): Pair<List<Double>, Riferimento>? {
        val recenti = (0 until k).map { ultimo.minusDays(it.toLong()) }.reversed()
        val v = recenti.map { serie[it] ?: return null }
        val rif = riferimento(Tempo.giorni(ultimo.minusDays(k + 27L), ultimo.minusDays(k.toLong())).mapNotNull { serie[it] }, minimoRif) ?: return null
        return if (v.all { oltre(it, rif) }) v to rif else null
    }

    private fun periodo(ultimo: LocalDate, k: Int) = "dal ${Tempo.etichetta(ultimo.minusDays(k - 1L))} al ${Tempo.etichetta(ultimo)}"

    val fcRiposo = Regola("fc_riposo", "Frequenza a riposo più alta del solito",
        "Scatta se negli ultimi giorni completi (3 per impostazione) la FC a riposo della fonte, o in sua assenza la FC media durante il sonno, supera ogni giorno la tua mediana dei 28 giorni precedenti di almeno max(5 bpm; 2 deviazioni robuste). Richiede 14 giorni di riferimento.") { d, oggi, cfg ->
        val fonte = d.serie(Metrica.FC_RIPOSO).mapNotNull { (g, v) -> v.valore?.let { g to it } }.toMap()
        val (serie, nome) = if (fonte.size >= 14) fonte to "FC a riposo (fonte)" else d.notti().mapNotNull { (g, n) ->
            val c = d.campioni(Metrica.FREQUENZA_CARDIACA, n.inizio, n.fine)
            if (c.size >= 20) g to c.map { it.valore }.average() else null
        }.toMap() to "FC media durante il sonno (calcolata)"
        val ultimo = if (fonte.size >= 14) oggi.minusDays(1) else oggi
        val k = cfg.giorniPersistenza
        persistente(serie, ultimo, k, 14) { v, r -> v >= r.mediana + maxOf(5.0, 2 * r.dispersione) }?.let { (v, r) ->
            Segnalazione("fc_riposo", "Frequenza a riposo sopra il tuo solito",
                "$nome: ${v.joinToString(" · ") { Formato.numero(it) }} bpm ${periodo(ultimo, k)}, contro una mediana personale di ${Formato.numero(r.mediana)} bpm. Può dipendere da sonno scarso, allenamenti intensi, alcol, caldo o un malanno in arrivo. Se si accompagna a sintomi o persiste, parlane con il medico.",
                nome, periodo(ultimo, k), "$k giorni consecutivi sopra ${Formato.numero(r.mediana + maxOf(5.0, 2 * r.dispersione))} bpm (mediana + max(5; 2 deviazioni robuste), riferimento su ${r.n} giorni)")
        }
    }

    val sonnoRidotto = Regola("sonno_ridotto", "Sonno molto più breve del solito",
        "Scatta se le ultime notti (3 per impostazione), tutte registrate, durano ciascuna meno della tua mediana dei 28 giorni precedenti di almeno max(60 minuti; 2 deviazioni robuste). Richiede 14 notti di riferimento.") { d, oggi, cfg ->
        val serie = d.notti().mapValues { it.value.minutiSonno }
        val k = cfg.giorniPersistenza
        persistente(serie, oggi, k, 14) { v, r -> v <= r.mediana - maxOf(60.0, 2 * r.dispersione) }?.let { (v, r) ->
            Segnalazione("sonno_ridotto", "Sonno più breve del tuo solito",
                "Nelle ultime $k notti hai dormito ${v.joinToString(" · ") { Formato.durata(it) }}, contro una mediana personale di ${Formato.durata(r.mediana)}.",
                "Durata del sonno", periodo(oggi, k), "$k notti consecutive sotto ${Formato.durata(r.mediana - maxOf(60.0, 2 * r.dispersione))} (riferimento su ${r.n} notti)")
        }
    }

    val passiCalati = Regola("passi_calati", "Attività dimezzata",
        "Scatta se la media dei passi degli ultimi 7 giorni completi (almeno 5 con dati) è inferiore alla metà della media dei 28 giorni precedenti (almeno 20 con dati).") { d, oggi, _ ->
        val s = d.serie(Metrica.PASSI)
        val ultimi = Tempo.giorni(oggi.minusDays(7), oggi.minusDays(1)).mapNotNull { s[it]?.valore }
        val rif = Tempo.giorni(oggi.minusDays(35), oggi.minusDays(8)).mapNotNull { s[it]?.valore }
        if (ultimi.size < 5 || rif.size < 20) return@Regola null
        val a = ultimi.average()
        val b = rif.average()
        if (a < 0.5 * b) Segnalazione("passi_calati", "Attività molto più bassa del solito",
            "Negli ultimi 7 giorni hai fatto in media ${Formato.numero(a)} passi al giorno, meno della metà della tua media precedente (${Formato.numero(b)}). Se non indossi l'orologio con regolarità, il calo può essere solo apparente.",
            "Passi", periodo(oggi.minusDays(1), 7), "media 7 giorni < 50% della media dei 28 giorni precedenti") else null
    }

    val spo2 = Regola("spo2_bassa", "Saturazione notturna più bassa del solito",
        "Scatta se la media giornaliera della SpO₂ negli ultimi giorni completi (3 per impostazione) è ogni giorno inferiore di almeno 3 punti alla tua mediana dei 28 giorni precedenti (14 giorni di riferimento). Non è una soglia clinica.") { d, oggi, cfg ->
        val serie = d.serie(Metrica.SPO2).mapNotNull { (g, v) -> v.valore?.takeIf { v.n >= 3 }?.let { g to it } }.toMap()
        val k = cfg.giorniPersistenza
        val ultimo = oggi.minusDays(1)
        persistente(serie, ultimo, k, 14) { v, r -> v <= r.mediana - 3 }?.let { (v, r) ->
            Segnalazione("spo2_bassa", "Saturazione più bassa del tuo solito",
                "SpO₂ media: ${v.joinToString(" · ") { Formato.numero(it, 1) }}% ${periodo(ultimo, k)}, contro una mediana personale di ${Formato.numero(r.mediana, 1)}%. Le misure da polso risentono di movimento e posizione; se hai sintomi come affanno, rivolgiti al medico.",
                "SpO₂", periodo(ultimo, k), "$k giorni consecutivi almeno 3 punti sotto la mediana personale")
        }
    }

    val datiAssenti = Regola("dati_assenti", "Nessun dato nuovo",
        "Scatta se da 3 giorni non arriva nessuna misura da Health Connect o da altre fonti automatiche: di solito la sincronizzazione si è interrotta.") { d, oggi, _ ->
        val ultimo = d.ultimoGiorno() ?: return@Regola null
        val giorni = java.time.temporal.ChronoUnit.DAYS.between(ultimo, oggi)
        if (giorni >= 3) Segnalazione("dati_assenti", "Nessun dato da $giorni giorni",
            "L'ultimo dato risale al ${Tempo.etichetta(ultimo)}. Controlla che HONOR Health sincronizzi con Health Connect e che l'orologio sia collegato.",
            "Tutte le misure", "dal ${Tempo.etichetta(ultimo)} a oggi", "nessuna misura da almeno 3 giorni") else null
    }

    val tutte = listOf(fcRiposo, sonnoRidotto, passiCalati, spo2, datiAssenti)

    fun valuta(d: Dati, oggi: LocalDate, cfg: ConfigNotifiche): List<Segnalazione> =
        if (!cfg.attive) emptyList() else tutte.filter { it.id in cfg.regoleAttive }.mapNotNull { it.valuta(d, oggi, cfg) }

    fun inSilenzio(ora: LocalTime, cfg: ConfigNotifiche): Boolean {
        val h = ora.hour
        return if (cfg.silenzioDa == cfg.silenzioA) false
        else if (cfg.silenzioDa < cfg.silenzioA) h in cfg.silenzioDa until cfg.silenzioA
        else h >= cfg.silenzioDa || h < cfg.silenzioA
    }

    /**
     * Sceglie cosa inviare adesso: niente nelle ore silenziose, niente prima
     * dell'intervallo minimo dall'ultima notifica, nessuna regola ripetuta
     * prima del suo tempo. Al massimo una notifica per volta.
     */
    fun daInviare(s: List<Segnalazione>, storico: Map<String, Long>, adesso: Instant, cfg: ConfigNotifiche, zona: java.time.ZoneId = Tempo.ROMA): Segnalazione? {
        if (s.isEmpty()) return null
        if (inSilenzio(adesso.atZone(zona).toLocalTime(), cfg)) return null
        val ultima = storico.values.maxOrNull()
        if (ultima != null && adesso.toEpochMilli() - ultima < cfg.intervalloMinimoOre * 3_600_000L) return null
        return s.firstOrNull { seg -> storico[seg.regola]?.let { adesso.toEpochMilli() - it >= cfg.ripetizioneRegolaOre * 3_600_000L } ?: true }
    }
}
