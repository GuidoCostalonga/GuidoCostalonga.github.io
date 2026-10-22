package org.costalonga.fattidelgiorno.dominio

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tutte le date dell'app ragionano nel fuso di Roma, qualunque sia quello del telefono. */
object Date {
    val fusoRoma: ZoneId = ZoneId.of("Europe/Rome")

    private val mesi = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    private val giorniSettimana = listOf(
        "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica",
    )

    /** La data di oggi secondo il fuso Europe/Rome. */
    fun oggi(clock: Clock = Clock.systemUTC()): LocalDate = Instant.now(clock).atZone(fusoRoma).toLocalDate()

    fun nomeMese(mese: Int): String = mesi[mese - 1]

    /** "22 ottobre". Il 1 si scrive "1" e non "1º" per restare leggibile ovunque. */
    fun giornoMese(giorno: Int, mese: Int): String = "$giorno ${nomeMese(mese)}"

    /** "giovedì 22 ottobre 2026". */
    fun estesa(data: LocalDate): String =
        "${giorniSettimana[data.dayOfWeek.value - 1]} ${data.dayOfMonth} ${nomeMese(data.monthValue)} ${data.year}"

    /** "22/10/2026". */
    fun breve(data: LocalDate): String = data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY))

    /** Due cifre, per le chiamate alle fonti: 22 ottobre diventa "10" e "22". */
    fun dueCifre(n: Int): String = n.toString().padStart(2, '0')

    /** Il 29 febbraio esiste anche negli anni non bisestili, come data da cercare. */
    fun giornoValido(giorno: Int, mese: Int): Boolean {
        if (mese !in 1..12 || giorno < 1) return false
        val massimo = when (mese) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return giorno <= massimo
    }

    /** Converte i millisecondi scelti nel calendario (mezzanotte UTC) in una data di calendario. */
    fun daMillisecondiUtc(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L))

    fun aMillisecondiUtc(data: LocalDate): Long = data.toEpochDay() * 86_400_000L

    /** Momento attuale, con l'ora di Roma, in formato ISO 8601. */
    fun adesso(clock: Clock = Clock.systemUTC()): String =
        Instant.now(clock).atZone(fusoRoma).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** "22/10/2026 alle 21:30" a partire da un momento ISO 8601. */
    fun momentoLeggibile(iso: String): String = runCatching {
        val quando = java.time.OffsetDateTime.parse(iso).atZoneSameInstant(fusoRoma)
        quando.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm", Locale.ITALY))
    }.getOrDefault(iso)

    fun giornoConsultazione(clock: Clock = Clock.systemUTC()): String = oggi(clock).toString()
}
