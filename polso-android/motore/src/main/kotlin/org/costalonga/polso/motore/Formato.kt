package org.costalonga.polso.motore

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Numeri all'italiana: virgola decimale, punto per le migliaia. */
object Formato {
    private val simboli = DecimalFormatSymbols(Locale.ITALY)

    fun numero(v: Double?, decimali: Int = 0): String {
        if (v == null || v.isNaN()) return "—"
        val schema = if (decimali <= 0) "#,##0" else "#,##0." + "0".repeat(decimali)
        return DecimalFormat(schema, simboli).format(v)
    }

    fun conUnita(v: Double?, unita: String, decimali: Int = 0): String =
        if (v == null || v.isNaN()) "—" else "${numero(v, decimali)} $unita".trimEnd()

    fun percentuale(v: Double?, decimali: Int = 0): String =
        if (v == null || v.isNaN()) "—" else "${numero(v, decimali)}%"

    fun conSegno(v: Double?, decimali: Int = 0): String =
        if (v == null || v.isNaN()) "—" else (if (v > 0) "+" else if (v < 0) "−" else "") + numero(abs(v), decimali)

    /** 437 minuti → "7 h 17 min". */
    fun durata(minuti: Double?): String {
        if (minuti == null || minuti.isNaN()) return "—"
        val m = minuti.roundToInt()
        val segno = if (m < 0) "−" else ""
        val a = abs(m)
        return when {
            a < 60 -> "$segno$a min"
            a % 60 == 0 -> "$segno${a / 60} h"
            else -> "$segno${a / 60} h ${a % 60} min"
        }
    }

    /** Minuti dalla mezzanotte → "23:40". */
    fun orario(minuti: Double?): String {
        if (minuti == null || minuti.isNaN()) return "—"
        val m = ((minuti.roundToInt() % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** Passo in min/km da secondi per km → "5:32 min/km". */
    fun passo(secondiPerKm: Double?): String {
        if (secondiPerKm == null || secondiPerKm.isNaN() || secondiPerKm <= 0) return "—"
        val s = secondiPerKm.roundToInt()
        return "%d:%02d min/km".format(s / 60, s % 60)
    }

    fun distanza(metri: Double?): String =
        if (metri == null) "—" else if (metri >= 1000) "${numero(metri / 1000, 2)} km" else "${numero(metri)} m"

    fun pValore(p: Double?): String = when {
        p == null -> "—"
        p < 0.001 -> "< 0,001"
        else -> numero(p, 3)
    }
}
