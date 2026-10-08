package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Sport
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoPeriodo
import kotlin.math.abs

object AnalisiAllenamenti {
    private const val MANCANTI_ALL = "Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie."

    private fun def(id: String, titolo: String, dati: String, metodo: String, unita: String, minimo: String, limiti: String, prova: String) =
        Definizione(id, titolo, Sezione.ALLENAMENTI, dati, metodo, unita, minimo, MANCANTI_ALL, limiti, prova)

    private fun Contesto.sessioni() = dati.allenamentiNelPeriodo(periodo)
    private fun Contesto.cop(s: List<Allenamento>) = Copertura(s.map { Tempo.giorno(it.inizio, dati.zona) }.distinct().size, giorni.size,
        s.firstOrNull()?.let { Tempo.giorno(it.inizio, dati.zona) }, s.lastOrNull()?.let { Tempo.giorno(it.inizio, dati.zona) })

    private fun Contesto.richiediSessioni(min: Int): Pair<List<Allenamento>, Esito.NonDisponibile?> {
        if (dati.allenamenti.isEmpty()) return emptyList<Allenamento>() to nd("Nessun allenamento nell'archivio.", "Allenamenti")
        val s = sessioni()
        return s to if (s.size < min) nd("Servono almeno $min allenamenti nel periodo; ce ne sono ${s.size}.") else null
    }

    /** Secondi per km, solo per sport con distanza e distanza fornita. */
    fun passoSecKm(a: Allenamento): Double? {
        val d = a.distanzaM ?: return null
        if (d < 100 || a.sport !in Sport.conDistanza) return null
        return (a.durataMs / 1000.0) / (d / 1000.0)
    }

    fun velocitaKmh(a: Allenamento): Double? = passoSecKm(a)?.let { 3600.0 / it }

    val riepilogo = analisi(def("all.riepilogo", "Riepilogo degli allenamenti", "Sessioni di allenamento",
        "Numero di sessioni, durata totale e media, distanza totale (sulle sessioni che la riportano), calorie e dislivello forniti dalla fonte.",
        "sessioni, min, km", "1 sessione", "Durata = fine − inizio della sessione, comprese le pause se la fonte non le separa.", "AnalisiAllenamentiTest.riepilogo")) { c ->
        val (s, err) = c.richiediSessioni(1); err?.let { return@analisi it }
        val dur = s.map { it.durataMs / 60_000.0 }
        val conDist = s.mapNotNull { it.distanzaM }
        val voci = mutableListOf(
            Voce("Sessioni", "${s.size}", s.size.toDouble()),
            Voce("Durata totale", Formato.durata(dur.sum()), dur.sum(), "min"),
            Voce("Durata media", Formato.durata(dur.average()), dur.average(), "min"),
        )
        if (conDist.isNotEmpty()) voci += Voce("Distanza totale", "${Formato.distanza(conDist.sum())} (${conDist.size} sessioni con distanza)", conDist.sum(), "m", Provenienza.FONTE)
        s.mapNotNull { it.calorieKcal }.takeIf { it.isNotEmpty() }?.let { voci += Voce("Calorie (fonte)", Formato.conUnita(it.sum(), "kcal"), it.sum(), "kcal", Provenienza.FONTE) }
        s.mapNotNull { it.dislivelloM }.takeIf { it.isNotEmpty() }?.let { voci += Voce("Dislivello positivo (fonte)", Formato.conUnita(it.sum(), "m"), it.sum(), "m", Provenienza.FONTE) }
        Esito.Disponibile(voci, c.cop(s), principale = s.size.toDouble())
    }

    val frequenza = analisi(def("all.frequenza", "Frequenza e volume settimanale", "Sessioni di allenamento",
        "Sessioni e minuti per settimana di calendario; confronto dei minuti settimanali con l'indicazione dell'Organizzazione mondiale della sanità (150-300 minuti di attività moderata, 2020).",
        "min/settimana", "1 sessione; periodo di almeno 7 giorni", "Si contano solo gli allenamenti registrati: camminate e attività non avviate sull'orologio non entrano. L'intensità non viene verificata.", "AnalisiAllenamentiTest.frequenza")) { c ->
        val (s, err) = c.richiediSessioni(1); err?.let { return@analisi it }
        if (c.giorni.size < 7) return@analisi nd("Il periodo deve durare almeno 7 giorni.")
        val settimane = c.giorni.map { Periodo.di(TipoPeriodo.SETTIMANA, it).da }.distinct()
        val minuti = settimane.map { lun -> lun to s.filter { Periodo.di(TipoPeriodo.SETTIMANA, Tempo.giorno(it.inizio, c.dati.zona)).da == lun }.sumOf { it.durataMs / 60_000.0 } }
        val media = minuti.map { it.second }.average()
        Esito.Disponibile(
            listOf(
                Voce("Sessioni a settimana", Formato.numero(s.size.toDouble() / settimane.size, 1)),
                Voce("Minuti a settimana (media)", Formato.durata(media), media, "min"),
                Voce("Settimane con almeno 150 minuti", "${minuti.count { it.second >= 150 }} su ${settimane.size}"),
            ),
            c.cop(s),
            listOf(Grafico.Barre("Minuti di allenamento per settimana", "min", minuti.map { Tempo.etichettaBreve(it.first) to it.second }, riferimento = 150.0)),
            listOf("Le settimane all'inizio e alla fine del periodo possono essere parziali."),
        )
    }

    val perSport = analisi(def("all.sport", "Distribuzione per sport", "Sessioni di allenamento", "Numero di sessioni e minuti per sport.", "sessioni, min", "1 sessione",
        "Il tipo di sport è quello scelto o riconosciuto dall'orologio.", "AnalisiAllenamentiTest.sport")) { c ->
        val (s, err) = c.richiediSessioni(1); err?.let { return@analisi it }
        val g = s.groupBy { it.sport }.mapValues { e -> e.value.size to e.value.sumOf { it.durataMs / 60_000.0 } }.entries.sortedByDescending { it.value.second }
        Esito.Disponibile(
            g.map { Voce(Sport.nome(it.key), "${it.value.first} sessioni, ${Formato.durata(it.value.second)}", it.value.second, "min") },
            c.cop(s), listOf(Grafico.Barre("Minuti per sport", "min", g.map { Sport.nome(it.key) to it.value.second })),
        )
    }

    val passo = analisi(def("all.passo", "Passo e velocità", "Sessioni con distanza (camminata, corsa, ciclismo, nuoto…)",
        "Passo = durata / distanza (min/km); velocità = distanza / durata (km/h), per sessione e in media per sport.",
        "min/km, km/h", "1 sessione con distanza ≥ 100 m", "Le pause incluse nella durata rallentano il passo.", "AnalisiAllenamentiTest.passo")) { c ->
        val (s, err) = c.richiediSessioni(1); err?.let { return@analisi it }
        val con = s.filter { passoSecKm(it) != null }
        if (con.isEmpty()) return@analisi nd("Nessuna sessione con distanza nel periodo.")
        val voci = con.groupBy { it.sport }.map { (sp, l) ->
            val p = l.mapNotNull { passoSecKm(it) }
            Voce(Sport.nome(sp), "passo medio ${Formato.passo(p.average())}, velocità ${Formato.numero(3600 / p.average(), 1)} km/h (${l.size} sessioni)", 3600 / p.average(), "km/h")
        }
        Esito.Disponibile(voci, c.cop(con))
    }

    val progressi = analisi(def("all.progressi", "Progressi in sessioni comparabili", "Sessioni dello stesso sport con distanza simile",
        "Si prende come riferimento la distanza mediana dello sport più frequente e si tengono le sessioni entro ±15% di quella distanza; tendenza del passo con Theil–Sen e Mann–Kendall (pendenza negativa = più veloce).",
        "s/km per settimana", "4 sessioni comparabili", "Percorso, meteo e dislivello non sono controllati.", "AnalisiAllenamentiTest.progressi")) { c ->
        val (s, err) = c.richiediSessioni(4); err?.let { return@analisi it }
        val conDist = s.filter { passoSecKm(it) != null }
        val sport = conDist.groupBy { it.sport }.maxByOrNull { it.value.size } ?: return@analisi nd("Nessuna sessione con distanza.")
        val rif = Stat.mediana(sport.value.map { it.distanzaM!! })!!
        val comp = sport.value.filter { abs(it.distanzaM!! - rif) <= 0.15 * rif }
        if (comp.size < 4) return@analisi nd("Solo ${comp.size} sessioni di ${Sport.nome(sport.key)} hanno una distanza vicina a ${Formato.distanza(rif)}; ne servono 4.")
        val x = comp.map { (it.inizio - comp.first().inizio) / 86_400_000.0 }
        val t = Stat.tendenza(x, comp.map { passoSecKm(it)!! }) ?: return@analisi nd("Calcolo non possibile.")
        Esito.Disponibile(
            listOf(
                Voce("Sessioni confrontate", "${comp.size} di ${Sport.nome(sport.key)} attorno a ${Formato.distanza(rif)}"),
                Voce("Passo prima e ultima", "${Formato.passo(passoSecKm(comp.first()))} → ${Formato.passo(passoSecKm(comp.last()))}"),
                Voce("Variazione del passo", "${Formato.conSegno(t.pendenza * 7, 1)} s/km a settimana (p ${Formato.pValore(t.p)})", t.pendenza * 7, "s/km/settimana"),
            ),
            c.cop(comp),
            listOf(Grafico.Dispersione("Passo nelle sessioni comparabili", "s/km", "giorni dalla prima", "secondi per km", x.zip(comp.map { passoSecKm(it)!! }))),
        )
    }

    val carico = analisi(def("all.carico", "Carico di allenamento (TRIMP di Edwards)", "Sessioni con campioni cardiaci che coprono almeno l'80% della durata; FC massima",
        "TRIMP di Edwards = Σ minuti in zona × numero della zona (1-5), zone come nella sezione Cuore. Somma per settimana.",
        "unità arbitrarie", "1 sessione con copertura cardiaca ≥ 80%", "Indice descrittivo del volume e dell'intensità: non misura fatica, recupero o rischio di infortunio.", "AnalisiAllenamentiTest.carico")) { c ->
        val (s, err) = c.richiediSessioni(1); err?.let { return@analisi it }
        val rif = Cardio.riferimenti(c.dati, c.oggi)
        val lim = Cardio.limitiZone(rif, c.dati.pref.metodoZone) ?: return@analisi nd("Manca la FC massima (${rif.origineMax}).", "Anno di nascita o FC massima")
        val valutate = s.mapNotNull { a ->
            val t = Cardio.tempoInZone(c.dati.campioni(Metrica.FREQUENZA_CARDIACA, a.inizio, a.fine + 1), a.inizio, a.fine, lim)
            if (t.coperturaPct >= 80) a to Cardio.trimpEdwards(t) else null
        }
        if (valutate.isEmpty()) return@analisi nd("Nessuna sessione ha campioni cardiaci per almeno l'80% della durata.")
        val perSett = valutate.groupBy { Periodo.di(TipoPeriodo.SETTIMANA, Tempo.giorno(it.first.inizio, c.dati.zona)).da }.mapValues { e -> e.value.sumOf { it.second } }.toSortedMap()
        Esito.Disponibile(
            listOf(
                Voce("Sessioni valutate", "${valutate.size} su ${s.size}"),
                Voce("TRIMP medio per sessione", Formato.numero(valutate.map { it.second }.average()), valutate.map { it.second }.average()),
                Voce("TRIMP medio per settimana", Formato.numero(perSett.values.average()), perSett.values.average()),
            ),
            c.cop(valutate.map { it.first }),
            listOf(Grafico.Barre("TRIMP per settimana", "unità", perSett.map { Tempo.etichettaBreve(it.key) to it.value })),
        )
    }

    val elenco = listOf(riepilogo, frequenza, perSport, passo, progressi, carico)
}
