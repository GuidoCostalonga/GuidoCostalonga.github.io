package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.FaseSonno
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.NotteSonno
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object AnalisiSonno {
    private const val MANCANTI_SONNO = "Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti."

    private fun def(id: String, titolo: String, dati: String, metodo: String, unita: String, minimo: String, limiti: String, prova: String) =
        Definizione(id, titolo, Sezione.SONNO, dati, metodo, unita, minimo, MANCANTI_SONNO, limiti, prova)

    private fun Contesto.notti(): List<NotteSonno> = dati.nottiNelPeriodo(periodo).filter { it.giorno <= oggi }

    private fun Contesto.coperturaNotti(n: List<NotteSonno>) =
        Copertura(n.size, giorni.size, n.firstOrNull()?.giorno, n.lastOrNull()?.giorno, nota = if (parziale) "Periodo in corso." else null)

    private fun Contesto.richiediNotti(minimo: Int): Pair<List<NotteSonno>, Esito.NonDisponibile?> {
        if (dati.sonni.isEmpty()) return emptyList<NotteSonno>() to nd("Nessuna sessione di sonno nell'archivio.", "Sonno")
        val n = notti()
        return n to if (n.size < minimo) nd("Servono almeno $minimo notti nel periodo; ce ne sono ${n.size}.") else null
    }

    fun minutoAddormentamento(n: NotteSonno, d: Dati) = Tempo.minutoDelGiorno(n.inizio, d.zona, n.scartoSec)
    fun minutoRisveglio(n: NotteSonno, d: Dati) = Tempo.minutoDelGiorno(n.fine, d.zona, n.scartoSec)

    /** Punto medio del sonno in minuti dalla mezzanotte (può superare 1440 e viene riportato nell'intervallo). */
    fun puntoMedio(n: NotteSonno, d: Dati) = Tempo.minutoDelGiorno(n.inizio + (n.fine - n.inizio) / 2, d.zona, n.scartoSec)

    val durata = analisi(def("sonno.durata", "Durata del sonno", "Sessioni di sonno (e fasi, se presenti)",
        "Minuti di sonno per notte: somma delle fasi di sonno quando la fonte le fornisce, altrimenti durata della sessione principale. Media, mediana, minimo, massimo, deviazione standard; tempo a letto; pisolini a parte.",
        "min", "1 notte", "Senza fasi, il tempo sveglio a letto viene contato come sonno.", "AnalisiSonnoTest.durata")) { c ->
        val (n, err) = c.richiediNotti(1); err?.let { return@analisi it }
        val x = n.map { it.minutiSonno }
        val voci = mutableListOf(
            Voce("Media per notte", Formato.durata(x.average()), x.average(), "min"),
            Voce("Mediana", Formato.durata(Stat.mediana(x)), Stat.mediana(x), "min"),
            Voce("Notte più breve / più lunga", "${Formato.durata(x.min())} / ${Formato.durata(x.max())}"),
            Voce("Tempo medio a letto", Formato.durata(n.map { it.minutiInLetto }.average())),
        )
        Stat.devStd(x)?.let { voci += Voce("Deviazione standard", Formato.durata(it), it, "min") }
        val pis = n.sumOf { it.pisolini }
        if (pis > 0) voci += Voce("Pisolini", "$pis, in totale ${Formato.durata(n.sumOf { it.minutiPisolini })}")
        val fasiOrdine = listOf(FaseSonno.PROFONDO, FaseSonno.LEGGERO, FaseSonno.REM, FaseSonno.SVEGLIO)
        val conFasi = n.any { it.haFasi }
        val etichette = c.giorni.map { Tempo.etichettaBreve(it) }
        val perGiorno = c.giorni.map { g -> n.firstOrNull { it.giorno == g } }
        Esito.Disponibile(
            voci, c.coperturaNotti(n),
            listOf(Grafico.Barre("Sonno per notte", "ore", etichette.zip(perGiorno.map { it?.minutiSonno?.div(60) }),
                if (conFasi) fasiOrdine.map { f -> f.nome to perGiorno.map { nn -> nn?.takeIf { it.haFasi }?.let { (it.minutiPerFase[f] ?: 0.0) / 60 } } } else emptyList(),
                riferimento = c.dati.pref.obiettivoSonnoMin / 60.0)),
            listOf(if (conFasi) "Le fasi sono quelle classificate dalla fonte." else "La fonte non fornisce le fasi: la durata è il tempo della sessione.", MANCANTI_SONNO),
            principale = x.average(),
        )
    }

    val orari = analisi(def("sonno.orari", "Orari e regolarità", "Sessioni di sonno",
        "Orario medio di addormentamento, risveglio e punto medio con media circolare (gestisce la mezzanotte); regolarità come deviazione standard circolare in minuti.",
        "hh:mm, min", "3 notti", "La sessione inizia quando la fonte rileva il sonno, non quando si va a letto.", "AnalisiSonnoTest.orari")) { c ->
        val (n, err) = c.richiediNotti(3); err?.let { return@analisi it }
        val a = n.map { minutoAddormentamento(it, c.dati) }
        val r = n.map { minutoRisveglio(it, c.dati) }
        val m = n.map { puntoMedio(it, c.dati) }
        Esito.Disponibile(
            listOf(
                Voce("Addormentamento medio", Formato.orario(Stat.mediaOraria(a))),
                Voce("Risveglio medio", Formato.orario(Stat.mediaOraria(r))),
                Voce("Punto medio del sonno", Formato.orario(Stat.mediaOraria(m))),
                Voce("Variabilità dell'addormentamento", "± ${Formato.numero(Stat.devStdOraria(a))} min", Stat.devStdOraria(a), "min"),
                Voce("Variabilità del risveglio", "± ${Formato.numero(Stat.devStdOraria(r))} min", Stat.devStdOraria(r), "min"),
            ),
            c.coperturaNotti(n), principale = Stat.devStdOraria(m),
        )
    }

    val fasi = analisi(def("sonno.fasi", "Fasi del sonno", "Sessioni con fasi (leggero, profondo, REM, veglia)",
        "Minuti medi e percentuale sul sonno totale di ciascuna fase, sulle sole notti con fasi.",
        "min, %", "3 notti con fasi", "La classificazione delle fasi da polso è una stima della fonte, meno precisa della polisonnografia.", "AnalisiSonnoTest.fasi")) { c ->
        val (tutte, err) = c.richiediNotti(1); err?.let { return@analisi it }
        val n = tutte.filter { it.haFasi }
        if (n.size < 3) return@analisi nd("Solo ${n.size} notti hanno le fasi; ne servono 3.")
        val tot = n.sumOf { it.minutiSonno }
        val voci = listOf(FaseSonno.LEGGERO, FaseSonno.PROFONDO, FaseSonno.REM, FaseSonno.SONNO).mapNotNull { f ->
            val minuti = n.sumOf { it.minutiPerFase[f] ?: 0.0 }
            if (minuti == 0.0) null else Voce(f.nome, "${Formato.durata(minuti / n.size)} a notte (${Formato.percentuale(100 * minuti / tot)})", 100 * minuti / tot, "%", Provenienza.FONTE)
        }
        Esito.Disponibile(voci, c.coperturaNotti(n))
    }

    val risvegli = analisi(def("sonno.risvegli", "Risvegli ed efficienza", "Sessioni con fasi, compresa la veglia",
        "Risvegli = passaggi da sonno a veglia seguiti da altro sonno; minuti svegli a letto; efficienza = sonno / tempo a letto.",
        "numero, min, %", "3 notti con fasi", "I brevi risvegli sotto la risoluzione della fonte non vengono visti.", "AnalisiSonnoTest.risvegli")) { c ->
        val (tutte, err) = c.richiediNotti(1); err?.let { return@analisi it }
        val n = tutte.filter { it.haFasi && it.risvegli != null }
        if (n.size < 3) return@analisi nd("Solo ${n.size} notti hanno le fasi con la veglia; ne servono 3.")
        val ris = n.map { it.risvegli!!.toDouble() }
        val eff = n.mapNotNull { it.efficienza }
        Esito.Disponibile(
            listOf(
                Voce("Risvegli medi per notte", Formato.numero(ris.average(), 1), ris.average()),
                Voce("Minuti svegli a letto", Formato.durata(n.mapNotNull { it.minutiVeglia }.average())),
                Voce("Efficienza media", Formato.percentuale(eff.average()), eff.average(), "%"),
            ),
            c.coperturaNotti(n),
        )
    }

    val settimanaWeekend = analisi(def("sonno.weekend", "Giorni lavorativi e fine settimana", "Sessioni di sonno",
        "Durata e punto medio nelle notti che terminano da lunedì a venerdì e in quelle che terminano sabato e domenica. Lo scarto fra i punti medi è il cosiddetto «jet lag sociale».",
        "min", "3 notti feriali e 2 festive", "Le festività infrasettimanali sono considerate giorni lavorativi.", "AnalisiSonnoTest.weekend")) { c ->
        val (n, err) = c.richiediNotti(5); err?.let { return@analisi it }
        val fer = n.filter { !Tempo.weekend(it.giorno) }
        val fes = n.filter { Tempo.weekend(it.giorno) }
        if (fer.size < 3 || fes.size < 2) return@analisi nd("Servono almeno 3 notti feriali e 2 festive (ora ${fer.size} e ${fes.size}).")
        val mFer = Stat.mediaOraria(fer.map { puntoMedio(it, c.dati) })!!
        val mFes = Stat.mediaOraria(fes.map { puntoMedio(it, c.dati) })!!
        var diff = mFes - mFer
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440
        Esito.Disponibile(
            listOf(
                Voce("Durata nei giorni lavorativi", Formato.durata(fer.map { it.minutiSonno }.average())),
                Voce("Durata nel fine settimana", Formato.durata(fes.map { it.minutiSonno }.average())),
                Voce("Punto medio lavorativi / fine settimana", "${Formato.orario(mFer)} / ${Formato.orario(mFes)}"),
                Voce("Scarto dei punti medi (jet lag sociale)", Formato.durata(diff), diff, "min"),
            ),
            c.coperturaNotti(n), principale = diff,
        )
    }

    val obiettivo = analisi(def("sonno.obiettivo", "Scostamento dall'obiettivo personale", "Sessioni di sonno; obiettivo dalle impostazioni",
        "Differenza media fra sonno e obiettivo; notti sotto obiettivo; saldo cumulato degli ultimi 7 e 14 giorni (somma delle differenze sulle sole notti registrate).",
        "min", "1 notte", "Il «debito» è solo un calcolo rispetto all'obiettivo che hai scelto: non è una misura clinica né un bisogno fisiologico accertato.", "AnalisiSonnoTest.obiettivo")) { c ->
        val (n, err) = c.richiediNotti(1); err?.let { return@analisi it }
        val ob = c.dati.pref.obiettivoSonnoMin.toDouble()
        val diff = n.map { it.minutiSonno - ob }
        val fine = minOf(c.periodo.a, c.oggi)
        fun saldo(giorni: Int) = c.dati.notti().filterKeys { it in fine.minusDays(giorni - 1L)..fine }.values.sumOf { it.minutiSonno - ob }
        Esito.Disponibile(
            listOf(
                Voce("Obiettivo", Formato.durata(ob)),
                Voce("Scostamento medio", Formato.durata(diff.average()), diff.average(), "min"),
                Voce("Notti sotto obiettivo", "${diff.count { it < 0 }} su ${n.size}"),
                Voce("Saldo ultimi 7 giorni (calcolo)", Formato.durata(saldo(7))),
                Voce("Saldo ultimi 14 giorni (calcolo)", Formato.durata(saldo(14))),
            ),
            c.coperturaNotti(n), principale = diff.average(),
        )
    }

    /**
     * Indice di regolarità del sonno (SRI, Phillips et al. 2017): probabilità
     * che lo stato sonno/veglia sia lo stesso a 24 ore di distanza, minuto per
     * minuto, riscalata fra −100 e 100. Si usano solo coppie di giorni
     * consecutivi entrambi con una notte registrata.
     */
    fun sri(d: Dati, giorni: List<LocalDate>): Pair<Double, Int>? {
        val notti = d.notti()
        val disponibili = giorni.filter { notti[it] != null && notti[it.plusDays(1)] != null }
        if (disponibili.isEmpty()) return null
        var uguali = 0L
        var totali = 0L
        for (g in disponibili) {
            // Finestra di 24 h a partire dalle 12:00 del giorno precedente la notte g.
            val base = Tempo.inizioGiorno(g.minusDays(1), d.zona) + 12 * 3_600_000L
            val n1 = notti[g]!!
            val n2 = notti[g.plusDays(1)]!!
            for (minuto in 0 until 1440) {
                val t = base + minuto * 60_000L
                val a = t >= n1.inizio && t < n1.fine
                val b = (t + 86_400_000L).let { it >= n2.inizio && it < n2.fine }
                if (a == b) uguali++
                totali++
            }
        }
        return (-100.0 + 200.0 * uguali / totali) to disponibili.size
    }

    val regolaritaSri = analisi(def("sonno.sri", "Indice di regolarità del sonno (SRI)", "Sessioni di sonno di notti consecutive",
        "Sleep Regularity Index (Phillips et al., 2017): percentuale di minuti in cui lo stato sonno/veglia coincide a 24 ore di distanza, riscalata in −100…100 (100 = orari identici).",
        "punti", "7 coppie di notti consecutive", "Usa solo inizio e fine della sessione principale (non le fasi): i risvegli notturni non incidono. Le coppie con una notte mancante sono escluse.", "AnalisiSonnoTest.sri")) { c ->
        val (_, err) = c.richiediNotti(2); err?.let { return@analisi it }
        val r = sri(c.dati, c.giorni) ?: return@analisi nd("Nessuna coppia di notti consecutive.")
        if (r.second < 7) return@analisi nd("Servono 7 coppie di notti consecutive; ce ne sono ${r.second}.")
        Esito.Disponibile(listOf(Voce("SRI", Formato.numero(r.first), r.first, "punti"), Voce("Coppie di notti", "${r.second}")),
            c.coperturaNotti(c.notti()), principale = r.first)
    }

    val tendenza = analisi(def("sonno.tendenza", "Tendenza della durata del sonno", "Sessioni di sonno",
        "Pendenza di Theil–Sen e test di Mann–Kendall corretto, sulla durata per notte.", "min/settimana", "14 notti",
        "Descrive il periodo, non prevede il futuro.", "AnalisiSonnoTest.tendenza")) { c ->
        val (n, err) = c.richiediNotti(14); err?.let { return@analisi it }
        val x = n.map { ChronoUnit.DAYS.between(c.periodo.da, it.giorno).toDouble() }
        val t = Stat.tendenza(x, n.map { it.minutiSonno }) ?: return@analisi nd("Calcolo non possibile.")
        Esito.Disponibile(
            listOf(
                Voce("Andamento", if (t.p >= 0.05) "nessuna tendenza netta" else if (t.pendenza > 0) "in aumento" else "in diminuzione"),
                Voce("Variazione stimata", "${Formato.conSegno(t.pendenza * 7, 1)} min a settimana", t.pendenza * 7, "min/settimana"),
                Voce("p-value", Formato.pValore(t.p), t.p),
            ),
            c.coperturaNotti(n), principale = t.pendenza * 7,
        )
    }

    val elenco = listOf(durata, orari, fasi, risvegli, settimanaWeekend, obiettivo, regolaritaSri, tendenza)
}
