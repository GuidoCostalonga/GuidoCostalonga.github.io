package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.F
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.MetodoZone
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Preferenze
import org.costalonga.polso.motore.TipoDiario
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.motore.VoceDiario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogoTest {
    @Test fun ogniAnalisiHaUnaProvaCheEsiste() {
        val mancanti = Catalogo.tutte.map { it.def }.filter { d ->
            val (classe, metodo) = d.prova.split('.').let { it[0] to it.getOrNull(1) }
            val c = runCatching { Class.forName("org.costalonga.polso.motore.analisi.$classe") }.getOrNull() ?: return@filter true
            metodo != null && c.methods.none { it.name == metodo }
        }
        assertTrue("Prove mancanti: ${mancanti.map { it.id + " → " + it.prova }}", mancanti.isEmpty())
    }

    @Test fun identificativiUniciESchedeComplete() {
        assertEquals(Catalogo.tutte.size, Catalogo.tutte.map { it.def.id }.toSet().size)
        Catalogo.tutte.forEach { a ->
            with(a.def) { listOf(titolo, datiRichiesti, metodo, unita, minimo, mancanti, limiti).forEach { assertTrue("$id: campo vuoto", it.isNotBlank()) } }
        }
        assertTrue(Catalogo.tutte.size >= 50)
    }

    @Test fun archivioVuotoTutteNonDisponibiliConMotivo() {
        val d = F.dati()
        Catalogo.tutte.forEach { a ->
            val e = a.esegui(Contesto(d, F.ultimi(30), F.OGGI))
            assertTrue("${a.def.id} dovrebbe essere non disponibile", e is Esito.NonDisponibile)
            assertTrue((e as Esito.NonDisponibile).motivo.isNotBlank())
        }
    }

    @Test fun datiDimostrativiNessunErrore() {
        val d = F.demo()
        val esiti = Catalogo.tutte.associate { it.def.id to it.esegui(Contesto(d, F.ultimi(60), F.OGGI)) }
        esiti.forEach { (id, e) -> if (e is Esito.NonDisponibile) assertTrue("$id: ${e.motivo}", !e.motivo.startsWith("Calcolo non riuscito")) }
        assertTrue(esiti.values.count { it is Esito.Disponibile } >= 40)
    }
}

class AnalisiAttivitaTest {
    private val d = F.dati(F.seriePassi(30) { if (it == 5) null else 5000.0 + it * 100 })

    @Test fun passi() {
        val e = F.disp("att.passi", d)
        assertEquals(29, e.copertura.giorniConDati)
        val media = (0 until 30).filter { it != 5 }.map { 5000.0 + it * 100 }.average()
        assertEquals(media, F.voce(e, "Media giornaliera").valore!!, 1e-6)
        assertEquals(7900.0, F.voce(e, "Massimo").valore!!, 1e-9)
        val linea = e.grafici.first() as Grafico.Linea
        assertEquals(null, linea.punti[5].second) // il giorno mancante resta vuoto
    }

    @Test fun distanza() {
        val m = (0 until 3).map { Misura(Metrica.DISTANZA.codice, F.t(F.OGGI.minusDays(it.toLong()), 9), F.t(F.OGGI.minusDays(it.toLong()), 10), 1000.0, "m", null, "hc", "o") }
        assertEquals(3000.0, F.voce(F.disp("att.distanza", F.dati(m)), "Totale").valore!!, 1e-9)
    }

    @Test fun calorie() {
        val m = listOf(Misura(Metrica.CALORIE_ATTIVE.codice, F.t(F.OGGI, 9), F.t(F.OGGI, 10), 250.0, "kcal", null, "hc", "o"))
        assertEquals(250.0, F.voce(F.disp("att.calorie", F.dati(m)), "Totale").valore!!, 1e-9)
    }

    @Test fun minuti() {
        assertTrue(F.esegui("att.minuti", d) is Esito.NonDisponibile)
    }

    @Test fun piani() {
        assertTrue((F.esegui("att.piani", d) as Esito.NonDisponibile).motivo.contains("Nessun dato"))
    }

    @Test fun confronto() {
        val dd = F.dati(F.seriePassi(14) { if (it < 7) 4000.0 + it else 8000.0 + it })
        val e = F.disp("att.confronto", dd, Periodo.ultimiGiorni(7, F.OGGI))
        assertEquals(4007.0, F.voce(e, "Differenza").valore!!, 1e-6)
        assertTrue(e.note.none { it.contains("comprende lo zero") })
    }

    @Test fun settimana() {
        val e = F.disp("att.settimana", d)
        assertEquals(8, e.voci.size)
        assertTrue(e.voci.dropLast(1).all { it.testo.contains("n = ") })
    }

    @Test fun obiettivo() {
        val dd = F.dati(F.seriePassi(10) { if (it == 3) 2000.0 else 9000.0 }, pref = Preferenze(obiettivoPassi = 8000))
        val e = F.disp("att.obiettivo", dd, F.ultimi(10))
        assertEquals(6.0, F.voce(e, "Serie attuale").valore!!, 1e-9) // giorni 4..9
        assertEquals(6.0, F.voce(e, "Serie più lunga (tutto lo storico)").valore!!, 1e-9)
        // un giorno mancante interrompe la serie
        val conBuco = F.dati(F.seriePassi(10) { if (it == 7) null else 9000.0 })
        assertEquals(2.0, F.voce(F.disp("att.obiettivo", conBuco, F.ultimi(10)), "Serie attuale").valore!!, 1e-9)
    }

    @Test fun record() {
        val e = F.disp("att.record", d)
        assertEquals(7900.0, F.voce(e, "Giorno migliore").valore!!, 1e-9)
    }

    @Test fun tendenza() {
        val e = F.disp("att.tendenza", d)
        assertEquals(700.0, F.voce(e, "Variazione stimata").valore!!, 1e-6) // 100 passi/giorno = 700/settimana
        assertEquals("in aumento", F.voce(e, "Andamento").testo)
    }

    @Test fun distribuzione() {
        val e = F.disp("att.distribuzione", d)
        assertEquals(29.0, (e.grafici.first() as Grafico.Barre).barre.sumOf { it.second!! }, 1e-9)
    }

    @Test fun orario() {
        assertTrue(F.esegui("att.orario", d) is Esito.Disponibile)
        val giornalieri = F.dati((0 until 10).map { F.passi(F.OGGI.minusDays(it.toLong()), 8000.0, ora = 0, durataOre = 24) })
        assertTrue((F.esegui("att.orario", giornalieri) as Esito.NonDisponibile).motivo.contains("troppo lunghi"))
    }

    @Test fun sedentarieta() {
        // senza frequenza cardiaca non si può sapere se l'orologio era al polso
        assertTrue((F.esegui("att.sedentarieta", d) as Esito.NonDisponibile).motivo.contains("frequenza cardiaca"))
        val demo = F.demo()
        assertNotNull(F.disp("att.sedentarieta", demo).principale ?: 1.0)
    }

    @Test fun regolarita() {
        val costante = F.dati(F.seriePassi(20) { 6000.0 })
        assertEquals(0.0, F.voce(F.disp("att.regolarita", costante), "Coefficiente di variazione").valore!!, 1e-9)
    }
}

class AnalisiCuoreTest {
    private fun fc(g: LocalDate, ora: Int, v: Double) = F.istantanea(Metrica.FREQUENZA_CARDIACA, F.t(g, ora), v)

    @Test fun giornaliera() {
        val d = F.dati(listOf(fc(F.OGGI, 8, 60.0), fc(F.OGGI, 9, 80.0), fc(F.OGGI.minusDays(1), 9, 70.0)))
        val e = F.disp("fc.giornaliera", d)
        assertEquals(70.0, F.voce(e, "Media giornaliera").valore!!, 1e-9)
        assertEquals("60 / 80 bpm", F.voce(e, "Campione più basso / più alto").testo)
    }

    @Test fun notturna() {
        val e = F.disp("fc.notturna", F.demo())
        assertTrue(F.voce(e, "Media notturna").valore!! in 45.0..70.0)
    }

    @Test fun riposo() {
        val e = F.disp("fc.riposo", F.demo())
        assertTrue(e.voci.any { it.etichetta == "Ultimi 7 giorni vs 28 precedenti" })
    }

    @Test fun distribuzione() {
        val e = F.disp("fc.distribuzione", F.demo())
        assertTrue(e.voci.any { it.etichetta == "Zona 3" })
    }

    @Test fun zone() {
        val senzaEta = F.demo(pref = Preferenze())
        assertTrue((F.esegui("fc.zone", senzaEta) as Esito.NonDisponibile).motivo.contains("FC massima"))
        // zona per percentuale: FC massima 180 → limiti 90, 108, 126, 144, 162
        val lim = Cardio.limitiZone(RiferimentiCardiaci(180.0, "", 60.0, ""), MetodoZone.PERCENTUALE_FCMAX)!!
        assertEquals(listOf(90.0, 108.0, 126.0, 144.0, 162.0), lim.map { Math.round(it * 1000) / 1000.0 })
        val karv = Cardio.limitiZone(RiferimentiCardiaci(180.0, "", 60.0, ""), MetodoZone.KARVONEN)!!
        assertEquals(120.0, karv[0], 1e-9) // 60 + 0,5 × 120
        val ini = F.t(F.OGGI, 10)
        val camp = (0..6).map { F.istantanea(Metrica.FREQUENZA_CARDIACA, ini + it * 60_000L, 130.0) }
        val t = Cardio.tempoInZone(camp, ini, ini + 360_000L, lim)
        assertEquals(6.0, t.minuti[3], 1e-9)
        assertEquals(100.0, t.coperturaPct, 1e-9)
        assertTrue(F.disp("fc.zone", F.demo()).voci.size >= 7)
    }

    @Test fun risposta() {
        assertTrue(F.disp("fc.allenamento", F.demo()).voci.isNotEmpty())
    }

    @Test fun recupero() {
        val fine = F.t(F.OGGI, 18)
        val camp = listOf(F.istantanea(Metrica.FREQUENZA_CARDIACA, fine, 160.0), F.istantanea(Metrica.FREQUENZA_CARDIACA, fine + 61_000, 135.0), F.istantanea(Metrica.FREQUENZA_CARDIACA, fine + 119_000, 120.0))
        val r = Cardio.recupero(camp, fine, 60)!!
        assertEquals(25.0, r.first - r.second, 1e-9)
        assertEquals(null, Cardio.recupero(camp.take(1), fine, 60))
        val all = Allenamento(fine - 1_800_000, fine, null, "corsa", "", "hc", "o")
        val e = F.disp("fc.recupero", F.dati(camp, all = listOf(all)))
        assertEquals(25.0, e.voci.first().valore!!, 1e-9)
    }

    @Test fun storico() {
        val e = F.disp("fc.storico", F.demo(), F.ultimi(7))
        assertTrue(e.principale!! in 0.0..100.0)
    }

    @Test fun hrv() {
        assertTrue((F.esegui("fc.hrv", F.demo()) as Esito.NonDisponibile).motivo.contains("Nessun dato"))
    }
}

class AnalisiSonnoTest {
    private val g = F.OGGI
    private val fasi = listOf("leggero" to 120, "profondo" to 60, "sveglio" to 10, "rem" to 90, "leggero" to 140)

    @Test fun durata() {
        val n = F.notte(g, 23, 30, 420, fasi)
        val e = F.disp("sonno.durata", F.dati(sonni = listOf(n)))
        assertEquals(410.0, F.voce(e, "Media per notte").valore!!, 1e-9) // sonno = fasi senza la veglia
        val senzaFasi = F.notte(g, 23, 30, 420)
        assertEquals(420.0, F.voce(F.disp("sonno.durata", F.dati(sonni = listOf(senzaFasi))), "Media per notte").valore!!, 1e-9)
    }

    @Test fun oltreMezzanotteAppartieneAlRisveglio() {
        val n = F.notte(g, 23, 0, 480)
        val d = F.dati(sonni = listOf(n))
        assertEquals(setOf(g), d.notti().keys)
    }

    @Test fun orari() {
        val notti = (0 until 5).map { F.notte(g.minusDays(it.toLong()), 23, 30, 450) }
        val e = F.disp("sonno.orari", F.dati(sonni = notti))
        assertEquals("23:30", F.voce(e, "Addormentamento medio").testo)
        assertEquals("07:00", F.voce(e, "Risveglio medio").testo)
    }

    @Test fun fasi() {
        val notti = (0 until 3).map { F.notte(g.minusDays(it.toLong()), 23, 30, 420, fasi) }
        val e = F.disp("sonno.fasi", F.dati(sonni = notti))
        assertTrue(e.voci.any { it.etichetta == "REM" })
    }

    @Test fun risvegli() {
        val notti = (0 until 3).map { F.notte(g.minusDays(it.toLong()), 23, 30, 420, fasi) }
        val e = F.disp("sonno.risvegli", F.dati(sonni = notti))
        assertEquals(1.0, F.voce(e, "Risvegli medi per notte").valore!!, 1e-9)
    }

    @Test fun weekend() {
        val notti = (0 until 14).map { F.notte(g.minusDays(it.toLong()), 23, 0, 450) }
        val e = F.disp("sonno.weekend", F.dati(sonni = notti), F.ultimi(14))
        assertEquals(0.0, F.voce(e, "Scarto dei punti medi (jet lag sociale)").valore!!, 1.0)
    }

    @Test fun obiettivo() {
        val notti = (0 until 7).map { F.notte(g.minusDays(it.toLong()), 23, 0, 420) }
        val e = F.disp("sonno.obiettivo", F.dati(sonni = notti, pref = Preferenze(obiettivoSonnoMin = 480)))
        assertEquals(-60.0, F.voce(e, "Scostamento medio").valore!!, 1e-9)
        assertEquals("−7 h", F.voce(e, "Saldo ultimi 7 giorni (calcolo)").testo)
    }

    @Test fun sri() {
        val identiche = (0 until 10).map { F.notte(g.minusDays(it.toLong()), 23, 0, 480) }
        assertEquals(100.0, F.voce(F.disp("sonno.sri", F.dati(sonni = identiche)), "SRI").valore!!, 1e-9)
        val irregolari = (0 until 10).map { F.notte(g.minusDays(it.toLong()), if (it % 2 == 0) 22 else 2, 0, 480) }
        assertTrue(F.voce(F.disp("sonno.sri", F.dati(sonni = irregolari)), "SRI").valore!! < 60)
    }

    @Test fun tendenza() {
        val notti = (0 until 20).map { F.notte(g.minusDays(it.toLong()), 23, 0, 400 + (19 - it) * 3) }
        val e = F.disp("sonno.tendenza", F.dati(sonni = notti))
        assertEquals(21.0, F.voce(e, "Variazione stimata").valore!!, 0.5)
    }
}

class AnalisiAllenamentiTest {
    private fun corsa(giorniFa: Long, min: Int, km: Double?) =
        Allenamento(F.t(F.OGGI.minusDays(giorniFa), 18), F.t(F.OGGI.minusDays(giorniFa), 18) + min * 60_000L, null, "corsa", "", "hc", "o", "c$giorniFa", distanzaM = km?.times(1000))

    @Test fun riepilogo() {
        val e = F.disp("all.riepilogo", F.dati(all = listOf(corsa(1, 30, 5.0), corsa(3, 60, null))))
        assertEquals(90.0, F.voce(e, "Durata totale").valore!!, 1e-9)
        assertTrue(F.voce(e, "Distanza totale").testo.contains("1 sessioni con distanza"))
    }

    @Test fun frequenza() {
        val e = F.disp("all.frequenza", F.dati(all = (0 until 8).map { corsa(it * 3L, 40, 7.0) }), F.ultimi(28))
        assertNotNull(F.voce(e, "Minuti a settimana (media)").valore)
    }

    @Test fun sport() {
        assertEquals("Corsa", F.disp("all.sport", F.dati(all = listOf(corsa(1, 30, 5.0)))).voci.first().etichetta)
    }

    @Test fun passo() {
        val a = corsa(1, 30, 5.0)
        assertEquals(360.0, AnalisiAllenamenti.passoSecKm(a)!!, 1e-9) // 6:00 min/km
        assertEquals(10.0, AnalisiAllenamenti.velocitaKmh(a)!!, 1e-9)
        assertEquals(null, AnalisiAllenamenti.passoSecKm(a.copy(sport = "forza")))
    }

    @Test fun progressi() {
        val s = (0 until 6).map { corsa((5 - it) * 4L, 30 - it, 5.0) }
        val e = F.disp("all.progressi", F.dati(all = s))
        assertTrue(F.voce(e, "Variazione del passo").valore!! < 0)
    }

    @Test fun carico() {
        val e = F.disp("all.carico", F.demo())
        assertTrue(F.voce(e, "TRIMP medio per sessione").valore!! > 0)
        // copertura cardiaca insufficiente: nessun calcolo
        assertTrue(F.esegui("all.carico", F.dati(all = listOf(corsa(1, 30, 5.0)), pref = Preferenze(annoNascita = 1976))) is Esito.NonDisponibile)
    }
}

class AnalisiAltriTest {
    private fun m(metrica: Metrica, v: Double, giorniFa: Long = 0) = F.istantanea(metrica, F.t(F.OGGI.minusDays(giorniFa), 3), v)

    @Test fun spo2() {
        val e = F.disp("altri.spo2", F.dati(listOf(m(Metrica.SPO2, 97.0), m(Metrica.SPO2, 89.0, 1))))
        assertEquals("1", F.voce(e, "Letture sotto il 90%").testo)
    }

    @Test fun respirazione() { assertTrue(F.esegui("altri.respirazione", F.dati()) is Esito.NonDisponibile) }
    @Test fun temperatura() { assertTrue(F.esegui("altri.temperatura", F.dati(listOf(m(Metrica.TEMPERATURA_CUTANEA, 0.3)))) is Esito.Disponibile) }

    @Test fun stress() {
        val e = F.disp("altri.stress", F.dati(listOf(m(Metrica.STRESS, 40.0))))
        assertTrue(e.note.any { it.contains("metodo non pubblico") })
    }

    @Test fun vo2max() { assertTrue(F.esegui("altri.vo2max", F.dati(listOf(m(Metrica.VO2MAX, 42.0)))) is Esito.Disponibile) }

    @Test fun peso() {
        val diario = (0 until 5).map { VoceDiario(0, TipoDiario.PESO.codice, F.t(F.OGGI.minusDays(it * 7L), 7), 80.0 + it * 0.5, null, "", 0, 0) }
        val e = F.disp("altri.peso", F.dati(diario = diario))
        assertEquals(-2.0, F.voce(e, "Variazione nel periodo").valore!!, 1e-9)
        assertEquals(-0.5, F.voce(e, "Tendenza").valore!!, 1e-9)
    }

    @Test fun pressione() {
        val diario = listOf(VoceDiario(0, TipoDiario.PRESSIONE.codice, F.t(F.OGGI, 8), 120.0, 80.0, "", 0, 0), VoceDiario(0, TipoDiario.PRESSIONE.codice, F.t(F.OGGI, 20), 130.0, 84.0, "", 0, 0))
        assertEquals("125/82 mmHg", F.voce(F.disp("altri.pressione", F.dati(diario = diario)), "Media").testo)
    }
}

class RelazioniTest {
    @Test fun associazioneRiconosciutaECorretta() {
        // Più passi → più sonno la notte dopo, con rumore.
        val r = java.util.Random(3)
        val passi = (0 until 40).map { 4000.0 + r.nextInt(8000) }
        val misure = passi.mapIndexed { i, v -> F.passi(F.OGGI.minusDays(40L - i), v, id = "x$i") }
        val sonni = passi.mapIndexed { i, v -> F.notte(F.OGGI.minusDays(39L - i), 23, 0, (360 + v / 100 + r.nextInt(20)).toInt()) }
        val d = F.dati(misure, sonni)
        val e = F.disp("rel.passi_sonno", d, F.ultimi(45))
        assertTrue(F.voce(e, "ρ di Spearman").valore!! > 0.7)
        assertTrue(F.voce(e, "Sintesi").testo.startsWith("Associazione"))
        assertTrue(e.note.any { it.contains("non dimostra") })
    }

    @Test fun pochiDatiNonCalcola() {
        val d = F.dati(F.seriePassi(5) { 5000.0 })
        assertTrue((F.esegui("rel.passi_sonno", d) as Esito.NonDisponibile).motivo.contains("14"))
    }

    @Test fun nessunaAssociazioneSuDatiCasuali() {
        val r = java.util.Random(11)
        val misure = (0 until 40).map { F.passi(F.OGGI.minusDays(40L - it), 4000.0 + r.nextInt(8000), id = "y$it") }
        val sonni = (0 until 40).map { F.notte(F.OGGI.minusDays(39L - it), 23, 0, 360 + r.nextInt(120)) }
        val e = F.disp("rel.passi_sonno", F.dati(misure, sonni), F.ultimi(45))
        assertTrue(F.voce(e, "Sintesi").testo.startsWith("Nessuna associazione"))
    }
}

class IndiciTest {
    @Test fun regolarita() {
        val notti = (0 until 10).map { F.notte(F.OGGI.minusDays(it.toLong()), 23, 0, 480) }
        val e = F.disp("ind.regolarita", F.dati(F.seriePassi(20) { 6000.0 }, notti))
        assertEquals(100.0, e.principale!!, 1e-6)
    }

    @Test fun attivita() {
        val e = F.disp("ind.attivita", F.dati(F.seriePassi(10) { if (it % 2 == 0) 9000.0 else 3000.0 }, pref = Preferenze(obiettivoPassi = 8000)), F.ultimi(10))
        assertEquals(50.0, e.principale!!, 1e-9)
        assertTrue(e.voci.any { it.testo.startsWith("non disponibile") }) // nessun allenamento: componente esclusa, non zero
    }

    @Test fun recupero() {
        val solo = F.dati(sonni = (0 until 7).map { F.notte(F.OGGI.minusDays(it.toLong()), 23, 0, 480) })
        assertTrue(F.esegui("ind.recupero", solo) is Esito.NonDisponibile)
        assertTrue(F.disp("ind.recupero", F.demo()).principale!! in 0.0..100.0)
    }
}

class QualitaTest {
    @Test fun copertura() {
        val e = F.disp("qual.copertura", F.dati(F.seriePassi(10) { if (it < 5) 5000.0 else null }), F.ultimi(10))
        assertEquals(50.0, F.voce(e, "Passi").valore!!, 1e-9)
    }

    @Test fun evidenzeSoloConCriteri() {
        val d = F.dati(F.seriePassi(60) { 3000.0 + it * 150.0 })
        val ev = Evidenze.calcola(Contesto(d, Periodo.di(TipoPeriodo.PERSONALIZZATO, F.OGGI).copy(da = F.OGGI.minusDays(29)), F.OGGI))
        assertTrue(ev.any { it.analisiId == "att.tendenza" })
        assertTrue(Evidenze.calcola(Contesto(F.dati(F.seriePassi(30) { 5000.0 }), F.ultimi(30), F.OGGI)).none { it.analisiId == "att.tendenza" })
    }
}
