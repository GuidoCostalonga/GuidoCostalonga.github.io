package org.costalonga.fattidelgiorno

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.costalonga.fattidelgiorno.dati.FattiRepository
import org.costalonga.fattidelgiorno.dati.locale.CacheFatti
import org.costalonga.fattidelgiorno.dati.locale.RicorrenzeLocali
import org.costalonga.fattidelgiorno.dati.rete.ControlloRete
import org.costalonga.fattidelgiorno.dati.rete.EsitoFonte
import org.costalonga.fattidelgiorno.dati.rete.FonteFatti
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Avviso
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.Fonte
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.RisultatoRicerca
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FattiRepositoryTest {

    @get:Rule
    val cartella = TemporaryFolder()

    private class FonteFinta(override val nome: String, private val risposta: suspend () -> EsitoFonte) : FonteFatti {
        var chiamate = 0
        override suspend fun cerca(giorno: Int, mese: Int): EsitoFonte { chiamate++; return risposta() }
    }

    private val orologio: Clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)

    private val archivio = RicorrenzeLocali {
        """[{"id":"prova","titolo":"Ricorrenza di prova","testo":"Una ricorrenza locale controllata, lunga abbastanza per le prove.","giorno":22,"mese":10,
            "categoria":"RICORRENZA","collegamento":"RICORRENZA","area":"FVG",
            "fonti":[{"nome":"Ente di prova","url":"https://ente.example/prova","consultataIl":"2026-10-22"}]}]"""
    }

    private fun cache() = CacheFatti(File(cartella.root, "fatti"))

    private fun repository(
        fonti: List<FonteFatti>,
        online: Boolean = true,
        cache: CacheFatti = cache(),
        tempoMassimoMs: Long = 2_000,
    ) = FattiRepository(fonti, archivio, cache, ControlloRete { online }, orologio, tempoMassimoMs)

    private fun ok(vararg fatti: Fatto) = EsitoFonte(fatti.toList())
    private fun avviso(nome: String, tipo: TipoAvviso) = EsitoFonte(emptyList(), Avviso(nome, tipo, "prova"))

    @Test
    fun `senza chiavi e con le fonti che rispondono si ottengono le schede e la memoria`() = runBlocking {
        val wikipedia = FonteFinta("Wikipedia") { ok(Prova.fatto(id = "w1"), Prova.fatto(id = "w2", titolo = "Altro fatto", area = Area.MONDO)) }
        val r = repository(listOf(wikipedia)).cerca(22, 10)
        assertFalse(r.daMemoria)
        assertTrue(r.avvisi.isEmpty())
        assertEquals(listOf("locale-prova", "w1", "w2"), r.fatti.map { it.id })
        assertEquals(1, wikipedia.chiamate)
        // In memoria finiscono solo i fatti di rete: l'archivio locale c'è sempre.
        val memoria = cache().leggi(22, 10)
        assertNotNull(memoria)
        assertEquals(listOf("w1", "w2"), memoria!!.fatti.map { it.id })
    }

    @Test
    fun `senza connessione si mostrano solo i contenuti memorizzati, segnalati come tali`() = runBlocking {
        val fonte = FonteFinta("Wikipedia") { ok(Prova.fatto(id = "w1")) }
        // Prima nessuna memoria: solo l'archivio locale e l'avviso.
        val vuoto = repository(listOf(fonte), online = false).cerca(22, 10)
        assertTrue(vuoto.daMemoria)
        assertEquals(listOf("locale-prova"), vuoto.fatti.map { it.id })
        assertEquals(TipoAvviso.OFFLINE, vuoto.avvisi.single().tipo)
        assertEquals(0, fonte.chiamate)

        // Poi con la memoria piena.
        cache().salva(RisultatoRicerca(22, 10, listOf(Prova.fatto(id = "memorizzato")), consultatoIl = "2026-10-20T10:00:00+02:00"))
        val r = repository(listOf(fonte), online = false).cerca(22, 10)
        assertTrue(r.daMemoria)
        assertEquals("2026-10-20T10:00:00+02:00", r.consultatoIl)
        assertTrue(r.fatti.any { it.id == "memorizzato" })
        assertEquals(TipoAvviso.OFFLINE, r.avvisi.single().tipo)
        assertEquals(0, fonte.chiamate)
    }

    @Test
    fun `se una fonte raggiunge il limite si tengono le altre e si avvisa`() = runBlocking {
        val buona = FonteFinta("Wikipedia") { ok(Prova.fatto(id = "w1")) }
        val limitata = FonteFinta("Wikidata") { avviso("Wikidata", TipoAvviso.LIMITE) }
        val r = repository(listOf(buona, limitata)).cerca(22, 10)
        assertTrue(r.fatti.any { it.id == "w1" })
        assertEquals(listOf(TipoAvviso.LIMITE), r.avvisi.map { it.tipo })
        assertEquals("Wikidata", r.avvisi.single().fonte)
    }

    @Test
    fun `se tutte le fonti falliscono e non c'e memoria si spiega che non ci sono risultati verificabili`() = runBlocking {
        val rotta = FonteFinta("Wikipedia") { avviso("Wikipedia", TipoAvviso.ERRORE) }
        val eccezione = FonteFinta("Wikidata") { throw IllegalStateException("esplosa") }
        val archivioVuoto = RicorrenzeLocali { "[]" }
        val repo = FattiRepository(listOf(rotta, eccezione), archivioVuoto, cache(), ControlloRete { true }, orologio)
        val r = repo.cerca(22, 10)
        assertTrue(r.fatti.isEmpty())
        assertEquals(setOf(TipoAvviso.ERRORE, TipoAvviso.VUOTO), r.avvisi.map { it.tipo }.toSet())
        assertTrue(r.avvisi.any { it.messaggio.contains("esplosa") })
        assertTrue(r.avvisi.any { it.messaggio.contains("Nessun risultato verificabile") })
        assertNull(cache().leggi(22, 10)) // niente da memorizzare
    }

    @Test
    fun `se tutte le fonti falliscono ma c'e memoria la si usa, dicendolo`() = runBlocking {
        cache().salva(RisultatoRicerca(22, 10, listOf(Prova.fatto(id = "memorizzato")), consultatoIl = "2026-10-01T10:00:00+02:00"))
        val rotta = FonteFinta("Wikipedia") { avviso("Wikipedia", TipoAvviso.TIMEOUT) }
        val r = repository(listOf(rotta)).cerca(22, 10, forzaAggiornamento = true)
        assertTrue(r.daMemoria)
        assertTrue(r.fatti.any { it.id == "memorizzato" })
        assertTrue(r.avvisi.any { it.tipo == TipoAvviso.TIMEOUT })
        assertTrue(r.avvisi.any { it.messaggio.contains("memorizzati il 01/10/2026") })
    }

    @Test
    fun `una fonte troppo lenta viene abbandonata con un avviso di timeout`() = runBlocking {
        val lenta = FonteFinta("Lenta") { delay(1_500); ok(Prova.fatto(id = "tardi")) }
        val veloce = FonteFinta("Veloce") { ok(Prova.fatto(id = "subito")) }
        val r = repository(listOf(lenta, veloce), tempoMassimoMs = 200).cerca(22, 10)
        assertTrue(r.fatti.any { it.id == "subito" })
        assertFalse(r.fatti.any { it.id == "tardi" })
        assertEquals(TipoAvviso.TIMEOUT, r.avvisi.single().tipo)
        assertEquals("Lenta", r.avvisi.single().fonte)
    }

    @Test
    fun `la memoria recente evita nuove chiamate, salvo aggiornamento forzato`() = runBlocking {
        val fonte = FonteFinta("Wikipedia") { ok(Prova.fatto(id = "w1")) }
        val repo = repository(listOf(fonte))
        repo.cerca(22, 10)
        val seconda = repo.cerca(22, 10)
        assertEquals(1, fonte.chiamate)
        assertTrue(seconda.daMemoria)
        repo.cerca(22, 10, forzaAggiornamento = true)
        assertEquals(2, fonte.chiamate)
    }

    @Test
    fun `lo stesso fatto in Wikipedia e Wikidata diventa una scheda sola con tutte le fonti`() = runBlocking {
        val wikipedia = FonteFinta("Wikipedia") {
            ok(Prova.fatto(id = "wp", titolo = "Camillo Castiglioni", entita = "Q84183", anno = 1879,
                fonti = listOf(Fonte("Wikipedia (it): Camillo Castiglioni", "https://it.wikipedia.org/wiki/Camillo_Castiglioni"))))
        }
        val wikidata = FonteFinta("Wikidata") {
            ok(Prova.fatto(id = "wd", titolo = "Camillo Castiglioni", entita = "Q84183", anno = 1879, verifica = LivelloVerifica.BASSO,
                fonti = listOf(Fonte("Wikidata: Camillo Castiglioni", "http://www.wikidata.org/entity/Q84183"))))
        }
        val r = repository(listOf(wikipedia, wikidata)).cerca(22, 10)
        val schede = r.fatti.filter { it.titolo == "Camillo Castiglioni" }
        assertEquals(1, schede.size)
        assertEquals(LivelloVerifica.MEDIO, schede.single().verifica)
        assertEquals(2, schede.single().fonti.size)
        assertTrue(schede.single().note.any { it.contains("Stesso fatto") })
    }

    @Test
    fun `anni diversi per la stessa entita rendono la scheda discordante`() = runBlocking {
        val a = FonteFinta("A") { ok(Prova.fatto(id = "a", titolo = "Persona", entita = "Q1", anno = 1900)) }
        val b = FonteFinta("B") { ok(Prova.fatto(id = "b", titolo = "Persona", entita = "Q1", anno = 1901, verifica = LivelloVerifica.BASSO)) }
        val r = repository(listOf(a, b)).cerca(22, 10)
        val scheda = r.fatti.single { it.titolo == "Persona" }
        assertEquals(LivelloVerifica.INCOERENTE, scheda.verifica)
        assertTrue(scheda.note.any { it.contains("1900") && it.contains("1901") })
    }

    @Test
    fun `i doppioni per titolo e anno spariscono e l'ordine mette prima il Friuli Venezia Giulia`() = runBlocking {
        val fonte = FonteFinta("Wikipedia") {
            ok(
                Prova.fatto(id = "m", titolo = "Fatto nel mondo", area = Area.MONDO),
                Prova.fatto(id = "i", titolo = "Fatto in Italia", area = Area.ITALIA),
                Prova.fatto(id = "f", titolo = "Fatto in Friuli", area = Area.FVG),
                Prova.fatto(id = "f-bis", titolo = "Fatto in Friuli!", area = Area.FVG),
            )
        }
        val r = repository(listOf(fonte)).cerca(22, 10)
        assertEquals(listOf("locale-prova", "f", "i", "m"), r.fatti.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una data inesistente viene rifiutata`() = runBlocking {
        repository(emptyList()).cerca(31, 4)
        Unit
    }
}
