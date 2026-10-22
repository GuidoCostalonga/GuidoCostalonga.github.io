package org.costalonga.fattidelgiorno

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.costalonga.fattidelgiorno.dati.rete.ClientHttp
import org.costalonga.fattidelgiorno.dati.rete.WikipediaOggi
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class WikipediaOggiTest {

    private val servizio = MockWebServer()
    private lateinit var client: ClientHttp

    @Before
    fun avvia() {
        servizio.start()
        client = ClientHttp(client = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build())
    }

    @After
    fun ferma() = servizio.shutdown()

    private fun fonte(lingua: String = "it") =
        WikipediaOggi(client, lingua, oggiIso = { "2026-10-22" }, base = servizio.url("/").toString().trimEnd('/'))

    @Test
    fun `ogni scheda e legata al giorno e al mese richiesti e ha almeno una fonte`() {
        val fatti = fonte().interpreta(Prova.risorsa("wikipedia_it_22_10.json"), 22, 10)
        assertTrue(fatti.size >= 6)
        fatti.forEach { f ->
            assertEquals(22, f.giorno)
            assertEquals(10, f.mese)
            assertTrue(f.fonti.isNotEmpty())
            f.fonti.forEach { assertTrue(it.url, it.url.startsWith("https://")) }
            assertEquals("2026-10-22", f.fonti.first().consultataIl)
            assertEquals(LivelloVerifica.MEDIO, f.verifica)
            assertTrue(f.titolo.isNotBlank())
        }
    }

    @Test
    fun `ricorrenze, eventi e nascite hanno collegamento e categoria giusti`() {
        val fatti = fonte().interpreta(Prova.risorsa("wikipedia_it_22_10.json"), 22, 10)
        val ricorrenza = fatti.first { it.collegamento == Collegamento.RICORRENZA }
        assertEquals(Categoria.RICORRENZA, ricorrenza.categoria)
        assertNull(ricorrenza.anno)
        assertTrue(ricorrenza.spiegazioneData().startsWith("ricorrenza celebrata il 22 ottobre"))

        val meloni = fatti.first { it.testo.startsWith("Giorgia Meloni") }
        assertEquals(2022, meloni.anno)
        assertEquals(Area.ITALIA, meloni.area)
        assertEquals("avvenuto il 22 ottobre 2022", meloni.spiegazioneData())
        assertNotNull(meloni.entita)
        assertTrue(meloni.fonti.first().url.startsWith("https://it.wikipedia.org/wiki/"))

        val trieste = fatti.first { it.testo.startsWith("A Trieste") }
        assertEquals(Area.FVG, trieste.area)
        assertEquals(1901, trieste.anno)

        val nascita = fatti.first { it.collegamento == Collegamento.NATO }
        assertEquals("Camillo Castiglioni", nascita.titolo)
        assertEquals(Categoria.NASCITA, nascita.categoria)
        assertEquals("Q84183", nascita.entita)
        assertEquals("nato/a il 22 ottobre 1879", nascita.spiegazioneData())
    }

    @Test
    fun `la selezione in inglese viene segnalata come tale`() {
        val fatti = fonte("en").interpreta(Prova.risorsa("wikipedia_en_selected_22_10.json"), 22, 10)
        assertEquals(2, fatti.size)
        fatti.forEach {
            assertEquals("en", it.lingua)
            assertTrue(it.note.any { n -> n.contains("inglese") })
            assertTrue(it.fonti.first().url.startsWith("https://en.wikipedia.org/wiki/"))
        }
    }

    @Test
    fun `con una risposta valida la fonte restituisce le schede`() = runBlocking {
        servizio.enqueue(MockResponse().setBody(Prova.risorsa("wikipedia_it_22_10.json")))
        val esito = fonte().cerca(22, 10)
        assertNull(esito.avviso)
        assertTrue(esito.haRisposto)
        assertTrue(esito.fatti.isNotEmpty())
        val richiesta = servizio.takeRequest()
        assertEquals("/api/rest_v1/feed/onthisday/all/10/22", richiesta.path)
        assertTrue(richiesta.getHeader("User-Agent")!!.startsWith("FattiDelGiorno/"))
    }

    @Test
    fun `il limite di richieste diventa un avviso leggibile`() = runBlocking {
        servizio.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5").setBody("troppe richieste"))
        val esito = fonte().cerca(22, 10)
        assertEquals(TipoAvviso.LIMITE, esito.avviso?.tipo)
        assertTrue(esito.avviso!!.messaggio.contains("5 secondi"))
        assertTrue(esito.fatti.isEmpty())
    }

    @Test
    fun `un errore del server e una risposta illeggibile non fanno cadere l'app`() = runBlocking {
        servizio.enqueue(MockResponse().setResponseCode(503))
        assertEquals(TipoAvviso.ERRORE, fonte().cerca(22, 10).avviso?.tipo)
        servizio.enqueue(MockResponse().setBody("<html>non è json</html>"))
        val esito = fonte().cerca(22, 10)
        assertEquals(TipoAvviso.ERRORE, esito.avviso?.tipo)
        assertTrue(esito.avviso!!.messaggio.contains("non leggibile"))
    }

    @Test
    fun `se la fonte non risponde in tempo si segnala il timeout`() = runBlocking {
        servizio.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val esito = fonte().cerca(22, 10)
        assertEquals(TipoAvviso.TIMEOUT, esito.avviso?.tipo)
    }

    @Test
    fun `in inglese si chiede solo la selezione`() = runBlocking {
        servizio.enqueue(MockResponse().setBody(Prova.risorsa("wikipedia_en_selected_22_10.json")))
        fonte("en").cerca(1, 3)
        assertEquals("/api/rest_v1/feed/onthisday/selected/03/01", servizio.takeRequest().path)
    }
}
