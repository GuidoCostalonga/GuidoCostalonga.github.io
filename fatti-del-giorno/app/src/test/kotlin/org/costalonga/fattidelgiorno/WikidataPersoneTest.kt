package org.costalonga.fattidelgiorno

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.costalonga.fattidelgiorno.dati.rete.ClientHttp
import org.costalonga.fattidelgiorno.dati.rete.WikidataPersone
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class WikidataPersoneTest {

    private val servizio = MockWebServer()

    @Before
    fun avvia() = servizio.start()

    @After
    fun ferma() = servizio.shutdown()

    private fun fonte(ambito: WikidataPersone.Ambito = WikidataPersone.Ambito.NATI_FVG) =
        WikidataPersone(ClientHttp(), ambito, oggiIso = { "2026-10-22" }, endpoint = servizio.url("/sparql").toString())

    @Test
    fun `l'interrogazione chiede il giorno, il mese e la precisione al giorno`() {
        val q = fonte().query(6, 5)
        assertTrue(q.contains("MONTH(?quando) = 5"))
        assertTrue(q.contains("DAY(?quando) = 6"))
        assertTrue(q.contains("wikibase:timePrecision 11"))
        assertTrue(q.contains("wd:Q1250"))
        assertTrue(fonte(WikidataPersone.Ambito.MORTI_FVG).query(6, 5).contains("P570"))
        assertTrue(fonte(WikidataPersone.Ambito.NATI_ITALIA).query(6, 5).contains("wd:Q38"))
    }

    @Test
    fun `le righe diventano schede con livello di verifica e senza doppioni`() {
        val fatti = fonte().interpreta(Prova.risorsa("wikidata_fvg_22_10.json"), 22, 10)
        assertEquals(4, fatti.size) // cinque righe, una ripetuta

        val castiglioni = fatti.first { it.titolo == "Camillo Castiglioni" }
        assertEquals(LivelloVerifica.MEDIO, castiglioni.verifica) // ha la voce in italiano
        assertEquals(2, castiglioni.fonti.size)
        assertEquals("https://it.wikipedia.org/wiki/Camillo_Castiglioni", castiglioni.fonti[0].url)
        assertEquals("http://www.wikidata.org/entity/Q84183", castiglioni.fonti[1].url)
        assertEquals("Q84183", castiglioni.entita)
        assertEquals(1879, castiglioni.anno)
        assertEquals(Area.FVG, castiglioni.area)
        assertEquals(Categoria.NASCITA, castiglioni.categoria)
        assertEquals(Collegamento.NATO, castiglioni.collegamento)
        assertTrue(castiglioni.testo.contains("Nato/a a Trieste il 22 ottobre 1879"))

        val franchi = fatti.first { it.titolo == "Denis Franchi" }
        assertEquals(LivelloVerifica.BASSO, franchi.verifica) // nessuna voce collegata

        val discordante = fatti.first { it.titolo == "Persona Di Prova" }
        assertEquals(LivelloVerifica.INCOERENTE, discordante.verifica)
        assertTrue(discordante.note.any { it.contains("1965") && it.contains("1966") })
    }

    @Test
    fun `la chiamata manda l'interrogazione e legge la risposta`() = runBlocking {
        servizio.enqueue(MockResponse().setBody(Prova.risorsa("wikidata_fvg_22_10.json")))
        val esito = fonte().cerca(22, 10)
        assertEquals(null, esito.avviso)
        assertEquals(4, esito.fatti.size)
        val richiesta = servizio.takeRequest()
        assertTrue(richiesta.path!!.startsWith("/sparql?format=json&query="))
        val query = URLDecoder.decode(richiesta.path!!.substringAfter("query="), "UTF-8")
        assertTrue(query.contains("DAY(?quando) = 22"))
        assertEquals("application/sparql-results+json", richiesta.getHeader("Accept"))
    }

    @Test
    fun `il limite del servizio viene segnalato`() = runBlocking {
        servizio.enqueue(MockResponse().setResponseCode(429))
        assertEquals(TipoAvviso.LIMITE, fonte().cerca(22, 10).avviso?.tipo)
    }
}
