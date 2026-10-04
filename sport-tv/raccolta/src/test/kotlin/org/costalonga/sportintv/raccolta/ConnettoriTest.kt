package org.costalonga.sportintv.raccolta

import org.costalonga.sportintv.raccolta.connettori.Dazn
import org.costalonga.sportintv.raccolta.connettori.JolpicaF1
import org.costalonga.sportintv.raccolta.connettori.MediasetInfinity
import org.costalonga.sportintv.raccolta.connettori.OpenFootballSerieA
import org.costalonga.sportintv.raccolta.connettori.RaiPlay
import org.costalonga.sportintv.raccolta.connettori.SuperTennis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Lettura dei campioni reali salvati in src/test/resources/campioni. */
class ConnettoriTest {

    private fun campione(nome: String) = javaClass.getResource("/campioni/$nome")!!.readText()
    private val adesso = Instant.parse("2026-10-04T18:00:00Z")

    @Test
    fun raiSport_tieneGliEventiEScartaLeRubriche() {
        val elementi = RaiPlay.leggi(campione("raiplay-rai-sport-2026-10-04.json"), RaiPlay.CANALI[3], adesso)
        val titoli = elementi.map { it.titolo }
        // Rubriche e notiziari restano fuori.
        assertTrue(titoli.none { it.contains("Tg Sport") || it.contains("Memory") || it.contains("Tender") || it.contains("Diretta Azzurra") })
        // La partita è letta con partecipanti, competizione e orario corretti.
        val derby = elementi.single { it.partecipanti == listOf("Roma", "Lazio") }
        assertEquals(Sport.CALCIO, derby.sport)
        assertEquals("f", derby.genere)
        assertEquals("Serie A Women Athora", derby.competizione)
        // 02:00 ora italiana (ora legale, UTC+2) = 00:00 UTC.
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"), derby.inizio)
        assertEquals(Instant.parse("2026-10-04T02:00:00Z"), derby.fine)
        assertEquals(Accesso.IN_CHIARO, derby.accesso)
        assertTrue(derby.link!!.startsWith("https://www.raiplay.it/"))
        // Rai non dice se è diretta o replica: resta "non indicato".
        assertEquals(TipoTrasmissione.NON_INDICATO, derby.tipo)
        // Triathlon riconosciuto dal titolo anche se Rai lo classifica come "Nuoto".
        assertTrue(elementi.filter { it.titoloOriginale.startsWith("Triathlon") }.all { it.sport == Sport.TRIATHLON })
        assertTrue(elementi.any { it.titoloOriginale.startsWith("Ippica") && it.sport == Sport.IPPICA })
    }

    @Test
    fun rai1_tieneSoloLoSport() {
        val elementi = RaiPlay.leggi(campione("raiplay-rai-1-2026-10-05.json"), RaiPlay.CANALI[0], adesso)
        assertEquals(1, elementi.size)
        val partita = elementi.single()
        assertEquals(listOf("Italia", "Turchia"), partita.partecipanti)
        assertEquals("UEFA Nations League", partita.competizione)
        assertEquals(Instant.parse("2026-10-05T18:30:00Z"), partita.inizio)
    }

    @Test
    fun mediaset_leggeLaPartitaNfl() {
        val elementi = MediasetInfinity.leggi(campione("mediaset-LB-2026-10-04.json"), MediasetInfinity.CANALI[3], adesso)
        val nfl = elementi.single()
        assertEquals(Sport.FOOTBALL_AMERICANO, nfl.sport)
        assertEquals(listOf("Indianapolis Colts", "Washington Commanders"), nfl.partecipanti)
        assertEquals("20 Mediaset", nfl.canale)
        assertTrue(nfl.link!!.startsWith("https://mediasetinfinity.mediaset.it/"))
    }

    @Test
    fun dazn_soloDiretteEFuture_conNomiItalianiEOrdineCasaOspite() {
        val elementi = Dazn.leggi(campione("dazn-2026-10-10.json"), adesso)
        assertTrue(elementi.isNotEmpty())
        assertTrue(elementi.all { it.tipo == TipoTrasmissione.DIRETTA && it.accesso.aPagamento })
        // Il titolo DAZN "Barcellona - Getafe" vince sui Contestants in inglese.
        assertTrue(elementi.any { it.partecipanti == listOf("Barcellona", "Getafe") })
        // "Iowa @ Washington": in trasferta, quindi casa Washington.
        assertTrue(elementi.any { it.partecipanti == listOf("Washington", "Iowa") })
        // "Vamos! Inter - Parma": il prefisso non entra nei partecipanti.
        assertTrue(elementi.any { it.partecipanti == listOf("Inter", "Parma") })
        // Trasmissioni di contorno escluse.
        assertTrue(elementi.none { it.titoloOriginale.startsWith("Buongiorno Serie A") || it.titoloOriginale.startsWith("Vamos! Il sabato") })
        // Partita femminile riconosciuta come tale.
        assertTrue(elementi.single { it.partecipanti == listOf("Liechtenstein", "Gibraltar") }.genere == "f")
    }

    @Test
    fun superTennis_orariUtcEDirette() {
        val elementi = SuperTennis.leggi(campione("supertennis-palinsesto-2026-10-04.html"), adesso)
        assertTrue(elementi.isNotEmpty())
        assertTrue(elementi.all { it.sport == Sport.TENNIS && it.partecipanti.size == 2 })
        val sabalenka = elementi.first { it.partecipanti.any { p -> p.contains("Sabalenka") } && it.tipo == TipoTrasmissione.DIRETTA }
        // "10/04/2026 03:00:00 AM" è UTC: 05:00 in Italia.
        assertEquals(Instant.parse("2026-10-04T03:00:00Z"), sabalenka.inizio)
        assertEquals("WTA 1000 Pechino", sabalenka.competizione)
        assertTrue(elementi.any { it.tipo == TipoTrasmissione.REPLICA })
        // Cognomi in maiuscolo resi leggibili, sigle di nazione tolte.
        assertTrue(elementi.any { it.partecipanti.contains("Nikola Bartunkova") })
    }

    @Test
    fun openfootball_oraItalianaENomiBrevi() {
        val elementi = OpenFootballSerieA.leggi(campione("openfootball-it1-2026-27.json"), adesso)
        // Solo le partite con orario già fissato (le giornate lontane non lo hanno ancora).
        assertTrue(elementi.size in 100..380)
        val p = elementi.single { it.titolo == "Milan - Atalanta" }
        // 18:00 del 18 ottobre, ora legale: 16:00 UTC (come indicano anche altre fonti in UTC).
        assertEquals(Instant.parse("2026-10-18T16:00:00Z"), p.inizio)
        assertTrue(p.calendario)
        assertNull(p.canale)
        assertNotNull(p.notaSeNonTrasmesso)
    }

    @Test
    fun jolpica_sessioniInUtcConNomeItaliano() {
        val elementi = JolpicaF1.leggi(campione("jolpica-f1-2026.json"), adesso)
        val gara = elementi.single { it.titolo == "Gran Premio degli Stati Uniti · Gara" }
        assertEquals(Instant.parse("2026-10-25T20:00:00Z"), gara.inizio)
        assertTrue(elementi.all { it.calendario && it.sport == Sport.MOTORI })
        assertFalse(elementi.any { it.titolo.contains("null") })
    }
}
