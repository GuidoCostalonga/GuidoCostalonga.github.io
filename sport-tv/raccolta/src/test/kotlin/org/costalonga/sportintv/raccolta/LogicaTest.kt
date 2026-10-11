package org.costalonga.sportintv.raccolta

import org.costalonga.sportintv.raccolta.connettori.RaiPlay
import org.costalonga.sportintv.raccolta.connettori.SuperTennis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LogicaTest {

    private val adesso = Instant.parse("2026-10-04T10:00:00Z")

    private fun trasmesso(
        fonte: String, canale: String, inizio: String, titolo: String, partecipanti: List<String> = emptyList(),
        tipo: TipoTrasmissione = TipoTrasmissione.DIRETTA, sport: Sport = Sport.CALCIO, fine: String? = null,
    ) = Elemento(
        fonte = fonte, sport = sport, genere = "m", competizione = "Prova", titolo = titolo, partecipanti = partecipanti,
        inizio = Instant.parse(inizio), fine = fine?.let { Instant.parse(it) }, verificato = adesso, canale = canale,
        piattaforma = canale, tipo = tipo, accesso = Accesso.IN_CHIARO, link = "https://esempio.invalid/$canale",
    )

    private fun calendario(inizio: String, partecipanti: List<String>) = Elemento(
        fonte = "openfootball", sport = Sport.CALCIO, genere = "m", competizione = "Serie A",
        titolo = partecipanti.joinToString(" - "), partecipanti = partecipanti, inizio = Instant.parse(inizio),
        fine = null, verificato = adesso, notaSeNonTrasmesso = "Da confermare",
    )

    // ------------------------------------------------------------------ orari

    @Test
    fun oraLegale_finisceIl25Ottobre2026() {
        // Prima del cambio: UTC+2.
        assertEquals(Instant.parse("2026-10-24T18:45:00Z"), RaiPlay.istante("24/10/2026", "20:45"))
        // Dopo il cambio: UTC+1.
        assertEquals(Instant.parse("2026-10-25T19:45:00Z"), RaiPlay.istante("25/10/2026", "20:45"))
        // L'ora ripetuta (02:30 esiste due volte) prende la prima occorrenza, in ora legale.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), RaiPlay.istante("25/10/2026", "02:30"))
    }

    @Test
    fun oraLegale_cominciaIl29Marzo2026() {
        assertEquals(Instant.parse("2026-03-28T19:45:00Z"), RaiPlay.istante("28/03/2026", "20:45"))
        assertEquals(Instant.parse("2026-03-29T18:45:00Z"), RaiPlay.istante("29/03/2026", "20:45"))
        // Le 02:30 del 29 marzo non esistono: java.time le sposta alle 03:30 (UTC+2).
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), RaiPlay.istante("29/03/2026", "02:30"))
    }

    @Test
    fun superTennis_orarioUtc() {
        assertEquals(Instant.parse("2026-10-04T14:00:00Z"), SuperTennis.istante("10/04/2026 02:00:00 PM"))
    }

    @Test
    fun finestra_daMezzanotteItalianaPerQuindiciGiorni() {
        val f = Finestra.standard(Instant.parse("2026-10-04T23:30:00Z")) // già il 5 ottobre in Italia
        assertEquals(LocalDate.of(2026, 10, 5), f.primoGiorno)
        assertEquals(Instant.parse("2026-10-04T22:00:00Z"), f.da)
        // Il 25 ottobre il giorno dura 25 ore: la fine è a mezzanotte italiana (UTC+1).
        assertEquals(Instant.parse("2026-10-19T22:00:00Z"), f.a)
        assertEquals(15, f.date.size)
    }

    // ------------------------------------------------------------------- testi

    @Test
    fun partecipanti_formeDiverse() {
        assertEquals(listOf("Roma", "Lazio"), Testo.partecipanti("Roma - Lazio"))
        assertEquals(listOf("Jannik Sinner", "Alex De Minaur"), Testo.partecipanti("Jannik SINNER (ITA) vs Alex DE MINAUR (AUS)", atleti = true))
        assertEquals(listOf("Washington Commanders", "Indianapolis Colts"), Testo.partecipanti("Indianapolis Colts @ Washington Commanders"))
        assertEquals(listOf("Italia", "Turchia"), Testo.partecipanti("Italia - Turchia - 05/10/2026"))
        assertEquals(listOf("Bielorussia", "Italia"), Testo.partecipanti("Qualificazioni Mondiali femminili 2027 - Bielorussia - Italia"))
        assertEquals(emptyList<String>(), Testo.partecipanti("Europei di Ciclismo 2026 - Donne Élite - prima parte"))
        assertEquals(emptyList<String>(), Testo.partecipanti("Milano-Sanremo"))
        assertEquals(listOf("Colts", "Commanders"), Testo.partecipanti("Colts-Commanders", trattinoStretto = true))
    }

    @Test
    fun stessoPartecipante_nomiDiversiDellaStessaSquadra() {
        assertTrue(Testo.stessoPartecipante("Inter", "FC Internazionale Milano"))
        assertTrue(Testo.stessoPartecipante("Napoli", "SSC Napoli"))
        assertTrue(Testo.stessoPartecipante("Colts", "Indianapolis Colts"))
        assertTrue(Testo.stessoPartecipante("Barcellona", "Barcelona"))
        assertFalse(Testo.stessoPartecipante("Juventus", "Juve Stabia"))
        assertFalse(Testo.stessoPartecipante("Milan", "Inter"))
    }

    @Test
    fun nomiBreviDelleSquadre() {
        assertEquals("Sassuolo", Testo.nomeBreveSquadra("US Sassuolo Calcio"))
        assertEquals("Bologna", Testo.nomeBreveSquadra("Bologna FC 1909"))
        assertEquals("Internazionale Milano", Testo.nomeBreveSquadra("FC Internazionale Milano"))
        assertEquals("Como", Testo.nomeBreveSquadra("Como 1907"))
    }

    @Test
    fun riconoscimentoDelloSport() {
        assertEquals(Sport.VOLLEY, Sport.riconosci("Pallavolo. Serie A1 Fineco - 2a g.ta"))
        assertEquals(Sport.CALCIO, Sport.riconosci("Calcio: Serie A Women"))
        assertEquals(Sport.CALCIO_A_5, Sport.riconosci("Calcio a 5: Serie A"))
        assertEquals(Sport.FOOTBALL_AMERICANO, Sport.riconosci("Football Americano"))
        assertEquals(Sport.TRIATHLON, Sport.riconosci("Triathlon - World Cup"))
        assertEquals(Sport.SPORT_INVERNALI, Sport.riconosci("Sci alpino: slalom"))
        assertEquals(Sport.ALTRO, Sport.riconosci("Scienza e tecnologia"))
        assertEquals(Sport.ORIENTAMENTO, Sport.riconosci("Orientamento. Coppa Italia"))
    }

    @Test
    fun rubricheEsclusse() {
        assertTrue(Testo.eRubrica("Speciale Tg Sport: Rally Sardegna"))
        assertTrue(Testo.eRubrica("Sport Mediaset Monday Night"))
        assertTrue(Testo.eRubrica("90° minuto"))
        assertFalse(Testo.eRubrica("Calcio: Serie A Women - Roma - Lazio"))
        assertFalse(Testo.eRubrica("Il Lombardia"))
    }

    @Test
    fun tipoDiTrasmissione_soloDaParoleEsplicite() {
        assertEquals(TipoTrasmissione.REPLICA, Testo.tipoDaTesto("WTA Pechino (replica)"))
        assertEquals(TipoTrasmissione.DIRETTA, Testo.tipoDaTesto("LIVE WTA 1000 Pechino"))
        assertEquals(TipoTrasmissione.DIFFERITA, Testo.tipoDaTesto("Giro d'Italia in differita"))
        assertEquals(TipoTrasmissione.NON_INDICATO, Testo.tipoDaTesto("Roma - Lazio"))
        assertTrue(Testo.soloAnniPassati("ATP 1000 Shanghai 2025", 2026))
        assertFalse(Testo.soloAnniPassati("Serie A 2025-2026", 2026))
        assertFalse(Testo.soloAnniPassati("Serie A 2026-2027", 2026))
    }

    // ------------------------------------------------------------------ unione

    @Test
    fun unione_stessaPartitaSuDueCanaliDiventaUnEventoConDueTrasmissioni() {
        val eventi = Unione.unisci(
            listOf(
                trasmesso("dazn", "DAZN", "2026-10-11T16:00:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan")),
                trasmesso("raiplay", "Rai 2", "2026-10-11T15:45:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan"), TipoTrasmissione.NON_INDICATO),
                calendario("2026-10-11T16:00:00Z", listOf("Sassuolo", "AC Milan")),
            )
        )
        val e = eventi.single()
        assertEquals(2, e.trasmissioni.size)
        assertEquals(setOf("dazn", "raiplay", "openfootball"), e.fonti.toSet())
        assertEquals(StatoEvento.CONFERMATO, e.stato)
        // L'inizio è il calcio d'inizio del calendario, non l'inizio dello studio prepartita.
        assertEquals(Instant.parse("2026-10-11T16:00:00Z"), e.inizio)
        assertTrue(e.incertezze.isEmpty())
        // Nessuna nota "da confermare": la trasmissione c'è.
        assertFalse(e.nota.orEmpty().contains("Da confermare"))
    }

    @Test
    fun unione_calendarioSenzaPalinsestoRestaDaConfermare() {
        val e = Unione.unisci(listOf(calendario("2026-10-18T16:00:00Z", listOf("Milan", "Atalanta")))).single()
        assertEquals(StatoEvento.DA_CONFERMARE, e.stato)
        assertFalse(e.trasmissioneVerificata)
        assertEquals("Da confermare", e.nota)
    }

    @Test
    fun unione_orariDiscordantiVengonoSegnalati() {
        val e = Unione.unisci(
            listOf(
                trasmesso("dazn", "DAZN", "2026-10-11T13:00:00Z", "Lecce - Bologna", listOf("Lecce", "Bologna")),
                calendario("2026-10-11T10:30:00Z", listOf("Lecce", "Bologna")),
            )
        ).single()
        assertEquals(1, e.incertezze.size)
        assertTrue(e.incertezze.single().contains("12:30") && e.incertezze.single().contains("15:00"))
    }

    @Test
    fun unione_replicaDelGiornoDopoRestaSeparataConIdentificativoDiverso() {
        val eventi = Unione.unisci(
            listOf(
                trasmesso("dazn", "DAZN", "2026-10-04T13:00:00Z", "Milan - Juventus", listOf("Milan", "Juventus")),
                trasmesso("raiplay", "Rai Sport", "2026-10-05T00:00:00Z", "Milan - Juventus", listOf("Milan", "Juventus"), TipoTrasmissione.NON_INDICATO),
                trasmesso("raiplay", "Rai Sport", "2026-10-05T08:00:00Z", "Milan - Juventus", listOf("Milan", "Juventus"), TipoTrasmissione.REPLICA),
            )
        )
        assertEquals(3, eventi.size)
        assertEquals(3, eventi.map { it.id }.toSet().size)
    }

    @Test
    fun unione_identificativoStabileAncheSeCambiaLOrarioNelloStessoGiorno() {
        val prima = Unione.unisci(listOf(trasmesso("dazn", "DAZN", "2026-10-11T16:00:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan")))).single()
        val dopo = Unione.unisci(listOf(trasmesso("dazn", "DAZN", "2026-10-11T18:45:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan")))).single()
        assertEquals(prima.id, dopo.id)
        val altroGiorno = Unione.unisci(listOf(trasmesso("dazn", "DAZN", "2026-10-12T18:45:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan")))).single()
        assertNotEquals(prima.id, altroGiorno.id)
    }

    @Test
    fun unione_eventiSenzaPartecipantiConTitoliSimili() {
        val eventi = Unione.unisci(
            listOf(
                trasmesso("dazn", "DAZN", "2026-10-10T08:35:00Z", "Il Lombardia", sport = Sport.CICLISMO),
                trasmesso("raiplay", "Rai Sport", "2026-10-10T08:35:00Z", "Il Lombardia", sport = Sport.CICLISMO, tipo = TipoTrasmissione.NON_INDICATO),
                trasmesso("raiplay", "Rai 2", "2026-10-10T12:00:00Z", "Il Lombardia", sport = Sport.CICLISMO, tipo = TipoTrasmissione.NON_INDICATO),
            )
        )
        // Le prime due si sovrappongono, la terza comincia oltre 45 minuti dopo.
        assertEquals(2, eventi.size)
        assertEquals(2, eventi.first().trasmissioni.size)
    }

    // ------------------------------------------------------------- raccoglitore

    private class Finto(override val id: String, val risultato: () -> RisultatoFonte) : Connettore {
        override val nome = id
        override val tipo = TipoFonte.PALINSESTO
        override val url = "https://esempio.invalid"
        override val copertura = ""
        override val canali = emptyList<String>()
        override val condizioni = ""
        override suspend fun raccogli(finestra: Finestra, http: Http) = risultato()
    }

    private val finestra = Finestra(LocalDate.of(2026, 10, 4), 15)

    @Test
    fun raccoglitore_fonteGuastaConservaIDatiPrecedenti() = kotlinx.coroutines.test.runTest {
        val partita = trasmesso("dazn", "DAZN", "2026-10-11T16:00:00Z", "Sassuolo - Milan", listOf("Sassuolo", "Milan"))
        val primo = Raccoglitore(listOf(Finto("dazn") { RisultatoFonte(listOf(partita), 1, 0) }))
            .raccogli(finestra, null, adesso)
        assertEquals(StatoFonte.OK, primo.fonti.single().stato)

        val secondo = Raccoglitore(listOf(Finto("dazn") { throw java.io.IOException("giù") }))
            .raccogli(finestra, primo, adesso.plusSeconds(3600))
        val fonte = secondo.fonti.single()
        assertEquals(StatoFonte.DATI_PRECEDENTI, fonte.stato)
        assertEquals(adesso, fonte.ultimoSuccesso)
        assertEquals(primo.eventi.map { it.id }, secondo.eventi.map { it.id })
        assertTrue(fonte.messaggio!!.contains("potrebbero essere superati"))

        val senzaPrecedente = Raccoglitore(listOf(Finto("dazn") { throw java.io.IOException("giù") }))
            .raccogli(finestra, null, adesso)
        assertEquals(StatoFonte.ERRORE, senzaPrecedente.fonti.single().stato)
        assertTrue(senzaPrecedente.eventi.isEmpty())
    }

    @Test
    fun raccoglitore_distingueParzialeEVuota() = kotlinx.coroutines.test.runTest {
        val p = Raccoglitore(
            listOf(
                Finto("a") { RisultatoFonte(listOf(trasmesso("a", "A", "2026-10-05T10:00:00Z", "X - Y", listOf("X", "Y"))), 3, 1) },
                Finto("b") { RisultatoFonte(emptyList(), 2, 0) },
            )
        ).raccogli(finestra, null, adesso)
        assertEquals(StatoFonte.PARZIALE, p.fonti[0].stato)
        assertEquals(StatoFonte.VUOTA, p.fonti[1].stato)
    }

    @Test
    fun integra_fonteMancanteDalServizioAggiuntaDalTelefono() = kotlinx.coroutines.test.runTest {
        val rai = trasmesso("raiplay", "Rai 2", "2026-10-06T13:25:00Z", "Tre Valli Varesine", sport = Sport.CICLISMO, tipo = TipoTrasmissione.NON_INDICATO)
        val dazn = trasmesso("dazn", "DAZN", "2026-10-06T13:25:00Z", "Tre Valli Varesine", sport = Sport.CICLISMO)
        val servizio = Raccoglitore(
            listOf(Finto("raiplay") { RisultatoFonte(listOf(rai), 1, 0) }, Finto("dazn") { throw java.io.IOException("rifiutato") }),
        ).raccogli(finestra, null, adesso)
        assertEquals(StatoFonte.ERRORE, servizio.fonti[1].stato)
        assertEquals(1, servizio.eventi.single().trasmissioni.size)

        val telefono = Raccoglitore(listOf(Finto("dazn") { RisultatoFonte(listOf(dazn), 1, 0) })).raccogli(finestra, servizio, adesso)
        val unito = Raccoglitore.integra(servizio, telefono, finestra)
        assertEquals(listOf(StatoFonte.OK, StatoFonte.OK), unito.fonti.map { it.stato })
        // Stesso evento, due trasmissioni: Rai dal servizio, DAZN dal telefono.
        assertEquals(setOf("Rai 2", "DAZN"), unito.eventi.single().trasmissioni.map { it.canale }.toSet())
    }

    @Test
    fun raccoglitore_motivoDellErroreRiportato() = kotlinx.coroutines.test.runTest {
        val p = Raccoglitore(listOf(Finto("x") { RisultatoFonte(emptyList(), 0, 3, "La fonte ha risposto con il codice 403") }))
            .raccogli(finestra, null, adesso)
        assertTrue(p.fonti.single().messaggio!!.contains("403"))
    }

    @Test
    fun pacchetto_andataERitornoInJson() {
        val e = Unione.unisci(listOf(calendario("2026-10-18T16:00:00Z", listOf("Milan", "Atalanta"))))
        val p = Pacchetto(generato = adesso, da = finestra.da, a = finestra.a, fonti = emptyList(), eventi = e)
        val testo = JsonSportTv.encodeToString(Pacchetto.serializer(), p)
        assertTrue(testo.contains("\"inizio\":\"2026-10-18T16:00:00Z\""))
        assertTrue(testo.contains("\"stato\":\"da_confermare\""))
        assertEquals(p, JsonSportTv.decodeFromString(Pacchetto.serializer(), testo))
    }
}
