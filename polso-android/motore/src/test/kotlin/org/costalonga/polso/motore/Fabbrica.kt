package org.costalonga.polso.motore

import org.costalonga.polso.motore.analisi.Contesto
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Catalogo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Costruttori di dati sintetici per le prove. */
object F {
    val Z: ZoneId = Tempo.ROMA
    val OGGI: LocalDate = LocalDate.of(2026, 10, 5)

    fun t(g: LocalDate, ora: Int, min: Int = 0): Long = LocalDateTime.of(g, java.time.LocalTime.of(ora, min)).atZone(Z).toInstant().toEpochMilli()
    fun t(s: String): Long = LocalDateTime.parse(s).atZone(Z).toInstant().toEpochMilli()

    fun passi(g: LocalDate, v: Double, origine: String = "com.hihonor.health", ora: Int = 10, durataOre: Int = 1, id: String? = null) =
        Misura(Metrica.PASSI.codice, t(g, ora), t(g, ora) + durataOre * 3_600_000L, v, "passi", null, "health_connect", origine, idEsterno = id)

    fun istantanea(m: Metrica, istante: Long, v: Double, origine: String = "com.hihonor.health") =
        Misura(m.codice, istante, istante, v, m.unita, null, "health_connect", origine)

    fun notte(sveglia: LocalDate, oraLetto: Int, minLetto: Int, durataMin: Int, fasi: List<Pair<String, Int>> = emptyList()): SessioneSonno {
        val ini = t(sveglia.minusDays(if (oraLetto >= 12) 1 else 0), oraLetto, minLetto)
        var c = ini
        val f = fasi.map { (fase, m) -> IntervalloSonno(c, c + m * 60_000L, fase).also { c += m * 60_000L } }
        return SessioneSonno(ini, ini + durataMin * 60_000L, null, "health_connect", "com.hihonor.health", "n-$sveglia", f)
    }

    fun dati(misure: List<Misura> = emptyList(), sonni: List<SessioneSonno> = emptyList(), all: List<Allenamento> = emptyList(), diario: List<VoceDiario> = emptyList(), pref: Preferenze = Preferenze()) =
        Dati(misure, sonni, all, diario, pref)

    fun ultimi(n: Int) = Periodo.ultimiGiorni(n, OGGI)

    /** Passi giornalieri dati da una funzione, con null = giorno senza dati. */
    fun seriePassi(giorni: Int, f: (Int) -> Double?): List<Misura> = (0 until giorni).mapNotNull { i ->
        val g = OGGI.minusDays((giorni - 1 - i).toLong())
        f(i)?.let { passi(g, it, id = "p$i") }
    }

    fun demo(giorni: Int = 120, pref: Preferenze = Preferenze(annoNascita = 1976)): Dati {
        val a = Demo.genera(OGGI, giorni)
        return Dati(a.misure, a.sonni, a.allenamenti, a.diario, pref)
    }

    fun esegui(id: String, d: Dati, p: Periodo = ultimi(30)): Esito = Catalogo.perId(id)!!.esegui(Contesto(d, p, OGGI))
    fun disp(id: String, d: Dati, p: Periodo = ultimi(30)): Esito.Disponibile {
        val e = esegui(id, d, p)
        check(e is Esito.Disponibile) { "$id non disponibile: ${(e as Esito.NonDisponibile).motivo}" }
        return e
    }
    fun voce(e: Esito.Disponibile, etichetta: String) = e.voci.first { it.etichetta == etichetta }
}
