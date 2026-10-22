package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dominio.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DateTest {

    private fun orologio(iso: String) = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)

    @Test
    fun `oggi segue il fuso di Roma e non quello UTC`() {
        // Alle 23:30 UTC del 28 marzo a Roma è già il 29 marzo (ora solare, UTC+1).
        assertEquals(LocalDate.of(2026, 3, 29), Date.oggi(orologio("2026-03-28T23:30:00Z")))
        // D'estate (UTC+2) alle 22:30 UTC del 1 luglio a Roma è il 2 luglio.
        assertEquals(LocalDate.of(2026, 7, 2), Date.oggi(orologio("2026-07-01T22:30:00Z")))
        // Alle 10 UTC la data coincide.
        assertEquals(LocalDate.of(2026, 10, 22), Date.oggi(orologio("2026-10-22T10:00:00Z")))
    }

    @Test
    fun `il calendario restituisce la mezzanotte UTC e la conversione non sbaglia giorno`() {
        val data = LocalDate.of(2024, 2, 29)
        assertEquals(data, Date.daMillisecondiUtc(Date.aMillisecondiUtc(data)))
        // Anche con qualche ora in più nello stesso giorno UTC.
        assertEquals(data, Date.daMillisecondiUtc(Date.aMillisecondiUtc(data) + 5 * 3_600_000L))
        assertEquals(LocalDate.of(1969, 12, 31), Date.daMillisecondiUtc(-1L))
    }

    @Test
    fun `il 29 febbraio si puo cercare, il 31 aprile no`() {
        assertTrue(Date.giornoValido(29, 2))
        assertFalse(Date.giornoValido(30, 2))
        assertFalse(Date.giornoValido(31, 4))
        assertTrue(Date.giornoValido(31, 12))
        assertFalse(Date.giornoValido(1, 13))
        assertFalse(Date.giornoValido(0, 1))
    }

    @Test
    fun `le date si scrivono in italiano`() {
        assertEquals("giovedì 22 ottobre 2026", Date.estesa(LocalDate.of(2026, 10, 22)))
        assertEquals("22 ottobre", Date.giornoMese(22, 10))
        assertEquals("1 gennaio", Date.giornoMese(1, 1))
        assertEquals("22/10/2026", Date.breve(LocalDate.of(2026, 10, 22)))
        assertEquals("05", Date.dueCifre(5))
    }

    @Test
    fun `il momento della consultazione si legge con l'ora di Roma`() {
        val iso = Date.adesso(orologio("2026-10-22T19:30:00Z"))
        assertTrue(iso, iso.startsWith("2026-10-22T21:30"))
        assertEquals("22/10/2026 alle 21:30", Date.momentoLeggibile(iso))
        assertEquals("2026-10-22", Date.giornoConsultazione(orologio("2026-10-22T19:30:00Z")))
    }
}
