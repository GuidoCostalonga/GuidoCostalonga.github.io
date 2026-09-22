package org.costalonga.meteofvg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AttendibilitaTest {

    @Test
    fun `modelli che dicono la stessa cosa danno il punteggio massimo`() {
        val due = listOf(List<Double?>(24) { 12.0 }, List<Double?>(24) { 12.0 })
        assertEquals(96, Lettura.attendibilitaDaSerie(due))
    }

    @Test
    fun `un grado di distanza fra i modelli toglie dodici punti`() {
        val due = listOf(List<Double?>(24) { 12.0 }, List<Double?>(24) { 13.0 })
        assertEquals(84, Lettura.attendibilitaDaSerie(due))
    }

    @Test
    fun `quando i modelli divergono molto il punteggio si ferma a quarantacinque`() {
        val due = listOf(List<Double?>(24) { 5.0 }, List<Double?>(24) { 30.0 })
        assertEquals(45, Lettura.attendibilitaDaSerie(due))
    }

    @Test
    fun `con meno di due modelli il punteggio resta settanta`() {
        assertEquals(70, Lettura.attendibilitaDaSerie(emptyList()))
        assertEquals(70, Lettura.attendibilitaDaSerie(listOf(List<Double?>(24) { 12.0 })))
    }

    @Test
    fun `i buchi nei dati non fanno saltare il conto`() {
        val con = listOf(
            List<Double?>(24) { indice -> if (indice == 3) null else 10.0 },
            List<Double?>(24) { 10.0 },
        )
        assertEquals(96, Lettura.attendibilitaDaSerie(con))
    }
}

class ConsigliTest {

    private fun ore(pioggia: Int, vento: Double): List<Ora> {
        val partenza = LocalDateTime.now(FUSO).withMinute(0).withSecond(0).withNano(0)
        return (0 until 24).map { passo ->
            Ora(
                quando = partenza.plusHours(passo.toLong()),
                temperatura = 18.0,
                percepita = 18.0,
                pioggia = pioggia,
                vento = vento,
                codice = 3,
                diGiorno = true,
            )
        }
    }

    @Test
    fun `sopra il cinquantacinque per cento consiglia l'ombrello`() {
        val consigli = Lettura.consigli(ore(pioggia = 70, vento = 10.0), adesso = 18.0)
        assertEquals("Ombrello consigliato", consigli[0].titolo)
        assertTrue(consigli[0].rilievo)
        assertTrue(consigli[0].testo.contains("70"))
    }

    @Test
    fun `alla soglia esatta non allarma`() {
        val consigli = Lettura.consigli(ore(pioggia = 55, vento = 10.0), adesso = 18.0)
        assertEquals("Pioggia poco probabile", consigli[0].titolo)
        assertTrue(!consigli[0].rilievo)
    }

    @Test
    fun `il vento sostenuto viene segnalato`() {
        val consigli = Lettura.consigli(ore(pioggia = 10, vento = 48.0), adesso = 18.0)
        assertEquals("Attenzione al vento", consigli[1].titolo)
        assertTrue(consigli[1].rilievo)
        assertTrue(consigli[1].testo.contains("48"))
    }

    @Test
    fun `il vento contenuto non viene segnalato`() {
        val consigli = Lettura.consigli(ore(pioggia = 10, vento = 35.0), adesso = 18.0)
        assertEquals("Vento contenuto", consigli[1].titolo)
    }

    @Test
    fun `il caldo sopra i ventisette gradi viene segnalato`() {
        assertEquals(
            "Sole e caldo",
            Lettura.consigli(ore(10, 10.0), adesso = 31.0)[2].titolo,
        )
        assertEquals(
            "Temperatura",
            Lettura.consigli(ore(10, 10.0), adesso = 27.0)[2].titolo,
        )
    }

    @Test
    fun `i consigli sono sempre tre`() {
        assertEquals(3, Lettura.consigli(ore(0, 0.0), adesso = 0.0).size)
        assertEquals(3, Lettura.consigli(emptyList(), adesso = 0.0).size)
    }
}

class GiudizioTest {

    private fun previsione(attendibilita: Int) = Previsione(
        comune = COMUNE_PREDEFINITO,
        temperatura = 10.0,
        percepita = 10.0,
        umidita = 60,
        vento = 5.0,
        codice = 3,
        diGiorno = true,
        ore = emptyList(),
        giorni = emptyList(),
        attendibilita = attendibilita,
        confronti = emptyList(),
        consigli = emptyList(),
        aggiornato = 0L,
    )

    @Test
    fun `le soglie del giudizio sono quelle della pagina`() {
        assertEquals("Alta", previsione(96).giudizio)
        assertEquals("Alta", previsione(85).giudizio)
        assertEquals("Buona", previsione(84).giudizio)
        assertEquals("Buona", previsione(70).giudizio)
        assertEquals("Moderata", previsione(69).giudizio)
        assertEquals("Moderata", previsione(45).giudizio)
    }
}
