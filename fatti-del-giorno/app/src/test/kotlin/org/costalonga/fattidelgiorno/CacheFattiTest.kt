package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dati.locale.CacheFatti
import org.costalonga.fattidelgiorno.dominio.RisultatoRicerca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheFattiTest {

    @get:Rule
    val cartella = TemporaryFolder()

    @Test
    fun `salva e rilegge i risultati per data`() {
        val cache = CacheFatti(File(cartella.root, "fatti"))
        assertNull(cache.leggi(22, 10))
        val risultato = RisultatoRicerca(22, 10, listOf(Prova.fatto()), consultatoIl = "2026-10-22T21:30:00+02:00")
        cache.salva(risultato)
        assertEquals(risultato, cache.leggi(22, 10))
        assertNull(cache.leggi(23, 10))
        assertEquals(1, cache.quanteDate())
        cache.svuota()
        assertNull(cache.leggi(22, 10))
    }

    @Test
    fun `un file rovinato non blocca l'app`() {
        val dir = File(cartella.root, "fatti").apply { mkdirs() }
        File(dir, "10-22.json").writeText("{rotto")
        assertNull(CacheFatti(dir).leggi(22, 10))
    }
}
