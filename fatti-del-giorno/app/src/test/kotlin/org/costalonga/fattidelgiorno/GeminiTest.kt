package org.costalonga.fattidelgiorno

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.costalonga.fattidelgiorno.ai.Gemini
import org.costalonga.fattidelgiorno.dati.rete.ClientHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiTest {

    private val servizio = MockWebServer()

    @Before
    fun avvia() = servizio.start()

    @After
    fun ferma() = servizio.shutdown()

    private fun gemini() = Gemini(ClientHttp(), base = servizio.url("/v1beta/models/").toString())

    @Test
    fun `la chiave viaggia nell'intestazione e mai nell'indirizzo`() = runBlocking {
        servizio.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"Bozza rifinita"}]}}]}"""))
        val esito = gemini().genera("CHIAVE-SEGRETA", "gemini-2.5-flash", "prompt di prova")
        assertEquals(Gemini.Esito.Ok("Bozza rifinita"), esito)
        val richiesta = servizio.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", richiesta.path)
        assertEquals("CHIAVE-SEGRETA", richiesta.getHeader("x-goog-api-key"))
        assertFalse(richiesta.path!!.contains("CHIAVE"))
        assertTrue(richiesta.body.readUtf8().contains("prompt di prova"))
    }

    @Test
    fun `quota esaurita ed errori diventano messaggi, non eccezioni`() = runBlocking {
        servizio.enqueue(MockResponse().setResponseCode(429))
        assertTrue(gemini().genera("k", "m", "p") is Gemini.Esito.Limite)
        servizio.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid"}}"""))
        val errore = gemini().genera("k", "m", "p")
        assertTrue(errore is Gemini.Esito.Errore)
        assertTrue((errore as Gemini.Esito.Errore).messaggio.contains("400"))
    }

    @Test
    fun `una risposta vuota o malformata viene segnalata`() {
        assertTrue(gemini().estrai("""{"candidates":[]}""") is Gemini.Esito.Errore)
        assertTrue(gemini().estrai("non json") is Gemini.Esito.Errore)
        assertTrue(gemini().estrai("""{"candidates":[{"content":{"parts":[{"text":"  "}]}}]}""") is Gemini.Esito.Errore)
    }
}
