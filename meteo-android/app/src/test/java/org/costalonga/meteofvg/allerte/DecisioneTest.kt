package org.costalonga.meteofvg.allerte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole dell'avviso, provate una per una: quando il telefono deve
 * squillare, quando deve tacere e che cosa scrive.
 */
class DecisioneTest {

    private val avviso = "Allerta gialla per temporali forti dalle 12 alle 20 di oggi."

    @Test
    fun `avvisa quando l'allerta compare`() {
        assertTrue(
            Decisione.daAvvisare(
                precedente = StatoAllerte.VUOTO,
                improntaPrecedente = "",
                adesso = StatoAllerte.PIENO,
                improntaAdesso = Decisione.impronta(avviso),
            ),
        )
    }

    @Test
    fun `avvisa anche se prima non si sapeva niente`() {
        assertTrue(
            Decisione.daAvvisare(
                precedente = null,
                improntaPrecedente = null,
                adesso = StatoAllerte.PIENO,
                improntaAdesso = Decisione.impronta(avviso),
            ),
        )
    }

    @Test
    fun `non ripete l'avviso finche' resta lo stesso`() {
        val impronta = Decisione.impronta(avviso)
        assertFalse(
            Decisione.daAvvisare(
                precedente = StatoAllerte.PIENO,
                improntaPrecedente = impronta,
                adesso = StatoAllerte.PIENO,
                improntaAdesso = impronta,
            ),
        )
    }

    @Test
    fun `avvisa di nuovo se l'allerta cambia`() {
        assertTrue(
            Decisione.daAvvisare(
                precedente = StatoAllerte.PIENO,
                improntaPrecedente = Decisione.impronta(avviso),
                adesso = StatoAllerte.PIENO,
                improntaAdesso = Decisione.impronta("Allerta arancione per piogge intense."),
            ),
        )
    }

    @Test
    fun `tace quando non ci sono allerte`() {
        assertFalse(
            Decisione.daAvvisare(
                precedente = StatoAllerte.PIENO,
                improntaPrecedente = Decisione.impronta(avviso),
                adesso = StatoAllerte.VUOTO,
                improntaAdesso = "",
            ),
        )
    }

    @Test
    fun `tace quando il riquadro non e' raggiungibile`() {
        assertFalse(
            Decisione.daAvvisare(
                precedente = StatoAllerte.VUOTO,
                improntaPrecedente = "",
                adesso = StatoAllerte.NON_RAGGIUNGIBILE,
                improntaAdesso = "",
            ),
        )
    }

    @Test
    fun `non avvisa senza testo`() {
        assertFalse(
            Decisione.daAvvisare(
                precedente = StatoAllerte.VUOTO,
                improntaPrecedente = "",
                adesso = StatoAllerte.PIENO,
                improntaAdesso = "",
            ),
        )
    }

    @Test
    fun `spazi e maiuscole non fanno un'allerta nuova`() {
        assertEquals(
            Decisione.impronta(avviso),
            Decisione.impronta("  ALLERTA gialla per temporali forti\n dalle 12 alle 20 di oggi.  "),
        )
    }

    @Test
    fun `testi diversi hanno impronte diverse`() {
        assertNotEquals(
            Decisione.impronta(avviso),
            Decisione.impronta("Allerta rossa per piena dei fiumi."),
        )
    }

    @Test
    fun `l'impronta del vuoto e' vuota`() {
        assertEquals("", Decisione.impronta("   \n  "))
    }

    @Test
    fun `il riassunto si ferma alla prima frase`() {
        assertEquals(
            "Allerta gialla per temporali forti dalle 12 alle 20 di oggi.",
            Decisione.riassunto("$avviso Si raccomanda prudenza negli spostamenti."),
        )
    }

    @Test
    fun `il riassunto taglia alle parole e non a meta'`() {
        val lungo = "Allerta " + "temporali ".repeat(40)
        val riassunto = Decisione.riassunto(lungo, massimo = 60)
        assertTrue(riassunto.length <= 61)
        assertTrue(riassunto.endsWith("…"))
        assertFalse(riassunto.contains("  "))
    }

    @Test
    fun `il riassunto stira gli spazi`() {
        assertEquals(
            "Allerta gialla per vento.",
            Decisione.riassunto("  Allerta   gialla\n\nper vento.  "),
        )
    }
}
