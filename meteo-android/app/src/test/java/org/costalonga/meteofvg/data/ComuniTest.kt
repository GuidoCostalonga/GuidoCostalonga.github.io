package org.costalonga.meteofvg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'elenco dei Comuni non si controlla a occhio: queste prove ripetono le
 * verifiche fatte sulla pagina, così una modifica sbagliata si vede subito.
 */
class ComuniTest {

    @Test
    fun `sono duecentoquindici`() {
        assertEquals(215, COMUNI.size)
    }

    @Test
    fun `i conteggi per provincia sono quelli della regione`() {
        val perSigla = COMUNI.groupingBy { it.sigla }.eachCount()
        assertEquals(25, perSigla["GO"])
        assertEquals(50, perSigla["PN"])
        assertEquals(6, perSigla["TS"])
        assertEquals(134, perSigla["UD"])
    }

    @Test
    fun `nomi, codici ISTAT e indirizzi sono tutti distinti`() {
        assertEquals(215, COMUNI.map { it.nome }.toSet().size)
        assertEquals(215, COMUNI.map { it.istat }.toSet().size)
        assertEquals(215, COMUNI.map { it.slug }.toSet().size)
    }

    @Test
    fun `ogni Comune si ritrova dal proprio indirizzo`() {
        COMUNI.forEach { comune ->
            assertEquals(comune.nome, comunePerSlug(comune.slug)?.nome)
        }
    }

    @Test
    fun `gli indirizzi sono quelli usati dalla pagina`() {
        assertEquals("roveredo-in-piano", slugDi("Roveredo in Piano"))
        assertEquals("san-vito-al-tagliamento", slugDi("San Vito al Tagliamento"))
        assertEquals("tarvisio", slugDi("Tarvisio"))
        // accenti e apostrofi non finiscono nell'indirizzo
        assertEquals("doberdo-del-lago", slugDi("Doberdò del Lago"))
        assertEquals("farra-d-isonzo", slugDi("Farra d'Isonzo"))
    }

    @Test
    fun `le coordinate stanno dentro il Friuli Venezia Giulia`() {
        COMUNI.forEach { comune ->
            assertTrue(
                "${comune.nome}: latitudine ${comune.lat}",
                comune.lat in 45.4..46.7,
            )
            assertTrue(
                "${comune.nome}: longitudine ${comune.lon}",
                comune.lon in 12.3..13.95,
            )
        }
    }

    @Test
    fun `i codici ISTAT hanno cinque cifre`() {
        COMUNI.forEach { comune ->
            assertTrue(comune.nome, comune.istat.length == 5 && comune.istat.all { it.isDigit() })
        }
    }

    @Test
    fun `il Comune predefinito e quelli rapidi ci sono`() {
        assertEquals("Roveredo in Piano", COMUNE_PREDEFINITO.nome)
        assertEquals(
            listOf("Roveredo in Piano", "Pordenone", "Trieste"),
            COMUNI_RAPIDI.map { it.nome },
        )
    }

    @Test
    fun `le province raggruppate coprono tutti i Comuni`() {
        assertEquals(215, COMUNI_PER_PROVINCIA.sumOf { it.second.size })
        assertEquals(
            listOf("Gorizia", "Pordenone", "Trieste", "Udine"),
            COMUNI_PER_PROVINCIA.map { it.first },
        )
    }

    @Test
    fun `un nome che non esiste non restituisce nulla`() {
        assertNull(comunePerNome("Venezia"))
        assertNull(comunePerSlug("venezia"))
        assertNotNull(comunePerNome("Udine"))
    }
}
