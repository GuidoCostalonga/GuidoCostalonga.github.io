package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Combinazione
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun analisi(def: Definizione, f: (Contesto) -> Esito): Analisi = object : Analisi(def) {
    override fun calcola(c: Contesto): Esito = f(c)
}

internal const val MANCANTI_STD = "I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero."

/**
 * Riepilogo statistico di una metrica giornaliera: usato da più sezioni per non
 * duplicare formule. Totale solo per le grandezze sommabili.
 */
internal fun riepilogoMetrica(c: Contesto, m: Metrica, minimo: Int, riferimento: Double? = null, etichettaRif: String? = null): Esito {
    c.richiedi(m, minimo)?.let { return it }
    val punti = c.giorni.map { g -> g to (if (g > c.oggi) null else c.dati.serie(m)[g]?.valore) }
    val x = punti.mapNotNull { it.second }
    val d = m.decimali
    val voci = mutableListOf<Voce>()
    val prov = if (m.combinazione == Combinazione.SOMMA) Provenienza.CALCOLATO else Provenienza.CALCOLATO
    if (m.combinazione == Combinazione.SOMMA) voci += Voce("Totale", Formato.conUnita(x.sum(), m.unita, d), x.sum(), m.unita, prov)
    voci += Voce("Media giornaliera", Formato.conUnita(Stat.media(x), m.unita, d), Stat.media(x), m.unita)
    voci += Voce("Mediana", Formato.conUnita(Stat.mediana(x), m.unita, d), Stat.mediana(x), m.unita)
    voci += Voce("Minimo", Formato.conUnita(x.min(), m.unita, d), x.min(), m.unita)
    voci += Voce("Massimo", Formato.conUnita(x.max(), m.unita, d), x.max(), m.unita)
    if (x.size >= 5) {
        voci += Voce("10° e 90° percentile", "${Formato.numero(Stat.quantile(x, 0.1), d)} – ${Formato.numero(Stat.quantile(x, 0.9), d)} ${m.unita}")
        voci += Voce("Deviazione standard", Formato.conUnita(Stat.devStd(x), m.unita, d), Stat.devStd(x), m.unita)
        voci += Voce("Coefficiente di variazione", Formato.percentuale(Stat.cv(x)), Stat.cv(x), "%")
    }
    val mm = Stat.mediaMobile(punti.map { it.second }, 7, 4)
    val grafici = mutableListOf<Grafico>(Grafico.Linea("${m.nome} per giorno", m.unita, punti, mm, riferimento, etichettaRif))
    val note = mutableListOf(MANCANTI_STD)
    if (m.proprietaria) note += "Punteggio calcolato dalla fonte con un metodo non pubblico: va letto come indicazione di tendenza, non come grandezza clinica."
    return Esito.Disponibile(voci, c.copertura(m), grafici, note, principale = Stat.media(x))
}

object AnalisiAttivita {
    private fun def(id: String, titolo: String, dati: String, metodo: String, unita: String, minimo: String, limiti: String, prova: String, mancanti: String = MANCANTI_STD) =
        Definizione(id, titolo, Sezione.ATTIVITA, dati, metodo, unita, minimo, mancanti, limiti, prova)

    val passi = analisi(def("att.passi", "Passi nel periodo", "Passi (intervalli della fonte)",
        "Somma per giorno locale degli intervalli della fonte scelta; intervalli a cavallo della mezzanotte ripartiti in proporzione alla durata. Totale, media, mediana, minimo, massimo, percentili (tipo 7), deviazione standard, coefficiente di variazione, media mobile a 7 giorni (almeno 4 giorni presenti).",
        "passi", "1 giorno con dati (percentili e dispersione da 5)", "I passi dipendono dall'indossare l'orologio: un giorno con l'orologio sul comodino sembra un giorno poco attivo.", "AnalisiAttivitaTest.passi")) { c ->
        riepilogoMetrica(c, Metrica.PASSI, 1, c.dati.pref.obiettivoPassi.toDouble(), "Obiettivo")
    }

    val distanza = analisi(def("att.distanza", "Distanza", "Distanza (intervalli della fonte)", "Come per i passi, in metri; mostrata in km.", "m", "1 giorno con dati",
        "Distanza stimata dalla fonte (passo medio o GPS): non è una misura topografica.", "AnalisiAttivitaTest.distanza")) { c ->
        riepilogoMetrica(c, Metrica.DISTANZA, 1)
    }

    val calorie = analisi(def("att.calorie", "Calorie attive", "Calorie attive della fonte", "Somma giornaliera delle calorie attive fornite dalla fonte.", "kcal", "1 giorno con dati",
        "Le calorie sono una stima della fonte, con errori individuali anche ampi.", "AnalisiAttivitaTest.calorie")) { c ->
        riepilogoMetrica(c, Metrica.CALORIE_ATTIVE, 1)
    }

    val minutiAttivi = analisi(def("att.minuti", "Minuti attivi", "Minuti attivi della fonte", "Somma giornaliera dei minuti attivi forniti dalla fonte (solo importazione da file: Health Connect non ha questo tipo).", "min", "1 giorno con dati",
        "La definizione di «minuto attivo» è della fonte.", "AnalisiAttivitaTest.minuti")) { c ->
        riepilogoMetrica(c, Metrica.MINUTI_ATTIVI, 1)
    }

    val piani = analisi(def("att.piani", "Piani e dislivello", "Piani saliti della fonte", "Somma giornaliera.", "piani", "1 giorno con dati",
        "L'A58 non dichiara un barometro: il dato può mancare del tutto.", "AnalisiAttivitaTest.piani")) { c ->
        riepilogoMetrica(c, Metrica.PIANI, 1)
    }

    val confronto = analisi(def("att.confronto", "Confronto con il periodo precedente", "Passi",
        "Media giornaliera dei passi nel periodo e nel periodo equivalente precedente (stessa durata) e dello stesso periodo dell'anno prima. Differenza assoluta e percentuale; intervallo di confidenza al 95% della differenza con bootstrap (2000 ricampionamenti, seme fisso).",
        "passi/giorno", "3 giorni con dati in entrambi i periodi", "Con pochi giorni l'intervallo è ampio: se comprende lo zero la differenza non è distinguibile dalla normale variabilità.", "AnalisiAttivitaTest.confronto")) { c ->
        confrontoPeriodi(c, Metrica.PASSI)
    }

    val giornoSettimana = analisi(def("att.settimana", "Andamento per giorno della settimana", "Passi",
        "Media dei passi per ciascun giorno della settimana nel periodo, con il numero di giorni usati.", "passi", "14 giorni con dati",
        "Con meno di 3 osservazioni per giorno la media è poco stabile; il numero è indicato accanto.", "AnalisiAttivitaTest.settimana")) { c ->
        c.richiedi(Metrica.PASSI, 14)?.let { return@analisi it }
        val s = c.dati.serie(Metrica.PASSI)
        val per = (1..7).map { dow -> c.giorni.filter { it.dayOfWeek.value == dow }.mapNotNull { s[it]?.valore } }
        val voci = per.mapIndexed { i, v -> Voce(Tempo.nomiGiorni[i].replaceFirstChar { it.uppercase() }, "${Formato.numero(Stat.media(v))} passi (n = ${v.size})", Stat.media(v), "passi") }
        val feriali = c.giorni.filter { !Tempo.weekend(it) }.mapNotNull { s[it]?.valore }
        val festivi = c.giorni.filter { Tempo.weekend(it) }.mapNotNull { s[it]?.valore }
        Esito.Disponibile(
            voci + Voce("Lunedì-venerdì / sabato-domenica", "${Formato.numero(Stat.media(feriali))} / ${Formato.numero(Stat.media(festivi))} passi"),
            c.copertura(Metrica.PASSI),
            listOf(Grafico.Barre("Media per giorno della settimana", "passi", per.mapIndexed { i, v -> Tempo.nomiGiorni[i].take(3) to Stat.media(v) })),
            listOf(MANCANTI_STD),
        )
    }

    val obiettivo = analisi(def("att.obiettivo", "Obiettivo personale e serie di giorni attivi", "Passi; obiettivo dalle impostazioni",
        "Giorni con passi ≥ obiettivo sul totale dei giorni con dati; serie attuale e serie più lunga di giorni consecutivi con obiettivo raggiunto.",
        "giorni", "1 giorno con dati", "Un giorno senza dati interrompe la serie (non si può sapere se l'obiettivo era stato raggiunto). Oggi non interrompe la serie finché non è finito.",
        "AnalisiAttivitaTest.obiettivo")) { c ->
        c.richiedi(Metrica.PASSI, 1)?.let { return@analisi it }
        val soglia = (c.dati.pref.sogliaGiornoAttivo ?: c.dati.pref.obiettivoPassi).toDouble()
        val s = c.dati.serie(Metrica.PASSI)
        val conDati = c.giorni.filter { s[it]?.valore != null }
        val raggiunti = conDati.count { s[it]!!.valore!! >= soglia }
        val (attuale, record) = serie(c.dati.serie(Metrica.PASSI).mapValues { it.value.valore }, soglia, c.oggi)
        Esito.Disponibile(
            listOf(
                Voce("Obiettivo", Formato.conUnita(soglia, "passi")),
                Voce("Giorni con obiettivo raggiunto", "$raggiunti su ${conDati.size} (${Formato.percentuale(100.0 * raggiunti / conDati.size)})", 100.0 * raggiunti / conDati.size, "%"),
                Voce("Serie attuale", "$attuale giorni", attuale.toDouble(), "giorni"),
                Voce("Serie più lunga (tutto lo storico)", "$record giorni", record.toDouble(), "giorni"),
            ),
            c.copertura(Metrica.PASSI),
            listOf(Grafico.Calendario("Passi per giorno", "passi", c.giorni.associateWith { s[it]?.valore }, c.periodo.da, minOf(c.periodo.a, c.oggi))),
            listOf(MANCANTI_STD),
            principale = 100.0 * raggiunti / conDati.size,
        )
    }

    /** Serie attuale e più lunga di giorni consecutivi con valore ≥ soglia. */
    fun serie(valori: Map<LocalDate, Double?>, soglia: Double, oggi: LocalDate): Pair<Int, Int> {
        val giorni = valori.keys.filter { it <= oggi }.sorted()
        if (giorni.isEmpty()) return 0 to 0
        var record = 0
        var corrente = 0
        var precedente: LocalDate? = null
        for (g in giorni) {
            val ok = (valori[g] ?: -1.0) >= soglia
            corrente = if (ok && precedente != null && ChronoUnit.DAYS.between(precedente, g) == 1L && corrente > 0) corrente + 1 else if (ok) 1 else 0
            record = maxOf(record, corrente)
            precedente = g
        }
        // Serie attuale: si conta all'indietro da ieri (oggi conta solo se già raggiunto).
        var attuale = 0
        var g = if ((valori[oggi] ?: -1.0) >= soglia) oggi else oggi.minusDays(1)
        while ((valori[g] ?: -1.0) >= soglia) { attuale++; g = g.minusDays(1) }
        return attuale to record
    }

    val record = analisi(def("att.record", "Record personali", "Passi e distanza di tutto lo storico",
        "Giorno con più passi, settimana di calendario (lunedì-domenica) con più passi fra quelle con 7 giorni di dati, mese con la media giornaliera più alta fra quelli con almeno 20 giorni di dati.",
        "passi", "1 giorno con dati", "Settimane e mesi incompleti non concorrono, per non confrontare totali su durate diverse.", "AnalisiAttivitaTest.record")) { c ->
        c.richiedi(Metrica.PASSI, 1)?.let { return@analisi it }
        val s = c.dati.serie(Metrica.PASSI).filterValues { it.valore != null && it.sospetti == 0 }.mapValues { it.value.valore!! }
        if (s.isEmpty()) return@analisi nd("Nessun giorno valido.")
        val top = s.maxBy { it.value }
        val settimane = s.entries.groupBy { Periodo.di(org.costalonga.polso.motore.TipoPeriodo.SETTIMANA, it.key).da }.filterValues { it.size == 7 }
        val topSett = settimane.maxByOrNull { e -> e.value.sumOf { it.value } }
        val mesi = s.entries.groupBy { it.key.withDayOfMonth(1) }.filterValues { it.size >= 20 }
        val topMese = mesi.maxByOrNull { e -> e.value.map { it.value }.average() }
        val voci = mutableListOf(Voce("Giorno migliore", "${Formato.numero(top.value)} passi il ${Tempo.etichetta(top.key)}", top.value, "passi"))
        topSett?.let { voci += Voce("Settimana migliore", "${Formato.numero(it.value.sumOf { e -> e.value })} passi (dal ${Tempo.etichetta(it.key)})") }
            ?: run { voci += Voce("Settimana migliore", "nessuna settimana completa") }
        topMese?.let { voci += Voce("Mese migliore", "${Formato.numero(it.value.map { e -> e.value }.average())} passi al giorno (${Tempo.nomiMesi[it.key.monthValue - 1]} ${it.key.year})") }
            ?: run { voci += Voce("Mese migliore", "nessun mese con almeno 20 giorni di dati") }
        val dist = c.dati.serie(Metrica.DISTANZA).filterValues { it.valore != null }.maxByOrNull { it.value.valore!! }
        dist?.let { voci += Voce("Distanza massima in un giorno", "${Formato.distanza(it.value.valore)} il ${Tempo.etichetta(it.key)}") }
        Esito.Disponibile(voci, c.copertura(Metrica.PASSI), note = listOf("I record considerano tutto lo storico, non solo il periodo selezionato. I giorni con valori sospetti sono esclusi."))
    }

    val tendenza = analisi(def("att.tendenza", "Tendenza dei passi", "Passi",
        "Pendenza di Theil–Sen (mediana delle pendenze fra tutte le coppie di giorni) e test di Mann–Kendall con correzione della varianza per l'autocorrelazione dei residui.",
        "passi/giorno per giorno", "14 giorni con dati", "Una tendenza descrive il periodo osservato e non prevede il futuro. p < 0,05 indica che una pendenza così netta sarebbe improbabile se non ci fosse alcuna tendenza.", "AnalisiAttivitaTest.tendenza")) { c ->
        tendenzaMetrica(c, Metrica.PASSI, 14)
    }

    val distribuzione = analisi(def("att.distribuzione", "Distribuzione dei passi giornalieri", "Passi", "Istogramma in 8 classi di uguale ampiezza fra minimo e massimo del periodo.", "giorni", "7 giorni con dati",
        "La forma dipende dal numero di giorni: con poche osservazioni è indicativa.", "AnalisiAttivitaTest.distribuzione")) { c ->
        c.richiedi(Metrica.PASSI, 7)?.let { return@analisi it }
        val x = c.dati.osservati(Metrica.PASSI, c.giorni)
        val h = Stat.istogramma(x, 8)
        Esito.Disponibile(
            listOf(Voce("Giorni", "${x.size}"), Voce("Intervallo interquartile", Formato.conUnita(Stat.iqr(x), "passi"))),
            c.copertura(Metrica.PASSI),
            listOf(Grafico.Barre("Giorni per fascia di passi", "giorni", h.map { (r, n) -> "${Formato.numero(r.start / 1000, 1)}k" to n.toDouble() })),
            listOf(MANCANTI_STD),
        )
    }

    /** Durata mediana in minuti degli intervalli dei passi: dice se si possono fare analisi orarie. */
    fun granularitaPassiMin(c: Contesto): Double? {
        val da = Tempo.inizioGiorno(c.periodo.da, c.dati.zona)
        val a = Tempo.inizioGiorno(c.periodo.a.plusDays(1), c.dati.zona)
        val d = c.dati.misure(Metrica.PASSI).filter { it.inizio in da until a }.map { (it.fine - it.inizio) / 60_000.0 }
        return Stat.mediana(d)
    }

    val fasceOrarie = analisi(def("att.orario", "Passi per fascia oraria", "Passi con intervalli di al massimo 60 minuti",
        "Media, per ciascuna ora del giorno, dei passi registrati in quell'ora nei giorni con dati.", "passi/ora", "7 giorni; durata mediana degli intervalli ≤ 60 minuti",
        "Se la fonte fornisce solo totali giornalieri l'analisi non è possibile.", "AnalisiAttivitaTest.orario",
        mancanti = "Si considerano solo i giorni con almeno un dato di passi; un'ora senza intervalli in un giorno con dati conta come zero passi in quell'ora, perché le fonti non registrano intervalli vuoti.")) { c ->
        c.richiedi(Metrica.PASSI, 7)?.let { return@analisi it }
        val gran = granularitaPassiMin(c) ?: return@analisi nd("Nessun intervallo nel periodo.")
        if (gran > 60) return@analisi nd("La fonte fornisce intervalli di circa ${Formato.numero(gran)} minuti: troppo lunghi per un profilo orario.")
        val ore = DoubleArray(24)
        val zona = c.dati.zona
        val giorniCon = c.giorni.filter { c.dati.serie(Metrica.PASSI)[it]?.valore != null }.toSet()
        c.dati.misure(Metrica.PASSI).forEach { m ->
            val g = Tempo.giorno(m.inizio, zona, m.scartoSec)
            if (g in giorniCon) ore[Instant.ofEpochMilli(m.inizio).atZone(zona).hour] += m.valore
        }
        val medie = ore.map { it / giorniCon.size }
        val picco = medie.indices.maxBy { medie[it] }
        Esito.Disponibile(
            listOf(Voce("Ora più attiva", "%02d:00–%02d:59, in media %s passi".format(picco, picco, Formato.numero(medie[picco])))),
            c.copertura(Metrica.PASSI),
            listOf(Grafico.Barre("Passi medi per ora", "passi", medie.mapIndexed { i, v -> "%02d".format(i) to v })),
            listOf("Granularità mediana degli intervalli: ${Formato.numero(gran)} minuti."),
        )
    }

    val sedentarieta = analisi(def("att.sedentarieta", "Ore sedentarie", "Passi con intervalli ≤ 60 minuti e frequenza cardiaca (prova che l'orologio era indossato)",
        "Fra le 8 e le 21, un'ora è sedentaria se l'orologio risulta indossato (almeno un battito registrato in quell'ora) e i passi sono meno di 250 (soglia usata dai promemoria «muoviti» dei contapassi). Media delle ore sedentarie per giorno.",
        "ore/giorno", "7 giorni con passi orari e frequenza cardiaca", "Senza frequenza cardiaca non si distingue un'ora ferma da un'ora senza orologio: in quel caso l'analisi non viene fatta. Bici, nuoto e attività senza passi risultano sedentarie.",
        "AnalisiAttivitaTest.sedentarieta", mancanti = "Le ore senza battiti registrati sono escluse (orologio probabilmente non indossato).")) { c ->
        c.richiedi(Metrica.PASSI, 7)?.let { return@analisi it }
        if (!c.dati.presente(Metrica.FREQUENZA_CARDIACA)) return@analisi nd("Serve la frequenza cardiaca per sapere quando l'orologio era indossato.", Metrica.FREQUENZA_CARDIACA.nome)
        val gran = granularitaPassiMin(c) ?: 999.0
        if (gran > 60) return@analisi nd("Gli intervalli dei passi durano circa ${Formato.numero(gran)} minuti: troppo lunghi.")
        val zona = c.dati.zona
        val passiOra = HashMap<Pair<LocalDate, Int>, Double>()
        c.dati.misure(Metrica.PASSI).forEach { m ->
            val z = Instant.ofEpochMilli(m.inizio).atZone(zona)
            passiOra.merge(z.toLocalDate() to z.hour, m.valore, Double::plus)
        }
        val indossato = c.dati.misure(Metrica.FREQUENZA_CARDIACA).map { Instant.ofEpochMilli(it.inizio).atZone(zona).let { z -> z.toLocalDate() to z.hour } }.toSet()
        val perGiorno = c.giorni.mapNotNull { g ->
            val oreValide = (8..20).filter { (g to it) in indossato }
            if (oreValide.size < 6) null else g to oreValide.count { (passiOra[g to it] ?: 0.0) < 250 }
        }
        if (perGiorno.size < 7) return@analisi nd("Solo ${perGiorno.size} giorni hanno almeno 6 ore diurne con l'orologio indossato; ne servono 7.")
        val x = perGiorno.map { it.second.toDouble() }
        Esito.Disponibile(
            listOf(Voce("Ore sedentarie medie (8-21)", "${Formato.numero(Stat.media(x), 1)} ore", Stat.media(x), "ore"), Voce("Giorni valutati", "${perGiorno.size}")),
            c.copertura(Metrica.PASSI),
            listOf(Grafico.Linea("Ore sedentarie", "ore", c.giorni.map { g -> g to perGiorno.firstOrNull { it.first == g }?.second?.toDouble() })),
        )
    }

    val regolarita = analisi(def("att.regolarita", "Regolarità dell'attività", "Passi",
        "Coefficiente di variazione dei passi giornalieri (più basso = più regolare) e quota di giorni entro ±25% della mediana del periodo.",
        "%", "14 giorni con dati", "Misura la costanza, non la quantità: una settimana sempre sedentaria è «regolare».", "AnalisiAttivitaTest.regolarita")) { c ->
        c.richiedi(Metrica.PASSI, 14)?.let { return@analisi it }
        val x = c.dati.osservati(Metrica.PASSI, c.giorni)
        val med = Stat.mediana(x)!!
        val entro = x.count { med > 0 && kotlin.math.abs(it - med) <= 0.25 * med }
        Esito.Disponibile(
            listOf(
                Voce("Coefficiente di variazione", Formato.percentuale(Stat.cv(x)), Stat.cv(x), "%"),
                Voce("Giorni entro ±25% della mediana", "$entro su ${x.size} (${Formato.percentuale(100.0 * entro / x.size)})", 100.0 * entro / x.size, "%"),
            ),
            c.copertura(Metrica.PASSI), principale = Stat.cv(x),
        )
    }

    val elenco = listOf(passi, distanza, calorie, minutiAttivi, piani, confronto, giornoSettimana, obiettivo, record, tendenza, distribuzione, fasceOrarie, sedentarieta, regolarita)
}

/** Confronto con il periodo equivalente precedente e con l'anno prima. */
internal fun confrontoPeriodi(c: Contesto, m: Metrica): Esito {
    c.richiedi(m, 3)?.let { return it }
    val ora = c.dati.osservati(m, c.giorni)
    val prima = c.periodo.precedente()
    val xPrima = c.dati.osservati(m, prima.giorni)
    if (xPrima.size < 3) return nd("Servono almeno 3 giorni con dati anche nel periodo precedente (${prima.descrizione()}); ce ne sono ${xPrima.size}.")
    val mOra = ora.average()
    val mPrima = xPrima.average()
    val ic = Stat.bootstrapDifferenzaMedie(ora, xPrima)
    val voci = mutableListOf(
        Voce("Media nel periodo", Formato.conUnita(mOra, m.unita, m.decimali), mOra, m.unita),
        Voce("Media nel periodo precedente", Formato.conUnita(mPrima, m.unita, m.decimali) + " (${prima.descrizione()})", mPrima, m.unita),
        Voce("Differenza", "${Formato.conSegno(mOra - mPrima, m.decimali)} ${m.unita} (${Formato.conSegno(Stat.variazionePercentuale(mOra, mPrima), 1)}%)", mOra - mPrima, m.unita),
    )
    ic?.let { voci += Voce("Intervallo di confidenza 95% della differenza", "${Formato.conSegno(it.first, m.decimali)} … ${Formato.conSegno(it.second, m.decimali)} ${m.unita}") }
    val annoPrima = c.periodo.annoPrima()
    val xAnno = c.dati.osservati(m, annoPrima.giorni)
    if (xAnno.size >= 3) voci += Voce("Stesso periodo dell'anno prima", "${Formato.conUnita(xAnno.average(), m.unita, m.decimali)} (${Formato.conSegno(Stat.variazionePercentuale(mOra, xAnno.average()), 1)}%)")
    val note = mutableListOf("Si confrontano medie giornaliere sui soli giorni con dati, così periodi con copertura diversa restano confrontabili.")
    if (c.parziale) note += "Il periodo attuale non è finito."
    if (ic != null && ic.first <= 0 && ic.second >= 0) note += "L'intervallo comprende lo zero: la differenza non si distingue dalla variabilità abituale."
    return Esito.Disponibile(voci, c.copertura(m), note = note, principale = Stat.variazionePercentuale(mOra, mPrima))
}

internal fun tendenzaMetrica(c: Contesto, m: Metrica, minimo: Int): Esito {
    c.richiedi(m, minimo)?.let { return it }
    val s = c.dati.serie(m)
    val coppie = c.giorni.mapNotNull { g -> s[g]?.valore?.let { ChronoUnit.DAYS.between(c.periodo.da, g).toDouble() to it } }
    val t = Stat.tendenza(coppie.map { it.first }, coppie.map { it.second }) ?: return nd("Dati insufficienti per la tendenza.")
    val settimanale = t.pendenza * 7
    val giudizio = when {
        t.p >= 0.05 -> "nessuna tendenza netta"
        t.pendenza > 0 -> "in aumento"
        else -> "in diminuzione"
    }
    return Esito.Disponibile(
        listOf(
            Voce("Andamento", giudizio),
            Voce("Variazione stimata", "${Formato.conSegno(settimanale, maxOf(m.decimali, 1))} ${m.unita} a settimana", settimanale, "${m.unita}/settimana"),
            Voce("p-value (Mann–Kendall corretto)", Formato.pValore(t.p), t.p),
            Voce("Giorni usati", "${t.n}"),
        ),
        c.copertura(m),
        listOf(Grafico.Linea("${m.nome} e retta di tendenza", m.unita, c.giorni.map { it to (if (it > c.oggi) null else s[it]?.valore) },
            c.giorni.map { g -> t.intercetta + t.pendenza * ChronoUnit.DAYS.between(c.periodo.da, g) })),
        listOf("La linea secondaria è la retta di Theil–Sen, non una media mobile.", MANCANTI_STD),
        principale = settimanale,
    )
}
