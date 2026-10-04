package org.costalonga.sportintv

import org.costalonga.sportintv.dati.Archivio
import org.costalonga.sportintv.dati.FiltroAccesso
import org.costalonga.sportintv.dati.Filtri
import org.costalonga.sportintv.dati.Preferiti
import org.costalonga.sportintv.dati.Seguito
import org.costalonga.sportintv.dati.TipoSeguito
import org.costalonga.sportintv.dati.filtra
import org.costalonga.sportintv.dati.inCorso
import org.costalonga.sportintv.dati.inOnda
import org.costalonga.sportintv.promemoria.PianoPromemoria
import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.StatoEvento
import org.costalonga.sportintv.raccolta.TipoTrasmissione
import org.costalonga.sportintv.raccolta.Trasmissione
import org.costalonga.sportintv.raccolta.Unione
import org.costalonga.sportintv.ui.Formato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Logica dell'app senza Android: filtri, preferiti, promemoria, "in corso", formati. */
class AppLogicaTest {

    private val adesso = Instant.parse("2026-10-11T12:00:00Z")

    private fun t(canale: String, piattaforma: String, inizio: String, fine: String?, tipo: TipoTrasmissione, accesso: Accesso) = Trasmissione(
        canale = canale, piattaforma = piattaforma, tipo = tipo, accesso = accesso, link = "https://esempio.invalid",
        inizio = Instant.parse(inizio), fine = fine?.let { Instant.parse(it) }, fonte = "prova", verificato = adesso, titoloOriginale = "",
    )

    private fun evento(
        titolo: String, partecipanti: List<String>, inizio: String, sport: String = "calcio", competizione: String? = "Serie A Enilive",
        trasmissioni: List<Trasmissione> = emptyList(), stato: StatoEvento = if (trasmissioni.isEmpty()) StatoEvento.DA_CONFERMARE else StatoEvento.CONFERMATO,
    ): Evento {
        val e = Evento(
            id = "", sport = sport, competizione = competizione, titolo = titolo, partecipanti = partecipanti,
            inizio = Instant.parse(inizio), stato = stato, trasmissioni = trasmissioni, fonti = listOf("prova"),
        )
        return e.copy(id = "ev_" + Unione.chiaveBase(e).hashCode())
    }

    private val sassuoloMilan = evento(
        "Sassuolo - Milan", listOf("Sassuolo", "Milan"), "2026-10-11T16:00:00Z",
        trasmissioni = listOf(t("DAZN", "DAZN · streaming", "2026-10-11T16:00:00Z", "2026-10-11T18:00:00Z", TipoTrasmissione.DIRETTA, Accesso.ABBONAMENTO)),
    )
    private val sinner = evento(
        "Jannik Sinner - Carlos Alcaraz", listOf("Jannik Sinner", "Carlos Alcaraz"), "2026-10-11T11:00:00Z", sport = "tennis", competizione = "ATP 1000 Shanghai",
        trasmissioni = listOf(t("SuperTennis", "SuperTennis · canale 64 del digitale terrestre", "2026-10-11T11:00:00Z", "2026-10-11T13:00:00Z", TipoTrasmissione.DIRETTA, Accesso.IN_CHIARO)),
    )
    private val ciclismo = evento(
        "Il Lombardia", emptyList(), "2026-10-10T08:35:00Z", sport = "ciclismo", competizione = "UCI World Tour",
        trasmissioni = listOf(t("Rai Sport", "Rai · digitale terrestre e RaiPlay", "2026-10-10T08:35:00Z", "2026-10-10T15:00:00Z", TipoTrasmissione.NON_INDICATO, Accesso.IN_CHIARO)),
    )
    private val women = evento("Roma - Lazio", listOf("Roma", "Lazio"), "2026-10-12T10:00:00Z", competizione = "Serie A Women Athora")
    private val tutti = listOf(ciclismo, sinner, sassuoloMilan, women)

    @Test
    fun filtri_combinati() {
        assertEquals(listOf(sassuoloMilan), tutti.filtra(Filtri(testo = "milan"), emptyList(), emptySet()))
        assertEquals(listOf(sinner), tutti.filtra(Filtri(testo = "SINNER"), emptyList(), emptySet()))
        assertEquals(listOf(sinner, sassuoloMilan), tutti.filtra(Filtri(giorno = LocalDate.of(2026, 10, 11)), emptyList(), emptySet()))
        assertEquals(listOf(ciclismo, sinner), tutti.filtra(Filtri(accesso = FiltroAccesso.GRATUITI), emptyList(), emptySet()))
        assertEquals(listOf(sassuoloMilan), tutti.filtra(Filtri(accesso = FiltroAccesso.A_PAGAMENTO), emptyList(), emptySet()))
        assertEquals(listOf(sinner, sassuoloMilan), tutti.filtra(Filtri(soloDirette = true), emptyList(), emptySet()))
        assertEquals(listOf(ciclismo), tutti.filtra(Filtri(piattaforme = setOf("Rai")), emptyList(), emptySet()))
        assertEquals(listOf(sinner), tutti.filtra(Filtri(sport = setOf("tennis"), soloDirette = true, accesso = FiltroAccesso.GRATUITI), emptyList(), emptySet()))
        // La ricerca trova anche per canale e competizione.
        assertEquals(listOf(sassuoloMilan), tutti.filtra(Filtri(testo = "dazn"), emptyList(), emptySet()))
        assertEquals(listOf(ciclismo), tutti.filtra(Filtri(testo = "world tour"), emptyList(), emptySet()))
    }

    @Test
    fun preferiti_squadreAtletiCompetizioni() {
        val seguiti = listOf(Seguito(TipoSeguito.SQUADRA, "AC Milan"), Seguito(TipoSeguito.ATLETA, "Sinner"))
        assertEquals(listOf(sinner, sassuoloMilan), tutti.filtra(Filtri(soloPreferiti = true), seguiti, emptySet()))
        // Un evento salvato compare anche se non corrisponde ai seguiti.
        assertEquals(listOf(ciclismo), tutti.filtra(Filtri(soloPreferiti = true), emptyList(), setOf(ciclismo.id)))
        // "Serie A" segue la Serie A maschile, non la Serie A femminile.
        val serieA = Seguito(TipoSeguito.COMPETIZIONE, "Serie A")
        assertTrue(Preferiti.corrisponde(sassuoloMilan, listOf(serieA)))
        assertFalse(Preferiti.corrisponde(women, listOf(serieA)))
        assertTrue(Preferiti.corrisponde(women, listOf(Seguito(TipoSeguito.COMPETIZIONE, "Serie A Women"))))
        assertEquals(TipoSeguito.ATLETA, Preferiti.tipoPartecipante(sinner))
        assertEquals(TipoSeguito.SQUADRA, Preferiti.tipoPartecipante(sassuoloMilan))
    }

    @Test
    fun inCorso_soloConDirettaEOrariDellaFonte() {
        assertTrue(sinner.inCorso(adesso))
        assertFalse(sassuoloMilan.inCorso(adesso))
        // Tipo non indicato: "in onda", non "in corso".
        val lombardia = ciclismo.copy(inizio = Instant.parse("2026-10-11T11:00:00Z"), trasmissioni = ciclismo.trasmissioni.map { it.copy(inizio = Instant.parse("2026-10-11T11:00:00Z"), fine = Instant.parse("2026-10-11T15:00:00Z")) })
        assertFalse(lombardia.inCorso(adesso))
        assertTrue(lombardia.inOnda(adesso))
        // Senza ora di fine non si dice "in corso".
        val senzaFine = sinner.copy(trasmissioni = sinner.trasmissioni.map { it.copy(fine = null) })
        assertFalse(senzaFine.inCorso(adesso))
    }

    @Test
    fun formato_oraItalianaConOraLegale() {
        assertEquals("18:00", Formato.ora(Instant.parse("2026-10-11T16:00:00Z")))
        assertEquals("18:00", Formato.ora(Instant.parse("2026-10-25T17:00:00Z"))) // dopo il ritorno all'ora solare
        assertEquals("Oggi", Formato.etichettaGiorno(LocalDate.of(2026, 10, 11), adesso))
        assertEquals("Domani", Formato.etichettaGiorno(LocalDate.of(2026, 10, 12), adesso))
    }

    // -------------------------------------------------------------- promemoria

    private fun promemoriaDi(e: Evento, automatico: Boolean = false) = PianoPromemoria.nuovo(e, 15, automatico)

    @Test
    fun promemoria_orarioCambiatoSiSposta() {
        val p = promemoriaDi(sassuoloMilan)
        val spostato = sassuoloMilan.copy(inizio = Instant.parse("2026-10-11T18:45:00Z"))
        val piano = PianoPromemoria.sincronizza(listOf(p), listOf(spostato), emptyList(), 15, false, adesso)
        val nuovo = piano.daSalvare.single()
        assertEquals(Instant.parse("2026-10-11T18:30:00Z").toEpochMilli(), nuovo.allarme)
        assertEquals(1, piano.avvisi.size)
        assertTrue(piano.daTogliere.isEmpty())
    }

    @Test
    fun promemoria_annullatoSiCancellaConAvviso() {
        val p = promemoriaDi(sassuoloMilan)
        val annullato = sassuoloMilan.copy(stato = StatoEvento.ANNULLATO)
        val piano = PianoPromemoria.sincronizza(listOf(p), listOf(annullato), emptyList(), 15, false, adesso)
        assertEquals(listOf(sassuoloMilan.id), piano.daTogliere)
        assertTrue(piano.avvisi.single().testo.contains("annullato"))
    }

    @Test
    fun promemoria_eventoAssenteNonSiToccaEStessoOrarioNonSiDuplica() {
        val p = promemoriaDi(sassuoloMilan)
        val assente = PianoPromemoria.sincronizza(listOf(p), emptyList(), emptyList(), 15, false, adesso)
        assertTrue(assente.daSalvare.isEmpty() && assente.daTogliere.isEmpty() && assente.avvisi.isEmpty())
        val uguale = PianoPromemoria.sincronizza(listOf(p), listOf(sassuoloMilan), emptyList(), 15, false, adesso)
        assertTrue(uguale.daSalvare.isEmpty() && uguale.daTogliere.isEmpty())
    }

    @Test
    fun promemoria_rinviatoAdAltroGiornoSiRitrovaPerImpronta() {
        val p = promemoriaDi(sassuoloMilan)
        val rinviato = evento(
            "Sassuolo - Milan", listOf("Sassuolo", "Milan"), "2026-10-14T18:45:00Z",
            trasmissioni = sassuoloMilan.trasmissioni.map { it.copy(inizio = Instant.parse("2026-10-14T18:45:00Z"), fine = null) },
        )
        assertEquals(Archivio.impronta(sassuoloMilan), Archivio.impronta(rinviato))
        val piano = PianoPromemoria.sincronizza(listOf(p), listOf(rinviato), emptyList(), 15, false, adesso)
        assertEquals(rinviato.id, piano.daSalvare.single().eventoId)
        assertEquals(listOf(sassuoloMilan.id), piano.daTogliere)
    }

    @Test
    fun promemoria_automaticiPerIPreferitiSenzaDuplicati() {
        val seguiti = listOf(Seguito(TipoSeguito.SQUADRA, "Milan"))
        val primo = PianoPromemoria.sincronizza(emptyList(), tutti, seguiti, 15, true, adesso)
        assertEquals(listOf(sassuoloMilan.id), primo.daSalvare.map { it.eventoId })
        // Al giro successivo il promemoria esiste già: nessun duplicato.
        val secondo = PianoPromemoria.sincronizza(primo.daSalvare, tutti, seguiti, 15, true, adesso)
        assertTrue(secondo.daSalvare.isEmpty())
        // Smesso di seguire: il promemoria automatico si toglie, quello manuale resta.
        val manuale = promemoriaDi(sinner)
        val terzo = PianoPromemoria.sincronizza(primo.daSalvare + manuale, tutti, emptyList(), 15, true, adesso)
        assertEquals(listOf(sassuoloMilan.id), terzo.daTogliere)
    }

    @Test
    fun promemoria_cambioAnticipoRicalcolaLAllarme() {
        val p = promemoriaDi(sassuoloMilan)
        val piano = PianoPromemoria.sincronizza(listOf(p), listOf(sassuoloMilan), emptyList(), 60, false, adesso)
        assertEquals(Instant.parse("2026-10-11T15:00:00Z").toEpochMilli(), piano.daSalvare.single().allarme)
        assertTrue(piano.avvisi.isEmpty())
    }

}
