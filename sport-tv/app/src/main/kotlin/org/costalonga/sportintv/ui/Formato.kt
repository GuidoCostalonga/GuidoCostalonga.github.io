package org.costalonga.sportintv.ui

import org.costalonga.sportintv.raccolta.ROMA
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tutti gli orari si mostrano nel fuso Europe/Rome, ora legale compresa. */
object Formato {
    private val it = Locale.ITALIAN
    private val fOra = DateTimeFormatter.ofPattern("HH:mm", it).withZone(ROMA)
    private val fGiornoBreve = DateTimeFormatter.ofPattern("EEE d", it)
    private val fGiornoLungo = DateTimeFormatter.ofPattern("EEEE d MMMM", it)
    private val fDataOra = DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", it).withZone(ROMA)
    private val fBreve = DateTimeFormatter.ofPattern("d/M 'alle' HH:mm", it).withZone(ROMA)

    fun oggi(adesso: Instant = Instant.now()): LocalDate = adesso.atZone(ROMA).toLocalDate()

    fun ora(i: Instant): String = fOra.format(i)

    fun dataOra(i: Instant): String = fDataOra.format(i)

    /** "Oggi", "Domani", "mar 6". */
    fun etichettaGiorno(g: LocalDate, adesso: Instant = Instant.now()): String {
        val o = oggi(adesso)
        return when (g) {
            o -> "Oggi"
            o.plusDays(1) -> "Domani"
            else -> fGiornoBreve.format(g)
        }
    }

    /** Intestazione di gruppo: "Oggi · domenica 4 ottobre". */
    fun intestazioneGiorno(g: LocalDate, adesso: Instant = Instant.now()): String {
        val lungo = fGiornoLungo.format(g)
        val o = oggi(adesso)
        return when (g) {
            o -> "Oggi · $lungo"
            o.plusDays(1) -> "Domani · $lungo"
            else -> lungo.replaceFirstChar { it.titlecase(it@Formato.it) }
        }
    }

    /** "alle 18:57 di oggi", "ieri alle 21:10", "il 2/10 alle 08:00". */
    fun momento(i: Instant, adesso: Instant = Instant.now()): String {
        val g = i.atZone(ROMA).toLocalDate()
        val o = oggi(adesso)
        return when (g) {
            o -> "oggi alle ${ora(i)}"
            o.minusDays(1) -> "ieri alle ${ora(i)}"
            else -> "il ${fBreve.format(i)}"
        }
    }

    /** "3 ore fa", "25 minuti fa". */
    fun fa(i: Instant, adesso: Instant = Instant.now()): String {
        val d = Duration.between(i, adesso)
        return when {
            d.toMinutes() < 1 -> "adesso"
            d.toMinutes() < 60 -> "${d.toMinutes()} minuti fa"
            d.toHours() < 2 -> "un'ora fa"
            d.toHours() < 48 -> "${d.toHours()} ore fa"
            else -> "${d.toDays()} giorni fa"
        }
    }
}
