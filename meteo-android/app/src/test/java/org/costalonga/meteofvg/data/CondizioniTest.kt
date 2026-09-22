package org.costalonga.meteofvg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CondizioniTest {

    /** I codici che Open-Meteo distribuisce: nessuno deve finire in "Variabile". */
    private val codiciWmo = listOf(
        0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99,
    )

    @Test
    fun `ogni codice conosciuto ha una descrizione propria`() {
        codiciWmo.forEach { codice ->
            assertNotEquals(
                "codice $codice senza descrizione",
                "Variabile",
                condizioneDi(codice, diGiorno = true).descrizione,
            )
        }
    }

    @Test
    fun `un codice sconosciuto non inventa nulla`() {
        assertEquals("Variabile", condizioneDi(7, diGiorno = true).descrizione)
        assertEquals(Famiglia.NUVOLE, condizioneDi(7, diGiorno = true).famiglia)
    }

    @Test
    fun `il sereno cambia disegno fra giorno e notte`() {
        assertEquals(Famiglia.SOLE, condizioneDi(0, diGiorno = true).famiglia)
        assertEquals(Famiglia.LUNA, condizioneDi(0, diGiorno = false).famiglia)
        assertEquals(Famiglia.SOLE_NUVOLE, condizioneDi(2, diGiorno = true).famiglia)
        assertEquals(Famiglia.LUNA_NUVOLE, condizioneDi(2, diGiorno = false).famiglia)
    }

    @Test
    fun `la pioggia, la neve e il temporale hanno il disegno giusto`() {
        assertEquals(Famiglia.PIOGGIA, condizioneDi(63, diGiorno = true).famiglia)
        assertEquals(Famiglia.NEVE, condizioneDi(73, diGiorno = true).famiglia)
        assertEquals(Famiglia.TEMPORALE, condizioneDi(95, diGiorno = false).famiglia)
        assertEquals(Famiglia.NEBBIA, condizioneDi(45, diGiorno = true).famiglia)
    }

    @Test
    fun `ogni condizione ha sei fotogrammi, tutti distinti`() {
        Famiglia.entries.forEach { famiglia ->
            val fotogrammi = famiglia.fotogrammi
            assertEquals(famiglia.name, Famiglia.FOTOGRAMMI, fotogrammi.size)
            assertEquals(
                "${famiglia.name}: fotogrammi ripetuti",
                Famiglia.FOTOGRAMMI,
                fotogrammi.toSet().size,
            )
            assertTrue(famiglia.name, fotogrammi.all { it != 0 })
        }
    }

    @Test
    fun `nessun disegno e usato da due condizioni diverse`() {
        val tutti = Famiglia.entries.flatMap { it.fotogrammi.toList() }
        assertEquals(Famiglia.entries.size * Famiglia.FOTOGRAMMI, tutti.toSet().size)
    }
}
