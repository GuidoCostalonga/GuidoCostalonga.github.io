package org.costalonga.polso.motore

import java.time.LocalDate
import java.time.ZoneId

/** Valore giornaliero calcolato, con la sua provenienza e la sua qualità. */
data class ValoreGiorno(
    val giorno: LocalDate,
    /** null = nessun dato (mai zero per "mancante"). */
    val valore: Double?,
    val n: Int,
    val origine: String?,
    val minimo: Double? = null,
    val massimo: Double? = null,
    /** Ore del giorno con almeno un dato (0..25). */
    val oreCoperte: Int = 0,
    val sospetti: Int = 0,
    /** Misure scartate perché sovrapposte a misure più fini della stessa origine. */
    val sovrapposteScartate: Int = 0,
    /** Origini alternative presenti quel giorno e non usate. */
    val altreOrigini: List<String> = emptyList(),
)

data class NotteSonno(
    val giorno: LocalDate,
    val inizio: Long,
    val fine: Long,
    val scartoSec: Int?,
    val origine: String,
    val minutiInLetto: Double,
    /** Minuti di sonno: somma delle fasi di sonno se presenti, altrimenti durata della sessione. */
    val minutiSonno: Double,
    val haFasi: Boolean,
    val minutiPerFase: Map<FaseSonno, Double>,
    /** Risvegli: periodi di veglia fra due fasi di sonno (solo con fasi disponibili). */
    val risvegli: Int?,
    val minutiVeglia: Double?,
    val pisolini: Int,
    val minutiPisolini: Double,
) {
    val efficienza: Double? get() = if (haFasi && minutiInLetto > 0) 100.0 * minutiSonno / minutiInLetto else null
}

/** Impostazioni che cambiano i calcoli. */
data class Preferenze(
    val zona: ZoneId = Tempo.ROMA,
    /** Origini in ordine di preferenza (es. com.hihonor.health prima del telefono). */
    val prioritaOrigini: List<String> = emptyList(),
    val obiettivoPassi: Int = 8000,
    val obiettivoSonnoMin: Int = 450,
    val annoNascita: Int? = null,
    val fcMaxManuale: Int? = null,
    val fcRiposoManuale: Int? = null,
    val metodoZone: MetodoZone = MetodoZone.PERCENTUALE_FCMAX,
    /** Soglia di passi per considerare "attivo" un giorno, se diversa dall'obiettivo. */
    val sogliaGiornoAttivo: Int? = null,
)

enum class MetodoZone(val nome: String, val descrizione: String) {
    PERCENTUALE_FCMAX("Percentuale della FC massima", "Zone al 50-60-70-80-90% della FC massima; FC massima inserita da te o stimata con la formula di Tanaka (208 − 0,7 × età)."),
    KARVONEN("Riserva cardiaca (Karvonen)", "Zone al 50-60-70-80-90% della riserva: FC riposo + p × (FC max − FC riposo). Richiede una FC a riposo."),
}

/**
 * Tutti i dati originali caricati in memoria per un intervallo, con le regole
 * di aggregazione. È la sola classe che trasforma misure in valori giornalieri.
 */
class Dati(
    misure: List<Misura>,
    val sonni: List<SessioneSonno>,
    val allenamenti: List<Allenamento>,
    val diario: List<VoceDiario>,
    val pref: Preferenze = Preferenze(),
) {
    val zona: ZoneId get() = pref.zona
    private val perMetrica: Map<String, List<Misura>> = misure.groupBy { it.metrica }
    private val cacheSerie = HashMap<Metrica, Map<LocalDate, ValoreGiorno>>()
    private var cacheNotti: Map<LocalDate, NotteSonno>? = null

    fun misure(m: Metrica): List<Misura> = perMetrica[m.codice].orEmpty()
    fun presente(m: Metrica): Boolean = misure(m).isNotEmpty()

    fun primoGiorno(): LocalDate? {
        val tutti = perMetrica.values.flatten().map { it.inizio } + sonni.map { it.fine } + allenamenti.map { it.inizio }
        return tutti.minOrNull()?.let { Tempo.giorno(it, zona) }
    }

    fun ultimoGiorno(): LocalDate? {
        val tutti = perMetrica.values.flatten().map { it.fine } + sonni.map { it.fine } + allenamenti.map { it.fine }
        return tutti.maxOrNull()?.let { Tempo.giorno(it, zona) }
    }

    private fun rango(origine: String): Int {
        val i = pref.prioritaOrigini.indexOf(origine)
        return if (i >= 0) i else Int.MAX_VALUE
    }

    /**
     * Sceglie l'origine da usare in un giorno: la prima delle preferenze; in
     * assenza di preferenze quella con più misure (la più dettagliata);
     * a parità, l'ordine alfabetico, così il risultato è sempre lo stesso.
     */
    private fun scegliOrigine(conte: Map<String, Int>): String =
        conte.keys.sortedWith(compareBy<String>({ rango(it) }, { -(conte[it] ?: 0) }, { it })).first()

    /**
     * Toglie le sovrapposizioni fra misure a intervallo della stessa origine
     * tenendo l'insieme più fine di intervalli disgiunti (scelta greedy per
     * fine crescente, a parità la durata minore). Evita di contare due volte
     * un totale giornaliero e i suoi parziali orari.
     */
    internal fun senzaSovrapposizioni(lista: List<Misura>): Pair<List<Misura>, Int> {
        val ordinate = lista.sortedWith(compareBy<Misura>({ it.fine }, { it.fine - it.inizio }))
        val tenute = ArrayList<Misura>(ordinate.size)
        var fineUltima = Long.MIN_VALUE
        for (m in ordinate) {
            if (m.inizio >= fineUltima || m.fine == m.inizio && m.inizio > fineUltima) {
                tenute += m
                fineUltima = maxOf(fineUltima, m.fine)
            }
        }
        return tenute to (lista.size - tenute.size)
    }

    fun serie(m: Metrica): Map<LocalDate, ValoreGiorno> = cacheSerie.getOrPut(m) { calcolaSerie(m) }

    private fun calcolaSerie(m: Metrica): Map<LocalDate, ValoreGiorno> {
        val lista = misure(m)
        if (lista.isEmpty()) return emptyMap()
        return when (m.natura) {
            Natura.INTERVALLO -> serieIntervalli(m, lista)
            Natura.ISTANTANEA -> serieIstantanee(m, lista)
        }
    }

    private fun serieIntervalli(m: Metrica, lista: List<Misura>): Map<LocalDate, ValoreGiorno> {
        // Ripartizione per giorno e origine.
        data class Acc(var somma: Double = 0.0, var n: Int = 0, val ore: HashSet<Int> = HashSet(), var sospetti: Int = 0)
        val perGiorno = HashMap<LocalDate, HashMap<String, Acc>>()
        val scartatePerGiornoOrigine = HashMap<Pair<LocalDate, String>, Int>()
        lista.groupBy { it.origine.ifBlank { it.fonte } }.forEach { (origine, delOrigine) ->
            val (pulite, scartate) = senzaSovrapposizioni(delOrigine)
            if (scartate > 0) {
                delOrigine.minus(pulite.toSet()).forEach {
                    val g = Tempo.giorno(it.inizio, zona, it.scartoSec)
                    scartatePerGiornoOrigine.merge(g to origine, 1, Int::plus)
                }
            }
            for (x in pulite) {
                if (x.valore.isNaN() || x.valore < 0) {
                    val g = Tempo.giorno(x.inizio, zona, x.scartoSec)
                    perGiorno.getOrPut(g) { HashMap() }.getOrPut(origine) { Acc() }.sospetti++
                    continue
                }
                for ((g, v) in Tempo.ripartisci(x.inizio, x.fine, x.valore, zona, x.scartoSec)) {
                    val acc = perGiorno.getOrPut(g) { HashMap() }.getOrPut(origine) { Acc() }
                    acc.somma += v
                    acc.n++
                    acc.ore += java.time.Instant.ofEpochMilli(maxOf(x.inizio, Tempo.inizioGiorno(g, zona))).atZone(zona).hour
                }
            }
        }
        return perGiorno.mapValues { (g, origini) ->
            val valide = origini.filterValues { it.n > 0 }
            if (valide.isEmpty()) {
                ValoreGiorno(g, null, 0, null, sospetti = origini.values.sumOf { it.sospetti })
            } else {
                val scelta = scegliOrigine(valide.mapValues { it.value.n })
                val acc = valide.getValue(scelta)
                val sospetto = !m.plausibile(acc.somma)
                ValoreGiorno(
                    giorno = g, valore = acc.somma, n = acc.n, origine = scelta,
                    oreCoperte = acc.ore.size,
                    sospetti = acc.sospetti + if (sospetto) 1 else 0,
                    sovrapposteScartate = scartatePerGiornoOrigine[g to scelta] ?: 0,
                    altreOrigini = valide.keys.filter { it != scelta }.sorted(),
                )
            }
        }.toSortedMap()
    }

    private fun serieIstantanee(m: Metrica, lista: List<Misura>): Map<LocalDate, ValoreGiorno> {
        val perGiorno = lista.groupBy { Tempo.giorno(it.inizio, zona, it.scartoSec) }
        return perGiorno.mapValues { (g, delGiorno) ->
            val perOrigine = delGiorno.groupBy { it.origine.ifBlank { it.fonte } }
            val scelta = scegliOrigine(perOrigine.mapValues { it.value.size })
            val tutte = perOrigine.getValue(scelta)
            val valide = tutte.filter { m.plausibile(it.valore) }
            val sospetti = tutte.size - valide.size
            if (valide.isEmpty()) return@mapValues ValoreGiorno(g, null, 0, scelta, sospetti = sospetti)
            val v = when (m.combinazione) {
                Combinazione.MEDIA -> valide.map { it.valore }.average()
                Combinazione.ULTIMO -> valide.maxBy { it.inizio }.valore
                Combinazione.SOMMA -> valide.sumOf { it.valore }
            }
            ValoreGiorno(
                giorno = g, valore = v, n = valide.size, origine = scelta,
                minimo = valide.minOf { it.valore }, massimo = valide.maxOf { it.valore },
                oreCoperte = valide.map { java.time.Instant.ofEpochMilli(it.inizio).atZone(Tempo.zonaPer(it.scartoSec, zona)).hour }.toSet().size,
                sospetti = sospetti,
                altreOrigini = perOrigine.keys.filter { it != scelta }.sorted(),
            )
        }.toSortedMap()
    }

    /** Valori dei giorni del periodo, null dove mancano: per grafici e medie mobili. */
    fun valoriNelPeriodo(m: Metrica, p: Periodo): List<Pair<LocalDate, Double?>> {
        val s = serie(m)
        return p.giorni.map { it to s[it]?.valore }
    }

    /** Solo i valori osservati nel periodo (giorni senza dati esclusi). */
    fun osservati(m: Metrica, giorni: List<LocalDate>): List<Double> {
        val s = serie(m)
        return giorni.mapNotNull { s[it]?.valore }
    }

    /** Misure istantanee di una metrica cadute dentro un intervallo. */
    fun campioni(m: Metrica, da: Long, a: Long): List<Misura> =
        misure(m).filter { it.inizio in da until a && m.plausibile(it.valore) }.sortedBy { it.inizio }

    // ----------------------------------------------------------------- sonno

    fun notti(): Map<LocalDate, NotteSonno> = cacheNotti ?: calcolaNotti().also { cacheNotti = it }

    private fun calcolaNotti(): Map<LocalDate, NotteSonno> {
        val perNotte = sonni.filter { it.fine > it.inizio }.groupBy { Tempo.nottePer(it, zona) }
        return perNotte.mapValues { (g, sessioni) ->
            val perOrigine = sessioni.groupBy { it.origine.ifBlank { it.fonte } }
            val scelta = scegliOrigine(perOrigine.mapValues { e -> e.value.sumOf { (it.fine - it.inizio) / 60_000 }.toInt() })
            val proprie = perOrigine.getValue(scelta).sortedBy { it.inizio }
            val principale = proprie.maxBy { it.fine - it.inizio }
            val altre = proprie.filter { it !== principale }
            val pisolini = altre.filter { (it.fine - it.inizio) < 4 * 3_600_000L }
            descriviNotte(g, principale, scelta, pisolini)
        }.toSortedMap()
    }

    private fun descriviNotte(g: LocalDate, s: SessioneSonno, origine: String, pisolini: List<SessioneSonno>): NotteSonno {
        val inLetto = (s.fine - s.inizio) / 60_000.0
        val fasi = s.fasi.filter { it.fine > it.inizio }.sortedBy { it.inizio }
        val conFasiDettagliate = fasi.any { FaseSonno.daCodice(it.fase) in setOf(FaseSonno.LEGGERO, FaseSonno.PROFONDO, FaseSonno.REM, FaseSonno.SVEGLIO, FaseSonno.SONNO) }
        val perFase = HashMap<FaseSonno, Double>()
        fasi.forEach { perFase.merge(FaseSonno.daCodice(it.fase), (it.fine - it.inizio) / 60_000.0, Double::plus) }
        val sonno = if (conFasiDettagliate) perFase.filterKeys { it.dormendo }.values.sum() else inLetto
        var risvegli: Int? = null
        var veglia: Double? = null
        if (conFasiDettagliate) {
            var conta = 0
            var dormito = false
            var inVeglia = false
            for (f in fasi) {
                val fase = FaseSonno.daCodice(f.fase)
                if (fase.dormendo) {
                    if (inVeglia && dormito) conta++
                    dormito = true
                    inVeglia = false
                } else if (fase == FaseSonno.SVEGLIO || fase == FaseSonno.FUORI_LETTO) {
                    inVeglia = true
                }
            }
            risvegli = conta
            veglia = (perFase[FaseSonno.SVEGLIO] ?: 0.0) + (perFase[FaseSonno.FUORI_LETTO] ?: 0.0)
        }
        return NotteSonno(
            giorno = g, inizio = s.inizio, fine = s.fine, scartoSec = s.scartoSec, origine = origine,
            minutiInLetto = inLetto, minutiSonno = sonno, haFasi = conFasiDettagliate, minutiPerFase = perFase,
            risvegli = risvegli, minutiVeglia = veglia,
            pisolini = pisolini.size, minutiPisolini = pisolini.sumOf { (it.fine - it.inizio) / 60_000.0 },
        )
    }

    fun nottiNelPeriodo(p: Periodo): List<NotteSonno> = p.giorni.mapNotNull { notti()[it] }

    // ----------------------------------------------------------- allenamenti

    fun allenamentiNelPeriodo(p: Periodo): List<Allenamento> =
        allenamenti.filter { Tempo.giorno(it.inizio, zona, it.scartoSec) in p.da..p.a }.sortedBy { it.inizio }

    // -------------------------------------------------------------- diario

    fun diarioNelPeriodo(p: Periodo, tipo: TipoDiario? = null): List<VoceDiario> =
        diario.filter { (tipo == null || it.tipo == tipo.codice) && Tempo.giorno(it.istante, zona) in p.da..p.a }

    /**
     * Serie del peso: misure della fonte (bilancia collegata) unite alle voci
     * manuali. A parità di giorno vale l'ultima registrazione.
     */
    fun seriePeso(): Map<LocalDate, Double> {
        val r = sortedMapOf<LocalDate, Pair<Long, Double>>()
        misure(Metrica.PESO).filter { Metrica.PESO.plausibile(it.valore) }.forEach {
            val g = Tempo.giorno(it.inizio, zona, it.scartoSec)
            if ((r[g]?.first ?: Long.MIN_VALUE) <= it.inizio) r[g] = it.inizio to it.valore
        }
        diario.filter { it.tipo == TipoDiario.PESO.codice && it.valore != null && Metrica.PESO.plausibile(it.valore) }.forEach {
            val g = Tempo.giorno(it.istante, zona)
            if ((r[g]?.first ?: Long.MIN_VALUE) <= it.istante) r[g] = it.istante to it.valore!!
        }
        return r.mapValues { it.value.second }.toSortedMap()
    }

    /** Valore giornaliero di una voce numerica del diario (somma per acqua, caffeina, alcol; media per le scale 1-5). */
    fun serieDiario(tipo: TipoDiario): Map<LocalDate, Double> {
        val somma = tipo in setOf(TipoDiario.ACQUA, TipoDiario.CAFFEINA, TipoDiario.ALCOL)
        return diario.filter { it.tipo == tipo.codice && it.valore != null }
            .groupBy { Tempo.giorno(it.istante, zona) }
            .mapValues { (_, v) -> if (somma) v.sumOf { it.valore!! } else v.map { it.valore!! }.average() }
            .toSortedMap()
    }
}
