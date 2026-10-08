package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.MetodoZone
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.Sport
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import java.time.LocalDate
import kotlin.math.abs

/** FC massima e a riposo usate per zone e carichi, con la loro origine dichiarata. */
data class RiferimentiCardiaci(val fcMax: Double?, val origineMax: String, val fcRiposo: Double?, val origineRiposo: String)

object Cardio {
    const val SALTO_MASSIMO_MS = 120_000L

    fun riferimenti(d: Dati, oggi: LocalDate): RiferimentiCardiaci {
        val p = d.pref
        val (max, oMax) = when {
            p.fcMaxManuale != null -> p.fcMaxManuale.toDouble() to "inserita da te"
            p.annoNascita != null -> (208 - 0.7 * (oggi.year - p.annoNascita)) to "stimata con la formula di Tanaka (208 − 0,7 × età)"
            else -> null to "mancante: indica anno di nascita o FC massima nelle impostazioni"
        }
        val riposoFonte = d.misure(Metrica.FC_RIPOSO).filter { it.inizio >= Tempo.inizioGiorno(oggi.minusDays(30), d.zona) && Metrica.FC_RIPOSO.plausibile(it.valore) }.map { it.valore }
        val (rip, oRip) = when {
            p.fcRiposoManuale != null -> p.fcRiposoManuale.toDouble() to "inserita da te"
            riposoFonte.size >= 3 -> Stat.mediana(riposoFonte) to "mediana degli ultimi 30 giorni della fonte"
            else -> null to "mancante"
        }
        return RiferimentiCardiaci(max, oMax, rip, oRip)
    }

    /** Limiti inferiori delle zone 1..5 in bpm, oppure null se mancano i riferimenti. */
    fun limitiZone(r: RiferimentiCardiaci, metodo: MetodoZone): List<Double>? {
        val max = r.fcMax ?: return null
        val quote = listOf(0.5, 0.6, 0.7, 0.8, 0.9)
        return when (metodo) {
            MetodoZone.PERCENTUALE_FCMAX -> quote.map { it * max }
            MetodoZone.KARVONEN -> {
                val rip = r.fcRiposo ?: return null
                quote.map { rip + it * (max - rip) }
            }
        }
    }

    fun zona(fc: Double, limiti: List<Double>): Int = limiti.indexOfLast { fc >= it } + 1 // 0 = sotto la zona 1

    data class TempoInZone(val minuti: DoubleArray, val coperturaPct: Double, val campioni: Int)

    /**
     * Tempo in zona: fra due campioni consecutivi distanti al massimo 2 minuti,
     * il tempo va alla zona del primo campione. Gli intervalli più lunghi
     * sono buchi e non vengono attribuiti.
     */
    fun tempoInZone(campioni: List<Misura>, inizio: Long, fine: Long, limiti: List<Double>): TempoInZone {
        val minuti = DoubleArray(6)
        var coperti = 0L
        val c = campioni.filter { it.inizio in inizio..fine }.sortedBy { it.inizio }
        for (i in 0 until c.size - 1) {
            val dt = c[i + 1].inizio - c[i].inizio
            if (dt in 1..SALTO_MASSIMO_MS) {
                minuti[zona(c[i].valore, limiti)] += dt / 60_000.0
                coperti += dt
            }
        }
        val durata = (fine - inizio).coerceAtLeast(1)
        return TempoInZone(minuti, 100.0 * coperti / durata, c.size)
    }

    /** TRIMP di Edwards: somma dei minuti in zona moltiplicati per il numero della zona (1-5). */
    fun trimpEdwards(t: TempoInZone): Double = (1..5).sumOf { t.minuti[it] * it }

    /** Recupero: FC alla fine meno FC dopo [secondi]; servono campioni entro ±15 s dai due istanti. */
    fun recupero(campioni: List<Misura>, fine: Long, secondi: Int): Pair<Double, Double>? {
        fun vicino(t: Long) = campioni.filter { abs(it.inizio - t) <= 15_000 }.minByOrNull { abs(it.inizio - t) }
        val a = vicino(fine) ?: return null
        val b = vicino(fine + secondi * 1000L) ?: return null
        if (b.inizio <= a.inizio) return null
        return a.valore to b.valore
    }
}

object AnalisiCuore {
    private fun def(id: String, titolo: String, dati: String, metodo: String, unita: String, minimo: String, limiti: String, prova: String, mancanti: String = MANCANTI_STD) =
        Definizione(id, titolo, Sezione.CUORE, dati, metodo, unita, minimo, mancanti, limiti, prova)

    val giornaliera = analisi(def("fc.giornaliera", "Frequenza cardiaca giornaliera", "Campioni di frequenza cardiaca",
        "Per ogni giorno: media dei campioni, minimo e massimo, numero di campioni e ore coperte. Riepilogo del periodo sulle medie giornaliere.",
        "bpm", "1 giorno con campioni", "La media dei campioni pesa di più i momenti in cui l'orologio misura più spesso (per esempio durante gli allenamenti). Valori fuori 25–230 bpm sono esclusi come sospetti.",
        "AnalisiCuoreTest.giornaliera")) { c ->
        val base = riepilogoMetrica(c, Metrica.FREQUENZA_CARDIACA, 1)
        if (base !is Esito.Disponibile) return@analisi base
        val s = c.dati.serie(Metrica.FREQUENZA_CARDIACA)
        val giorni = c.giorni.mapNotNull { s[it] }.filter { it.valore != null }
        val minimo = giorni.minOf { it.minimo!! }
        val massimo = giorni.maxOf { it.massimo!! }
        base.copy(voci = base.voci + listOf(
            Voce("Campione più basso / più alto", "${Formato.numero(minimo)} / ${Formato.numero(massimo)} bpm"),
            Voce("Campioni nel periodo", Formato.numero(giorni.sumOf { it.n }.toDouble())),
            Voce("Ore coperte in media al giorno", Formato.numero(giorni.map { it.oreCoperte.toDouble() }.average(), 1)),
        ))
    }

    val notturna = analisi(def("fc.notturna", "Frequenza cardiaca durante il sonno", "Campioni di frequenza cardiaca e sessioni di sonno",
        "Per ogni notte: media e minimo dei campioni caduti dentro la sessione di sonno principale. Il minimo notturno è un valore calcolato, non la «frequenza a riposo» della fonte.",
        "bpm", "3 notti con almeno 20 campioni ciascuna", "Dipende dalla frequenza di misura notturna impostata sull'orologio.", "AnalisiCuoreTest.notturna")) { c ->
        if (!c.dati.presente(Metrica.FREQUENZA_CARDIACA)) return@analisi nd("Nessun campione di frequenza cardiaca.", Metrica.FREQUENZA_CARDIACA.nome)
        val notti = c.dati.nottiNelPeriodo(c.periodo)
        if (notti.isEmpty()) return@analisi nd("Nessuna notte di sonno registrata nel periodo.", "Sonno")
        val valori = notti.mapNotNull { n ->
            val camp = c.dati.campioni(Metrica.FREQUENZA_CARDIACA, n.inizio, n.fine)
            if (camp.size < 20) null else Triple(n.giorno, camp.map { it.valore }.average(), camp.minOf { it.valore })
        }
        if (valori.size < 3) return@analisi nd("Solo ${valori.size} notti hanno almeno 20 campioni cardiaci; ne servono 3.")
        val medie = valori.map { it.second }
        val minimi = valori.map { it.third }
        Esito.Disponibile(
            listOf(
                Voce("Media notturna", Formato.conUnita(medie.average(), "bpm"), medie.average(), "bpm"),
                Voce("Minimo notturno medio", Formato.conUnita(minimi.average(), "bpm"), minimi.average(), "bpm"),
                Voce("Notti valutate", "${valori.size} su ${notti.size}"),
            ),
            Copertura(valori.size, c.giorni.size, valori.first().first, valori.last().first),
            listOf(Grafico.Linea("Media notturna", "bpm", c.giorni.map { g -> g to valori.firstOrNull { it.first == g }?.second })),
            principale = minimi.average(),
        )
    }

    val riposo = analisi(def("fc.riposo", "Frequenza a riposo (fonte)", "Frequenza a riposo fornita dalla fonte",
        "Riepilogo dei valori giornalieri e confronto fra la mediana degli ultimi 7 giorni e quella dei 28 giorni precedenti.",
        "bpm", "1 giorno (confronto: 4 valori recenti e 14 di riferimento)", "È il valore calcolato dalla fonte con il suo metodo.", "AnalisiCuoreTest.riposo")) { c ->
        val base = riepilogoMetrica(c, Metrica.FC_RIPOSO, 1)
        if (base !is Esito.Disponibile) return@analisi base
        val s = c.dati.serie(Metrica.FC_RIPOSO)
        val fine = minOf(c.periodo.a, c.oggi)
        val recenti = Tempo.giorni(fine.minusDays(6), fine).mapNotNull { s[it]?.valore }
        val rif = Tempo.giorni(fine.minusDays(34), fine.minusDays(7)).mapNotNull { s[it]?.valore }
        val extra = if (recenti.size >= 4 && rif.size >= 14) listOf(
            Voce("Ultimi 7 giorni vs 28 precedenti", "${Formato.numero(Stat.mediana(recenti), 1)} vs ${Formato.numero(Stat.mediana(rif), 1)} bpm (${Formato.conSegno(Stat.mediana(recenti)!! - Stat.mediana(rif)!!, 1)})"),
        ) else listOf(Voce("Confronto con lo storico", "non disponibile: servono 4 valori negli ultimi 7 giorni e 14 nei 28 precedenti"))
        base.copy(voci = base.voci.map { it.copy(provenienza = if (it.etichetta == "Media giornaliera") Provenienza.CALCOLATO else it.provenienza) } + extra)
    }

    val distribuzione = analisi(def("fc.distribuzione", "Distribuzione dei battiti", "Campioni di frequenza cardiaca",
        "Istogramma dei campioni del periodo in classi di 10 bpm e quota di campioni in ciascuna zona (se configurate).",
        "% dei campioni", "100 campioni", "Conta campioni, non minuti: se l'orologio misura più spesso sotto sforzo, le zone alte risultano sovrarappresentate.", "AnalisiCuoreTest.distribuzione")) { c ->
        val da = Tempo.inizioGiorno(c.periodo.da, c.dati.zona)
        val a = Tempo.inizioGiorno(minOf(c.periodo.a, c.oggi).plusDays(1), c.dati.zona)
        val camp = c.dati.campioni(Metrica.FREQUENZA_CARDIACA, da, a)
        if (camp.size < 100) return@analisi nd("Servono almeno 100 campioni nel periodo; ce ne sono ${camp.size}.")
        val classi = camp.groupBy { (it.valore / 10).toInt() * 10 }.toSortedMap()
        val voci = mutableListOf(Voce("Campioni", Formato.numero(camp.size.toDouble())), Voce("Mediana", Formato.conUnita(Stat.mediana(camp.map { it.valore }), "bpm")))
        val lim = Cardio.limitiZone(Cardio.riferimenti(c.dati, c.oggi), c.dati.pref.metodoZone)
        if (lim != null) {
            val perZona = camp.groupingBy { Cardio.zona(it.valore, lim) }.eachCount()
            (0..5).forEach { z -> voci += Voce(if (z == 0) "Sotto la zona 1" else "Zona $z", Formato.percentuale(100.0 * (perZona[z] ?: 0) / camp.size, 1)) }
        }
        Esito.Disponibile(voci, c.copertura(Metrica.FREQUENZA_CARDIACA),
            listOf(Grafico.Barre("Campioni per classe", "%", classi.map { (k, v) -> "$k" to 100.0 * v.size / camp.size })))
    }

    val zone = analisi(def("fc.zone", "Tempo nelle zone cardiache durante gli allenamenti", "Allenamenti, campioni cardiaci, FC massima (e a riposo per Karvonen)",
        "Zone 1-5 al 50/60/70/80/90% della FC massima o della riserva cardiaca (metodo scelto nelle impostazioni). Il tempo fra due campioni distanti al massimo 2 minuti va alla zona del primo; buchi più lunghi non sono attribuiti.",
        "min", "1 allenamento con campioni; FC massima nota", "La formula di Tanaka ha un errore tipico di circa 10 bpm: una FC massima misurata è preferibile.", "AnalisiCuoreTest.zone")) { c ->
        val rif = Cardio.riferimenti(c.dati, c.oggi)
        val lim = Cardio.limitiZone(rif, c.dati.pref.metodoZone)
            ?: return@analisi nd("Mancano i riferimenti: FC massima ${rif.origineMax}; FC a riposo ${rif.origineRiposo}.", "Anno di nascita o FC massima")
        val sessioni = c.dati.allenamentiNelPeriodo(c.periodo)
        if (sessioni.isEmpty()) return@analisi nd("Nessun allenamento nel periodo.", "Allenamenti")
        val tot = DoubleArray(6)
        var valutate = 0
        sessioni.forEach { s ->
            val t = Cardio.tempoInZone(c.dati.campioni(Metrica.FREQUENZA_CARDIACA, s.inizio, s.fine + 1), s.inizio, s.fine, lim)
            if (t.campioni >= 2) { valutate++; (0..5).forEach { tot[it] += t.minuti[it] } }
        }
        if (valutate == 0) return@analisi nd("Nessun allenamento ha campioni cardiaci registrati durante la sessione.")
        val voci = mutableListOf(
            Voce("Metodo", "${c.dati.pref.metodoZone.nome}; FC massima ${Formato.numero(rif.fcMax)} bpm (${rif.origineMax})" + if (c.dati.pref.metodoZone == MetodoZone.KARVONEN) "; FC a riposo ${Formato.numero(rif.fcRiposo)} (${rif.origineRiposo})" else ""),
            Voce("Allenamenti valutati", "$valutate su ${sessioni.size}"),
        )
        (1..5).forEach { z -> voci += Voce("Zona $z (da ${Formato.numero(lim[z - 1])} bpm)", Formato.durata(tot[z]), tot[z], "min") }
        voci += Voce("Sotto la zona 1", Formato.durata(tot[0]), tot[0], "min")
        Esito.Disponibile(voci, Copertura(valutate, sessioni.size, null, null),
            listOf(Grafico.Barre("Minuti per zona", "min", (0..5).map { (if (it == 0) "<Z1" else "Z$it") to tot[it] })))
    }

    val risposta = analisi(def("fc.allenamento", "Risposta cardiaca agli allenamenti", "Allenamenti e campioni cardiaci (o media/massimo forniti dalla fonte)",
        "Per ogni allenamento: FC media e massima dai campioni della sessione; se mancano, i valori forniti dalla fonte. Percentuale della FC massima di riferimento.",
        "bpm", "1 allenamento", "Con campioni radi la massima può essere sottostimata.", "AnalisiCuoreTest.risposta")) { c ->
        val sessioni = c.dati.allenamentiNelPeriodo(c.periodo)
        if (sessioni.isEmpty()) return@analisi nd("Nessun allenamento nel periodo.", "Allenamenti")
        val rif = Cardio.riferimenti(c.dati, c.oggi)
        val righe = sessioni.mapNotNull { s ->
            val camp = c.dati.campioni(Metrica.FREQUENZA_CARDIACA, s.inizio, s.fine + 1)
            val (media, max, prov) = when {
                camp.size >= 5 -> Triple(camp.map { it.valore }.average(), camp.maxOf { it.valore }, Provenienza.CALCOLATO)
                s.fcMedia != null -> Triple(s.fcMedia, s.fcMax, Provenienza.FONTE)
                else -> return@mapNotNull null
            }
            val pct = if (rif.fcMax != null && max != null) " · ${Formato.percentuale(100 * max / rif.fcMax)} della FC massima" else ""
            Voce("${Sport.nome(s.sport)} del ${Tempo.etichetta(Tempo.giorno(s.inizio, c.dati.zona))}", "media ${Formato.numero(media)} bpm, massima ${Formato.numero(max)} bpm$pct", media, "bpm", prov)
        }
        if (righe.isEmpty()) return@analisi nd("Gli allenamenti del periodo non hanno dati cardiaci.")
        Esito.Disponibile(righe, Copertura(righe.size, sessioni.size, null, null))
    }

    val recupero = analisi(def("fc.recupero", "Recupero dopo l'allenamento", "Campioni cardiaci entro ±15 s dalla fine dell'allenamento e da 60 s (e 120 s) dopo",
        "Recupero a 1 minuto = FC alla fine − FC 60 secondi dopo (e a 2 minuti). Calcolato solo quando entrambi i campioni esistono.",
        "bpm", "1 allenamento con campioni adeguati", "Se ci si ferma prima di chiudere la sessione, o se l'orologio smette di misurare alla chiusura, il valore non è calcolabile o non è attendibile.", "AnalisiCuoreTest.recupero")) { c ->
        val sessioni = c.dati.allenamentiNelPeriodo(c.periodo)
        if (sessioni.isEmpty()) return@analisi nd("Nessun allenamento nel periodo.", "Allenamenti")
        val righe = sessioni.mapNotNull { s ->
            val camp = c.dati.campioni(Metrica.FREQUENZA_CARDIACA, s.fine - 20_000, s.fine + 140_000)
            val r1 = Cardio.recupero(camp, s.fine, 60) ?: return@mapNotNull null
            val r2 = Cardio.recupero(camp, s.fine, 120)
            Voce("${Sport.nome(s.sport)} del ${Tempo.etichetta(Tempo.giorno(s.inizio, c.dati.zona))}",
                "−${Formato.numero(r1.first - r1.second)} bpm in 1 minuto" + (r2?.let { " · −${Formato.numero(it.first - it.second)} in 2 minuti" } ?: ""), r1.first - r1.second, "bpm")
        }
        if (righe.isEmpty()) return@analisi nd("Nessun allenamento ha campioni cardiaci sufficienti alla fine e nel minuto successivo: l'A58 di norma smette di misurare fitto quando la sessione si chiude.")
        Esito.Disponibile(righe, Copertura(righe.size, sessioni.size, null, null))
    }

    val storico = analisi(def("fc.storico", "Confronto con il tuo storico", "Media giornaliera della frequenza cardiaca",
        "Posizione della media del periodo rispetto alle medie giornaliere dei 90 giorni precedenti (percentile empirico).",
        "percentile", "3 giorni nel periodo e 30 nei 90 precedenti", "Le medie giornaliere risentono di quanto si è indossato l'orologio e di quanti allenamenti si sono fatti.", "AnalisiCuoreTest.storico")) { c ->
        c.richiedi(Metrica.FREQUENZA_CARDIACA, 3)?.let { return@analisi it }
        val ora = c.dati.osservati(Metrica.FREQUENZA_CARDIACA, c.giorni).average()
        val rif = c.dati.osservati(Metrica.FREQUENZA_CARDIACA, Tempo.giorni(c.periodo.da.minusDays(90), c.periodo.da.minusDays(1)))
        if (rif.size < 30) return@analisi nd("Servono 30 giorni di storico nei 90 precedenti; ce ne sono ${rif.size}.")
        val pct = 100.0 * rif.count { it <= ora } / rif.size
        Esito.Disponibile(
            listOf(
                Voce("Media del periodo", Formato.conUnita(ora, "bpm", 1), ora, "bpm"),
                Voce("Mediana dei 90 giorni precedenti", Formato.conUnita(Stat.mediana(rif), "bpm", 1)),
                Voce("Percentile nel tuo storico", Formato.numero(pct), pct, "°"),
            ),
            c.copertura(Metrica.FREQUENZA_CARDIACA), principale = pct,
        )
    }

    val hrv = analisi(def("fc.hrv", "Variabilità cardiaca (HRV) della fonte", "HRV RMSSD fornita dalla fonte",
        "Riepilogo dei valori forniti. L'app non calcola la HRV dai campioni di frequenza: servirebbero gli intervalli fra battiti (RR), che la fonte non esporta.",
        "ms", "1 giorno", "Valori molto sensibili a orario e condizioni di misura: confrontare solo misure notturne o della stessa fascia.", "AnalisiCuoreTest.hrv")) { c ->
        riepilogoMetrica(c, Metrica.HRV_RMSSD, 1)
    }

    val elenco = listOf(giornaliera, notturna, riposo, distribuzione, zone, risposta, recupero, storico, hrv)

    /** Per l'uso esterno (esportazioni): FC media e massima di una sessione dai campioni. */
    fun fcSessione(d: Dati, s: Allenamento): Pair<Double, Double>? {
        val camp = d.campioni(Metrica.FREQUENZA_CARDIACA, s.inizio, s.fine + 1)
        return if (camp.size >= 5) camp.map { it.valore }.average() to camp.maxOf { it.valore } else null
    }
}
