package org.costalonga.polso.motore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticheTest {
    @Test fun quantiliComeExcel() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 10.0)
        assertEquals(3.0, Stat.mediana(x)!!, 1e-9)
        assertEquals(2.0, Stat.quantile(x, 0.25)!!, 1e-9)
        assertEquals(7.6, Stat.quantile(x, 0.9)!!, 1e-9) // PERCENTILE.INC di Excel
        assertEquals(2.138, Stat.devStd(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))!!, 0.001)
        assertNull(Stat.media(emptyList()))
        assertNull(Stat.devStd(listOf(1.0)))
    }

    @Test fun mediaMobileNonRiempieIMancanti() {
        val s = listOf(1.0, null, null, null, 5.0, 6.0, 7.0)
        val mm = Stat.mediaMobile(s, 3, 2)
        assertNull(mm[2]) // finestra con un solo valore
        assertNull(mm[3])
        assertEquals(5.5, mm[5]!!, 1e-9)
        assertEquals(6.0, mm[6]!!, 1e-9)
    }

    @Test fun pValoreTDiRiferimento() {
        // t = 2,228 con 10 gradi di libertà → p bilaterale 0,05 (tavole).
        assertEquals(0.05, Stat.pValoreT(2.228, 10.0), 0.001)
        assertEquals(1.0, Stat.pValoreT(0.0, 10.0), 1e-9)
    }

    @Test fun spearmanConPareggi() {
        assertEquals(1.0, Stat.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(10.0, 20.0, 30.0, 40.0))!!, 1e-9)
        assertEquals(-1.0, Stat.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(4.0, 3.0, 2.0, 1.0))!!, 1e-9)
        assertEquals(listOf(1.5, 1.5, 3.0), Stat.ranghi(listOf(5.0, 5.0, 9.0)))
    }

    @Test fun benjaminiHochberg() {
        val q = Stat.benjaminiHochberg(listOf(0.01, 0.04, 0.03, 0.5))
        assertEquals(0.04, q[0], 1e-9)
        assertEquals(0.0533, q[1], 1e-3)
        assertEquals(0.0533, q[2], 1e-3)
        assertEquals(0.5, q[3], 1e-9)
    }

    @Test fun theilSenEMannKendall() {
        val x = (0 until 30).map { it.toDouble() }
        val y = x.map { 100 + 2 * it + if (it == 10.0) 500.0 else 0.0 } // un valore anomalo non sposta la pendenza
        val t = Stat.tendenza(x, y)!!
        assertEquals(2.0, t.pendenza, 1e-9)
        assertTrue(t.p < 0.001)
        val piatta = Stat.tendenza(x, x.map { if (it.toInt() % 2 == 0) 5.0 else 6.0 })!!
        assertTrue(piatta.p > 0.05)
    }

    @Test fun orariCircolariAttornoAMezzanotte() {
        val m = Stat.mediaOraria(listOf(23 * 60, 60))!!
        assertTrue(m < 1 || m > 1439)
        assertEquals(60.0, Stat.devStdOraria(listOf(23 * 60, 60))!!, 5.0)
    }

    @Test fun correlazioneConAutocorrelazioneRiduceN() {
        val x = (0 until 60).map { it.toDouble() }
        val y = x.map { it * 2 + kotlin.math.sin(it) }
        val c = Stat.correlazione(x, y)!!
        assertTrue(c.nEfficace < c.n)
        assertTrue(c.rho > 0.9)
    }

    @Test fun bootstrapRiproducibile() {
        val a = listOf(10.0, 12.0, 11.0, 13.0)
        val b = listOf(5.0, 6.0, 5.5, 6.5)
        assertEquals(Stat.bootstrapDifferenzaMedie(a, b), Stat.bootstrapDifferenzaMedie(a, b))
        assertTrue(Stat.bootstrapDifferenzaMedie(a, b)!!.first > 0)
    }
}

class TempoTest {
    @Test fun giorniDelCambioOra() {
        assertEquals(23.0, Tempo.oreDelGiorno(LocalDate.of(2026, 3, 29), Tempo.ROMA), 1e-9)
        assertEquals(25.0, Tempo.oreDelGiorno(LocalDate.of(2026, 10, 25), Tempo.ROMA), 1e-9)
        assertEquals(24.0, Tempo.oreDelGiorno(LocalDate.of(2026, 10, 5), Tempo.ROMA), 1e-9)
    }

    @Test fun ripartizioneAMezzanotte() {
        val r = Tempo.ripartisci(F.t("2026-10-04T23:00"), F.t("2026-10-05T01:00"), 1000.0, Tempo.ROMA)
        assertEquals(500.0, r[LocalDate.of(2026, 10, 4)]!!, 1e-9)
        assertEquals(500.0, r[LocalDate.of(2026, 10, 5)]!!, 1e-9)
    }

    @Test fun ripartizioneNelGiornoDi25Ore() {
        // Dalle 00:00 del 25 alle 00:00 del 26 ottobre 2026 passano 25 ore reali.
        val r = Tempo.ripartisci(F.t("2026-10-25T00:00"), F.t("2026-10-26T00:00"), 2500.0, Tempo.ROMA)
        assertEquals(1, r.size)
        assertEquals(2500.0, r[LocalDate.of(2026, 10, 25)]!!, 1e-9)
    }

    @Test fun scartoDellaFonteHaPrecedenza() {
        // 23:30 UTC = 01:30 a Roma, ma la fonte dice che la persona era a Londra (+01:00): 00:30 del giorno dopo.
        val t = java.time.Instant.parse("2026-07-10T23:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 7, 11), Tempo.giorno(t, Tempo.ROMA, 3600))
        assertEquals(LocalDate.of(2026, 7, 10), Tempo.giorno(t, Tempo.ROMA, -3600 * 2))
    }

    @Test fun periodi() {
        val s = Periodo.di(TipoPeriodo.SETTIMANA, LocalDate.of(2026, 10, 8))
        assertEquals(LocalDate.of(2026, 10, 5), s.da)
        assertEquals(LocalDate.of(2026, 10, 11), s.a)
        assertEquals(LocalDate.of(2026, 9, 28), s.precedente().da)
        assertTrue(s.parziale(F.OGGI))
        assertEquals(1, s.giorniTrascorsi(F.OGGI).size)
        val m = Periodo.di(TipoPeriodo.MESE, LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 2, 1), m.precedente().da)
        assertEquals(LocalDate.of(2026, 2, 28), m.precedente().a)
    }
}

class AggregazioneTest {
    @Test fun giornoSenzaDatiNonEZero() {
        val d = F.dati(listOf(F.passi(F.OGGI.minusDays(2), 5000.0), F.passi(F.OGGI, 7000.0)))
        val v = d.valoriNelPeriodo(Metrica.PASSI, F.ultimi(3))
        assertNull(v[1].second)
        assertEquals(6000.0, d.osservati(Metrica.PASSI, F.ultimi(3).giorni).average(), 1e-9)
    }

    @Test fun totaleGiornalieroEParzialiDellaStessaOrigineNonSiSommano() {
        val g = F.OGGI.minusDays(1)
        val parziali = (8..11).map { F.passi(g, 1000.0, ora = it) }
        val totale = F.passi(g, 4000.0, ora = 0, durataOre = 24)
        val d = F.dati(parziali + totale)
        val v = d.serie(Metrica.PASSI)[g]!!
        assertEquals(4000.0, v.valore!!, 1e-9)
        assertEquals(1, v.sovrapposteScartate)
    }

    @Test fun fontiSovrappostePrevaleLaPriorita() {
        val g = F.OGGI.minusDays(1)
        val orologio = F.passi(g, 9000.0, "com.hihonor.health")
        val telefono = F.passi(g, 7000.0, "com.android.telefono")
        val conPriorita = F.dati(listOf(orologio, telefono), pref = Preferenze(prioritaOrigini = listOf("com.hihonor.health")))
        assertEquals(9000.0, conPriorita.serie(Metrica.PASSI)[g]!!.valore!!, 1e-9)
        assertEquals(listOf("com.android.telefono"), conPriorita.serie(Metrica.PASSI)[g]!!.altreOrigini)
        val inverso = F.dati(listOf(orologio, telefono), pref = Preferenze(prioritaOrigini = listOf("com.android.telefono")))
        assertEquals(7000.0, inverso.serie(Metrica.PASSI)[g]!!.valore!!, 1e-9)
        // Senza preferenze: mai la somma delle due.
        assertTrue(F.dati(listOf(orologio, telefono)).serie(Metrica.PASSI)[g]!!.valore!! < 16000)
    }

    @Test fun valoriImpossibiliSonoSospetti() {
        val g = F.OGGI.minusDays(1)
        val d = F.dati(listOf(F.istantanea(Metrica.FREQUENZA_CARDIACA, F.t(g, 10), 60.0), F.istantanea(Metrica.FREQUENZA_CARDIACA, F.t(g, 11), 400.0)))
        val v = d.serie(Metrica.FREQUENZA_CARDIACA)[g]!!
        assertEquals(60.0, v.valore!!, 1e-9)
        assertEquals(1, v.sospetti)
    }

    @Test fun chiaveDiDeduplicazioneStabile() {
        val a = F.passi(F.OGGI, 100.0, id = "abc")
        assertEquals(a.chiave(), a.copy(valore = 200.0).chiave()) // stesso id = stessa misura aggiornata
        val senzaId = F.passi(F.OGGI, 100.0)
        assertEquals(senzaId.chiave(), senzaId.copy().chiave())
        assertFalse(senzaId.chiave() == senzaId.copy(valore = 101.0).chiave())
    }
}
