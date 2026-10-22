package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dati.locale.RicorrenzeLocali
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Controlla l'archivio vero depositato in assets/ricorrenze.json. */
class RicorrenzeLocaliTest {

    private val archivio = RicorrenzeLocali { File("src/main/assets/ricorrenze.json").readText() }

    @Test
    fun `ogni voce ha una data valida, una fonte con collegamento e la data del controllo`() {
        assertTrue(archivio.voci.size >= 10)
        archivio.voci.forEach { v ->
            assertTrue(v.id, Date.giornoValido(v.giorno, v.mese))
            assertTrue(v.id, v.titolo.isNotBlank() && v.testo.length > 40)
            assertTrue(v.id, v.fonti.isNotEmpty())
            v.fonti.forEach { f ->
                assertTrue("${v.id}: ${f.url}", f.url.startsWith("https://"))
                assertTrue("${v.id}: ${f.nome}", f.nome.isNotBlank())
                assertTrue("${v.id}: senza data di consultazione", f.consultataIl?.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) == true)
            }
        }
        assertEquals(archivio.voci.size, archivio.voci.map { it.id }.toSet().size)
    }

    @Test
    fun `le voci vengono proposte solo nel loro giorno, con verifica alta e funzionano senza rete`() {
        val seiMaggio = archivio.perData(6, 5)
        assertTrue(seiMaggio.any { it.titolo.contains("terremoto", ignoreCase = true) })
        seiMaggio.forEach {
            assertEquals(6, it.giorno); assertEquals(5, it.mese)
            assertEquals(LivelloVerifica.ALTO, it.verifica)
            assertEquals(Area.FVG, it.area)
            assertTrue(it.note.any { n -> n.contains("senza connessione") })
            assertTrue(it.id.startsWith("locale-"))
        }
        assertTrue(archivio.perData(7, 5).none { it.titolo.contains("terremoto", ignoreCase = true) })
        assertTrue(archivio.perData(3, 4).any { it.spiegazioneData() == "ricorrenza celebrata il 3 aprile" })
    }
}
