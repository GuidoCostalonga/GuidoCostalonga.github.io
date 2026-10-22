package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Classificatore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificatoreTest {

    @Test
    fun `riconosce il Friuli Venezia Giulia dai toponimi`() {
        assertEquals(Area.FVG, Classificatore.area("A Trieste viene inaugurato il nuovo molo"))
        assertEquals(Area.FVG, Classificatore.area("Terremoto del Friuli: oltre cento comuni colpiti"))
        assertEquals(Area.FVG, Classificatore.area("Nasce a Pordenone il pittore"))
        assertEquals(Area.FVG, Classificatore.area("Il disastro del Vajont"))
        assertTrue(Classificatore.riguardaFvg("Una storia friulana"))
        // "grado" come parola comune non basta se non è un toponimo intero: qui lo è.
        assertTrue(Classificatore.riguardaFvg("La laguna di Grado"))
        // Parole simili ma diverse non devono ingannare.
        assertFalse(Classificatore.riguardaFvg("Il gradiente della curva"))
        assertTrue(Classificatore.riguardaFvg("La partita dell'Udinese"))
    }

    @Test
    fun `riconosce l'Italia e lascia il resto al mondo`() {
        assertEquals(Area.ITALIA, Classificatore.area("Giorgia Meloni diventa Presidente del Consiglio, politica italiana"))
        assertEquals(Area.ITALIA, Classificatore.area("A Milano apre la prima linea della metropolitana"))
        assertEquals(Area.MONDO, Classificatore.area("Viene fondata la Croce Rossa a Ginevra"))
        assertEquals(Area.MONDO, Classificatore.area("A sword-wielding man attacked a school in Sweden"))
    }

    @Test
    fun `distingue gli eventi storici dalla cultura e dallo sport`() {
        assertEquals(Categoria.CULTURA, Classificatore.categoriaEvento("Esce il film Il Padrino"))
        assertEquals(Categoria.CULTURA, Classificatore.categoriaEvento("La Juventus vince lo scudetto"))
        assertEquals(Categoria.CULTURA, Classificatore.categoriaEvento("Viene assegnato il premio Nobel per la fisica"))
        assertEquals(Categoria.EVENTO, Classificatore.categoriaEvento("Le truppe entrano in città"))
    }

    @Test
    fun `la chiave dei doppioni ignora accenti, maiuscole e punteggiatura`() {
        assertEquals(
            Classificatore.chiaveDoppione("Terremoto del Friuli!", 1976),
            Classificatore.chiaveDoppione("terremoto del friuli", 1976),
        )
        assertEquals("citta di udine|", Classificatore.chiaveDoppione("Città di Udine", null))
        assertEquals("l aquila|", Classificatore.chiaveDoppione("L'Aquila", null))
    }
}
