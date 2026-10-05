package org.costalonga.polso.motore

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Regole sul tempo, uguali per tutta l'app.
 *
 * - Il giorno è il giorno di calendario locale: con lo scarto indicato dalla
 *   fonte, se c'è, altrimenti con il fuso scelto nelle impostazioni
 *   (inizialmente Europe/Rome). I giorni del cambio d'ora durano 23 o 25 ore.
 * - Una quantità su un intervallo che scavalca la mezzanotte viene ripartita
 *   fra i giorni in proporzione alla durata: è un'attribuzione, non una
 *   misura, ed è la stessa regola usata da Health Connect per gli aggregati.
 * - Il sonno appartiene al giorno del risveglio (la notte fra lunedì e
 *   martedì è "la notte di martedì").
 */
object Tempo {
    val ROMA: ZoneId = ZoneId.of("Europe/Rome")

    fun zonaPer(scartoSec: Int?, zona: ZoneId): ZoneId =
        if (scartoSec == null) zona else ZoneOffset.ofTotalSeconds(scartoSec)

    fun giorno(istante: Long, zona: ZoneId, scartoSec: Int? = null): LocalDate =
        Instant.ofEpochMilli(istante).atZone(zonaPer(scartoSec, zona)).toLocalDate()

    fun inizioGiorno(giorno: LocalDate, zona: ZoneId): Long =
        giorno.atStartOfDay(zona).toInstant().toEpochMilli()

    /** Durata reale del giorno in ore (23, 24 o 25 nei giorni del cambio d'ora). */
    fun oreDelGiorno(giorno: LocalDate, zona: ZoneId): Double =
        (inizioGiorno(giorno.plusDays(1), zona) - inizioGiorno(giorno, zona)) / 3_600_000.0

    /**
     * Ripartisce [valore], misurato in [inizio, fine), fra i giorni locali in
     * proporzione alla sovrapposizione. Un intervallo nullo va tutto al giorno
     * del suo inizio.
     */
    fun ripartisci(inizio: Long, fine: Long, valore: Double, zona: ZoneId, scartoSec: Int? = null): Map<LocalDate, Double> {
        val z = zonaPer(scartoSec, zona)
        if (fine <= inizio) return mapOf(giorno(inizio, zona, scartoSec) to valore)
        val totale = (fine - inizio).toDouble()
        val risultato = linkedMapOf<LocalDate, Double>()
        var g = giorno(inizio, zona, scartoSec)
        while (true) {
            val a = maxOf(inizio, inizioGiorno(g, z))
            val b = minOf(fine, inizioGiorno(g.plusDays(1), z))
            if (b > a) risultato[g] = (risultato[g] ?: 0.0) + valore * (b - a) / totale
            if (inizioGiorno(g.plusDays(1), z) >= fine) break
            g = g.plusDays(1)
        }
        return risultato
    }

    /** Minuti di sovrapposizione fra [inizio, fine) e un giorno locale. */
    fun minutiNelGiorno(inizio: Long, fine: Long, giorno: LocalDate, zona: ZoneId): Double {
        val a = maxOf(inizio, inizioGiorno(giorno, zona))
        val b = minOf(fine, inizioGiorno(giorno.plusDays(1), zona))
        return if (b > a) (b - a) / 60_000.0 else 0.0
    }

    fun nottePer(s: SessioneSonno, zona: ZoneId): LocalDate = giorno(s.fine, zona, s.scartoSec)

    fun weekend(g: LocalDate): Boolean = g.dayOfWeek == DayOfWeek.SATURDAY || g.dayOfWeek == DayOfWeek.SUNDAY

    /** Minuti dalla mezzanotte locale, per orari di addormentamento e risveglio. */
    fun minutoDelGiorno(istante: Long, zona: ZoneId, scartoSec: Int? = null): Int {
        val t = Instant.ofEpochMilli(istante).atZone(zonaPer(scartoSec, zona)).toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun giorni(da: LocalDate, a: LocalDate): List<LocalDate> {
        if (a < da) return emptyList()
        val n = ChronoUnit.DAYS.between(da, a).toInt()
        return (0..n).map { da.plusDays(it.toLong()) }
    }

    val nomiGiorni = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
    val nomiMesi = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    fun etichetta(g: LocalDate): String = "${g.dayOfMonth} ${nomiMesi[g.monthValue - 1]} ${g.year}"
    fun etichettaBreve(g: LocalDate): String = "${g.dayOfMonth}/${g.monthValue}"
}

enum class TipoPeriodo(val nome: String) { GIORNO("Giorno"), SETTIMANA("Settimana"), MESE("Mese"), ANNO("Anno"), PERSONALIZZATO("Personalizzato") }

/** Un periodo di calendario chiuso [da, a]. È parziale se comprende giorni futuri. */
data class Periodo(val tipo: TipoPeriodo, val da: LocalDate, val a: LocalDate) {
    val giorni: List<LocalDate> get() = Tempo.giorni(da, a)
    val lunghezza: Int get() = (ChronoUnit.DAYS.between(da, a) + 1).toInt()

    fun parziale(oggi: LocalDate): Boolean = a >= oggi

    /** Giorni già trascorsi (incluso oggi, che è comunque parziale). */
    fun giorniTrascorsi(oggi: LocalDate): List<LocalDate> = Tempo.giorni(da, minOf(a, oggi))

    /** Il periodo equivalente precedente (stessa durata, immediatamente prima). */
    fun precedente(): Periodo = when (tipo) {
        TipoPeriodo.GIORNO -> di(TipoPeriodo.GIORNO, da.minusDays(1))
        TipoPeriodo.SETTIMANA -> di(TipoPeriodo.SETTIMANA, da.minusWeeks(1))
        TipoPeriodo.MESE -> di(TipoPeriodo.MESE, da.minusMonths(1))
        TipoPeriodo.ANNO -> di(TipoPeriodo.ANNO, da.minusYears(1))
        TipoPeriodo.PERSONALIZZATO -> Periodo(tipo, da.minusDays(lunghezza.toLong()), da.minusDays(1))
    }

    /** Stesso periodo dell'anno prima. */
    fun annoPrima(): Periodo = when (tipo) {
        TipoPeriodo.PERSONALIZZATO -> Periodo(tipo, da.minusYears(1), a.minusYears(1))
        else -> di(tipo, da.minusYears(1))
    }

    fun descrizione(): String = when (tipo) {
        TipoPeriodo.GIORNO -> Tempo.etichetta(da)
        TipoPeriodo.MESE -> "${Tempo.nomiMesi[da.monthValue - 1]} ${da.year}"
        TipoPeriodo.ANNO -> "anno ${da.year}"
        else -> "dal ${Tempo.etichetta(da)} al ${Tempo.etichetta(a)}"
    }

    companion object {
        fun di(tipo: TipoPeriodo, rif: LocalDate): Periodo = when (tipo) {
            TipoPeriodo.GIORNO -> Periodo(tipo, rif, rif)
            TipoPeriodo.SETTIMANA -> {
                val l = rif.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Periodo(tipo, l, l.plusDays(6))
            }
            TipoPeriodo.MESE -> Periodo(tipo, rif.withDayOfMonth(1), rif.with(TemporalAdjusters.lastDayOfMonth()))
            TipoPeriodo.ANNO -> Periodo(tipo, rif.withDayOfYear(1), rif.with(TemporalAdjusters.lastDayOfYear()))
            TipoPeriodo.PERSONALIZZATO -> Periodo(tipo, rif.minusDays(29), rif)
        }

        fun ultimiGiorni(n: Int, oggi: LocalDate): Periodo = Periodo(TipoPeriodo.PERSONALIZZATO, oggi.minusDays(n - 1L), oggi)
    }
}
