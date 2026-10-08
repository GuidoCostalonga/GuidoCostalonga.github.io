package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoPeriodo

/**
 * Indici descrittivi 0-100 con formula dichiarata e componenti consultabili.
 * Non sono punteggi clinicamente validati; si calcolano solo con i dati
 * necessari e la media riguarda le sole componenti disponibili.
 */
object Indici {
    private const val LIMITI = "Indice descrittivo costruito da questa app per riassumere i tuoi dati: non è validato clinicamente e non va confrontato con punteggi di altre app."

    private fun def(id: String, titolo: String, dati: String, metodo: String, minimo: String, prova: String) =
        Definizione(id, titolo, Sezione.INDICI, dati, metodo, "punti 0-100", minimo,
            "Una componente senza dati sufficienti viene esclusa e dichiarata; non viene mai posta a zero.", LIMITI, prova)

    data class Componente(val nome: String, val punti: Double?, val dettaglio: String)

    private fun esito(c: Contesto, comp: List<Componente>, minimo: Int): Esito {
        val ok = comp.filter { it.punti != null }
        if (ok.size < minimo) return nd("Servono almeno $minimo componenti con dati sufficienti; disponibili: ${ok.size}. " + comp.filter { it.punti == null }.joinToString("; ") { "${it.nome}: ${it.dettaglio}" })
        val tot = ok.map { it.punti!! }.average()
        return Esito.Disponibile(
            listOf(Voce("Indice", "${Formato.numero(tot)} su 100", tot, "punti")) + comp.map { Voce(it.nome, if (it.punti == null) "non disponibile — ${it.dettaglio}" else "${Formato.numero(it.punti)} punti · ${it.dettaglio}", it.punti) },
            Copertura(ok.size, comp.size, null, null, nota = "Componenti disponibili: ${ok.size} su ${comp.size}."),
            note = listOf(LIMITI), principale = tot,
        )
    }

    val regolarita = analisi(def("ind.regolarita", "Indice di regolarità",
        "Orari del sonno (almeno 7 notti) e passi giornalieri (almeno 14 giorni)",
        "Media delle componenti disponibili. Sonno: 100 × max(0; 1 − DS circolare del punto medio / 120 min). Attività: 100 × max(0; 1 − CV dei passi / 100%).",
        "1 componente", "IndiciTest.regolarita")) { c ->
        val notti = c.dati.nottiNelPeriodo(c.periodo)
        val sd = if (notti.size >= 7) Stat.devStdOraria(notti.map { AnalisiSonno.puntoMedio(it, c.dati) }) else null
        val passi = c.dati.osservati(Metrica.PASSI, c.giorni)
        val cv = if (passi.size >= 14) Stat.cv(passi) else null
        esito(c, listOf(
            Componente("Regolarità del sonno", sd?.let { 100 * maxOf(0.0, 1 - it / 120) }, sd?.let { "punto medio ± ${Formato.numero(it)} min" } ?: "servono 7 notti (ora ${notti.size})"),
            Componente("Regolarità dell'attività", cv?.let { 100 * maxOf(0.0, 1 - it / 100) }, cv?.let { "CV ${Formato.percentuale(it)}" } ?: "servono 14 giorni di passi (ora ${passi.size})"),
        ), 1)
    }

    val attivita = analisi(def("ind.attivita", "Indice di attività",
        "Passi (almeno 7 giorni) e, se ne hai mai registrati, allenamenti",
        "Media delle componenti. Obiettivo: % di giorni con passi ≥ obiettivo. Allenamento: 100 × min(1; minuti settimanali medi / 150), riferimento OMS 2020.",
        "1 componente", "IndiciTest.attivita")) { c ->
        val s = c.dati.serie(Metrica.PASSI)
        val con = c.giorni.mapNotNull { s[it]?.valore }
        val ob = c.dati.pref.obiettivoPassi
        val compObiettivo = if (con.size >= 7) 100.0 * con.count { it >= ob } / con.size else null
        val settimane = c.giorni.map { Periodo.di(TipoPeriodo.SETTIMANA, it).da }.distinct()
        val compAll = if (c.dati.allenamenti.isNotEmpty() && c.giorni.size >= 7) {
            val minuti = c.dati.allenamentiNelPeriodo(c.periodo).sumOf { it.durataMs / 60_000.0 } / settimane.size
            100 * minOf(1.0, minuti / 150)
        } else null
        esito(c, listOf(
            Componente("Obiettivo di passi", compObiettivo, compObiettivo?.let { "${Formato.percentuale(it)} dei giorni sopra $ob passi" } ?: "servono 7 giorni di passi (ora ${con.size})"),
            Componente("Minuti di allenamento", compAll, if (compAll != null) "rispetto a 150 min/settimana" else "nessun allenamento nell'archivio o periodo inferiore a 7 giorni"),
        ), 1)
    }

    val recupero = analisi(def("ind.recupero", "Indice di recupero",
        "Sonno degli ultimi 7 giorni; FC a riposo (o minima notturna) e HRV degli ultimi 7 giorni rispetto ai 28 precedenti",
        "Media delle componenti. Sonno: 100 × min(1; sonno medio / obiettivo). FC: 100 − min(100; max(0; scarto dalla mediana di riferimento in bpm) × 10). HRV: 100 × min(1; mediana recente / mediana di riferimento). Riferimenti calcolati sui 28 giorni precedenti la settimana finale del periodo.",
        "2 componenti", "IndiciTest.recupero")) { c ->
        val fine = minOf(c.periodo.a, c.oggi)
        val recenti = Tempo.giorni(fine.minusDays(6), fine)
        val riferimento = Tempo.giorni(fine.minusDays(34), fine.minusDays(7))
        val notti = recenti.mapNotNull { c.dati.notti()[it]?.minutiSonno }
        val compSonno = if (notti.size >= 4) 100 * minOf(1.0, notti.average() / c.dati.pref.obiettivoSonnoMin) else null
        val fcFonte = c.dati.serie(Metrica.FC_RIPOSO)
        val fcR = recenti.mapNotNull { fcFonte[it]?.valore }
        val fcB = riferimento.mapNotNull { fcFonte[it]?.valore }
        val compFc = if (fcR.size >= 4 && fcB.size >= 14) {
            val diff = Stat.mediana(fcR)!! - Stat.mediana(fcB)!!
            100 - minOf(100.0, maxOf(0.0, diff) * 10)
        } else null
        val hrv = c.dati.serie(Metrica.HRV_RMSSD)
        val hR = recenti.mapNotNull { hrv[it]?.valore }
        val hB = riferimento.mapNotNull { hrv[it]?.valore }
        val compHrv = if (hR.size >= 4 && hB.size >= 14) 100 * minOf(1.0, Stat.mediana(hR)!! / Stat.mediana(hB)!!) else null
        esito(c, listOf(
            Componente("Sonno rispetto all'obiettivo", compSonno, if (compSonno != null) "media ${Formato.durata(notti.average())} negli ultimi 7 giorni" else "servono 4 notti negli ultimi 7 giorni (ora ${notti.size})"),
            Componente("Frequenza a riposo rispetto al tuo riferimento", compFc, if (compFc != null) "mediana ${Formato.numero(Stat.mediana(fcR), 1)} vs ${Formato.numero(Stat.mediana(fcB), 1)} bpm" else "servono 4 valori recenti e 14 di riferimento della fonte"),
            Componente("HRV rispetto al tuo riferimento", compHrv, if (compHrv != null) "mediana ${Formato.numero(Stat.mediana(hR))} vs ${Formato.numero(Stat.mediana(hB))} ms" else "HRV non disponibile dalla fonte o storico insufficiente"),
        ), 2)
    }

    val elenco = listOf(regolarita, attivita, recupero)
}

object Qualita {
    val copertura = analisi(Definizione("qual.copertura", "Copertura e qualità dei dati", Sezione.QUALITA, "Tutto l'archivio",
        "Per ogni grandezza: giorni con dati nel periodo, ore coperte in media, valori sospetti (fuori dall'intervallo plausibile), misure sovrapposte scartate, origini presenti. Per il sonno: notti registrate e notti con fasi.",
        "giorni, %", "nessuno", "I giorni senza dati sono dichiarati, non stimati.", "Un dato presente non è necessariamente corretto: i controlli riconoscono solo valori impossibili o sovrapposti.", "QualitaTest.copertura")) { c ->
        val voci = Metrica.entries.filter { c.dati.presente(it) }.map { m ->
            val s = c.dati.serie(m)
            val g = c.giorni.mapNotNull { s[it] }.filter { it.valore != null }
            val sospetti = c.giorni.sumOf { s[it]?.sospetti ?: 0 }
            val scartate = c.giorni.sumOf { s[it]?.sovrapposteScartate ?: 0 }
            val origini = c.giorni.flatMap { listOfNotNull(s[it]?.origine) + (s[it]?.altreOrigini ?: emptyList()) }.distinct()
            Voce(m.nome, "${g.size}/${c.giorni.size} giorni (${Formato.percentuale(if (c.giorni.isEmpty()) 0.0 else 100.0 * g.size / c.giorni.size)})" +
                (if (g.isNotEmpty()) ", ${Formato.numero(g.map { it.oreCoperte.toDouble() }.average(), 1)} ore coperte" else "") +
                (if (sospetti > 0) ", $sospetti sospetti" else "") + (if (scartate > 0) ", $scartate sovrapposte scartate" else "") +
                (if (origini.isNotEmpty()) " · origini: ${origini.joinToString()}" else ""),
                if (c.giorni.isEmpty()) 0.0 else 100.0 * g.size / c.giorni.size, "%")
        }.toMutableList()
        val notti = c.dati.nottiNelPeriodo(c.periodo)
        if (c.dati.sonni.isNotEmpty()) voci += Voce("Sonno", "${notti.size}/${c.giorni.size} notti, ${notti.count { it.haFasi }} con fasi")
        if (c.dati.allenamenti.isNotEmpty()) voci += Voce("Allenamenti", "${c.dati.allenamentiNelPeriodo(c.periodo).size} nel periodo")
        if (voci.isEmpty()) return@analisi nd("L'archivio è vuoto: collega Health Connect o importa un file.")
        Esito.Disponibile(voci, Copertura(voci.size, voci.size, c.periodo.da, c.periodo.a, nota = if (c.parziale) "Periodo in corso." else null))
    }

    val elenco = listOf(copertura)
}
