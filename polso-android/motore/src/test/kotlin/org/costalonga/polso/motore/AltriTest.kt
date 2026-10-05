package org.costalonga.polso.motore

import org.costalonga.polso.motore.backup.Backup
import org.costalonga.polso.motore.backup.ContenutoBackup
import org.costalonga.polso.motore.backup.FormatoNonValido
import org.costalonga.polso.motore.backup.PassphraseErrata
import org.costalonga.polso.motore.esporta.Contenuto
import org.costalonga.polso.motore.esporta.Esportazione
import org.costalonga.polso.motore.esporta.Xlsx
import org.costalonga.polso.motore.ia.CostruttoreFatti
import org.costalonga.polso.motore.ia.Interprete
import org.costalonga.polso.motore.ia.TestiDeterministici
import org.costalonga.polso.motore.ia.Verifica
import org.costalonga.polso.motore.importa.Csv
import org.costalonga.polso.motore.importa.Cumulativi
import org.costalonga.polso.motore.importa.ImportaCsvGenerico
import org.costalonga.polso.motore.importa.ImportaPolsoCsv
import org.costalonga.polso.motore.importa.Mappatura
import org.costalonga.polso.motore.importa.TipoValore
import org.costalonga.polso.motore.importa.Tracce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ImportazioneTest {
    private val csv = """
metrica;inizio;fine;valore;unita;tipo_valore;origine;id
passi;2026-10-01 08:00;2026-10-01 09:00;1200;;;honor;a1
distanza;2026-10-01 08:00;2026-10-01 09:00;0,9;km;;honor;a2
fc;2026-10-01T08:30:00+02:00;;72;bpm;;honor;a3
passi;2026-10-02 10:00;;500;;cumulativo;telefono;
passi;2026-10-02 12:00;;1500;;cumulativo;telefono;
passi;2026-10-02 18:00;;400;;cumulativo;telefono;
sonno;2026-10-01 23:30;2026-10-02 01:00;leggero;;;;
sonno;2026-10-02 01:00;2026-10-02 02:00;profondo;;;;
sonno;2026-10-02 02:00;2026-10-02 06:30;rem;;;;
allenamento;2026-10-01 18:00;2026-10-01 18:40;6500;;;;
pippo;2026-10-01 08:00;;3;;;;
passi;31/02/2026 10:00;;3;;;;
""".trimIndent()

    @Test fun formatoPolso() {
        val r = ImportaPolsoCsv.importa(csv, "prova.csv")
        assertEquals(2, r.errori.size)
        assertTrue(r.errori[0].contains("metrica sconosciuta"))
        assertEquals(900.0, r.misure.first { it.metrica == "distanza" }.valore, 1e-9) // km → m
        assertEquals(1, r.sonni.size)
        assertEquals(3, r.sonni[0].fasi.size)
        assertEquals(1, r.allenamenti.size)
        assertEquals(6500.0, r.allenamenti[0].distanzaM!!, 1e-9)
    }

    @Test fun cumulativiInIncrementiConAzzeramento() {
        val r = ImportaPolsoCsv.importa(csv, "prova.csv")
        val tel = r.misure.filter { it.origine == "telefono" }.sortedBy { it.inizio }
        // 500 da mezzanotte, +1000, poi azzeramento: 400. Mai la somma delle letture (2400).
        assertEquals(listOf(500.0, 1000.0, 400.0), tel.map { it.valore })
        assertEquals(1900.0, tel.sumOf { it.valore }, 1e-9)
    }

    @Test fun importazioneRipetutaStesseChiavi() {
        val a = ImportaPolsoCsv.importa(csv, "prova.csv")
        val b = ImportaPolsoCsv.importa(csv, "prova.csv")
        assertEquals(a.misure.map { it.chiave() }.toSet(), b.misure.map { it.chiave() }.toSet())
        assertEquals(a.misure.size, a.misure.map { it.chiave() }.toSet().size)
    }

    @Test fun oraLegaleNelFile() {
        val r = ImportaPolsoCsv.importa("metrica;inizio;valore\nfc;2026-03-29 03:30;60\nfc;2026-10-25 02:30;61", "x.csv")
        val g = r.misure.map { Instant.ofEpochMilli(it.inizio).atOffset(java.time.ZoneOffset.UTC).hour }
        assertEquals(listOf(1, 0), g) // 03:30 CEST = 01:30 UTC; 02:30 ambiguo → prima occorrenza (CEST) = 00:30 UTC
    }

    @Test fun csvGenericoConMappatura() {
        val testo = "Data,Battiti\n01/10/2026 08:00,61\n01/10/2026 09:00,abc\n"
        val r = ImportaCsvGenerico.importa(testo, "altro.csv", Mappatura(Metrica.FREQUENZA_CARDIACA, 0, null, 1, "bpm", "dd/MM/yyyy HH:mm"))
        assertEquals(1, r.misure.size)
        assertEquals(1, r.errori.size)
    }

    @Test fun csvConVirgoletteEAcapo() {
        val r = Csv.leggi("a;b\n\"x;y\";\"riga\nnuova\"\n")
        assertEquals(listOf("x;y", "riga\nnuova"), r[1])
        assertEquals("\"a\"\"b\"", Csv.campo("a\"b"))
    }

    @Test fun gpxConFrequenza() {
        val gpx = """<?xml version="1.0"?>
<gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
<trk><name>Giro</name><type>running</type><trkseg>
<trkpt lat="46.0" lon="12.6"><ele>100</ele><time>2026-10-01T16:00:00Z</time><extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>120</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
<trkpt lat="46.009" lon="12.6"><ele>110</ele><time>2026-10-01T16:05:00Z</time><extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>140</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
</trkseg></trk></gpx>"""
        val r = Tracce.gpx(gpx.byteInputStream(), "giro.gpx")
        assertEquals(1, r.allenamenti.size)
        assertEquals("corsa", r.allenamenti[0].sport)
        assertEquals(1000.8, r.allenamenti[0].distanzaM!!, 1.0) // 0,009° di latitudine ≈ 1 km
        assertEquals(10.0, r.allenamenti[0].dislivelloM!!, 1e-9)
        assertEquals(2, r.misure.size)
    }

    @Test fun gpxConEntitaEsterneRifiutato() {
        val r = Tracce.gpx("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><gpx>&e;</gpx>""".byteInputStream(), "x.gpx")
        assertTrue(r.errori.first().contains("non leggibile"))
    }

    @Test fun tcx() {
        val tcx = """<?xml version="1.0"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"><Activities><Activity Sport="Biking"><Id>2026-10-01T07:00:00Z</Id>
<Lap StartTime="2026-10-01T07:00:00Z"><TotalTimeSeconds>3600</TotalTimeSeconds><DistanceMeters>25000</DistanceMeters><Calories>600</Calories>
<Track><Trackpoint><Time>2026-10-01T07:00:00Z</Time><HeartRateBpm><Value>110</Value></HeartRateBpm></Trackpoint></Track></Lap></Activity></Activities></TrainingCenterDatabase>"""
        val r = Tracce.tcx(tcx.byteInputStream(), "bici.tcx")
        val a = r.allenamenti.single()
        assertEquals("ciclismo", a.sport)
        assertEquals(25000.0, a.distanzaM!!, 1e-9)
        assertEquals(3_600_000L, a.durataMs)
        assertEquals(600.0, a.calorieKcal!!, 1e-9)
    }

    @Test fun cumulativoSenzaAzzeramento() {
        val l = listOf(100.0, 250.0, 260.0).mapIndexed { i, v -> Misura("passi", F.t(F.OGGI, 10 + i), F.t(F.OGGI, 10 + i), v, "passi", null, "file", "x") }
        val inc = Cumulativi.aIncrementi(l, Tempo.ROMA, azzeramentoGiornaliero = false)
        assertEquals(listOf(150.0, 10.0), inc.map { it.valore })
    }
}

class EsportazioneTest {
    private val d = F.demo(40)
    private val p = F.ultimi(14)

    @Test fun excelValidoConFogliDistinti() {
        val out = ByteArrayOutputStream()
        Esportazione(d, p, F.OGGI).excel(out)
        val nomi = mutableListOf<String>()
        var workbook = ""
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            generateSequence { z.nextEntry }.forEach { e -> nomi += e.name; if (e.name == "xl/workbook.xml") workbook = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue("[Content_Types].xml" in nomi)
        assertEquals(8, nomi.count { it.startsWith("xl/worksheets/sheet") })
        listOf("Misure originali", "Giornaliero calcolato", "Sonno", "Allenamenti", "Diario", "Statistiche", "Definizioni").forEach { assertTrue(it, workbook.contains("name=\"$it\"")) }
    }

    @Test fun xlsxColonneEscape() {
        val x = Xlsx()
        assertEquals("A", x.colonna(0)); assertEquals("Z", x.colonna(25)); assertEquals("AA", x.colonna(26)); assertEquals("AZ", x.colonna(51))
        x.foglio("Prova").apply { intestazione("a"); riga("<&>\"", 1.5, null) }
        val out = ByteArrayOutputStream(); x.scrivi(out)
        assertTrue(out.size() > 500)
    }

    @Test fun csvSelezionatiConProvenienza() {
        val f = Esportazione(d, p, F.OGGI, setOf(Contenuto.MISURE, Contenuto.GIORNALIERO)).csv()
        assertEquals(setOf("misure.csv", "giornaliero.csv"), f.keys)
        val misure = f.getValue("misure.csv")
        assertTrue(misure.startsWith("﻿metrica;"))
        assertTrue(misure.contains("dato dimostrativo sintetico"))
        assertTrue(f.getValue("giornaliero.csv").contains("valore calcolato dall'app"))
    }

    @Test fun giorniSenzaDatiAssentiNonZero() {
        val dd = F.dati(listOf(F.passi(F.OGGI, 5000.0)))
        val righe = Esportazione(dd, F.ultimi(7), F.OGGI).righeGiornaliere()
        assertEquals(2, righe.size) // intestazione + un solo giorno
    }
}

class BackupTest {
    private val pass = "cavallo-batteria-graffetta".toCharArray()
    private fun contenuto(): ContenutoBackup {
        val a = Demo.genera(F.OGGI, 10)
        return ContenutoBackup(versioneSchema = 1, versioneApp = "1.0", creatoIl = 0, misure = a.misure, sonni = a.sonni, allenamenti = a.allenamenti, diario = a.diario, impostazioni = mapOf("obiettivo_passi" to "9000"))
    }

    @Test fun andataERitorno() {
        val c = contenuto()
        val file = Backup.cifra(c, pass, iterazioni = 2000)
        val r = Backup.decifra(file, pass.copyOf())
        assertEquals(c.conteggi(), r.conteggi())
        assertEquals(Backup.impronta(c), Backup.impronta(r))
        assertEquals("9000", r.impostazioni["obiettivo_passi"])
        assertFalse(String(file, Charsets.ISO_8859_1).contains("dati dimostrativi")) // il contenuto non è in chiaro
    }

    @Test fun passphraseErrata() {
        val file = Backup.cifra(contenuto(), pass, iterazioni = 2000)
        assertThrows(PassphraseErrata::class.java) { Backup.decifra(file, "un'altra-frase-lunga".toCharArray()) }
    }

    @Test fun intestazioneAlterata() {
        val file = Backup.cifra(contenuto(), pass, iterazioni = 2000)
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        assertThrows(PassphraseErrata::class.java) { Backup.decifra(file, pass) }
        assertThrows(FormatoNonValido::class.java) { Backup.decifra("ciao".toByteArray(), pass) }
    }

    @Test fun passphraseDeboleRifiutata() {
        assertNotNull(Backup.passphraseValida("corta".toCharArray()))
        assertNotNull(Backup.passphraseValida("aaaaaaaaaaaa".toCharArray()))
        assertNull(Backup.passphraseValida(pass))
        assertThrows(IllegalArgumentException::class.java) { Backup.cifra(contenuto(), "corta".toCharArray()) }
    }

    @Test fun ogniBackupHaSaleDiverso() {
        val a = Backup.cifra(contenuto(), pass, 2000, SecureRandom())
        val b = Backup.cifra(contenuto(), pass, 2000, SecureRandom())
        assertFalse(a.contentEquals(b))
    }
}

class RegoleTest {
    private fun riposo(valori: List<Double?>): Dati {
        val n = valori.size
        val m = valori.mapIndexedNotNull { i, v -> v?.let { F.istantanea(Metrica.FC_RIPOSO, F.t(F.OGGI.minusDays((n - i).toLong()), 12), it) } }
        return F.dati(m)
    }

    @Test fun fcRiposoPersistenteSegnalata() {
        val d = riposo(List(28) { 56.0 + (it % 3) } + listOf(66.0, 67.0, 68.0))
        val s = Regole.valuta(d, F.OGGI, ConfigNotifiche())
        val seg = s.single { it.regola == "fc_riposo" }
        assertTrue(seg.testo.contains("66 · 67 · 68"))
        assertTrue(seg.motivo.contains("3 giorni consecutivi"))
    }

    @Test fun unGiornoMancanteInterrompeLaPersistenza() {
        val d = riposo(List(28) { 56.0 } + listOf(66.0, null, 68.0))
        assertTrue(Regole.valuta(d, F.OGGI, ConfigNotifiche()).none { it.regola == "fc_riposo" })
    }

    @Test fun storicoInsufficienteNessunaSegnalazione() {
        val d = riposo(List(5) { 56.0 } + listOf(80.0, 80.0, 80.0))
        assertTrue(Regole.valuta(d, F.OGGI, ConfigNotifiche()).none { it.regola == "fc_riposo" })
    }

    @Test fun regolaDisattivata() {
        val d = riposo(List(28) { 56.0 } + listOf(66.0, 67.0, 68.0))
        assertTrue(Regole.valuta(d, F.OGGI, ConfigNotifiche(regoleAttive = emptySet())).isEmpty())
        assertTrue(Regole.valuta(d, F.OGGI, ConfigNotifiche(attive = false)).isEmpty())
    }

    @Test fun sonnoRidotto() {
        val notti = (0 until 31).map { i -> F.notte(F.OGGI.minusDays(30L - i), 23, 0, if (i >= 28) 300 else 450 + (i % 4) * 5) }
        assertNotNull(Regole.valuta(F.dati(sonni = notti), F.OGGI, ConfigNotifiche()).firstOrNull { it.regola == "sonno_ridotto" })
    }

    @Test fun oreSilenzioseEIntervalloMinimo() {
        val seg = listOf(Segnalazione("fc_riposo", "t", "x", "d", "p", "m"))
        val cfg = ConfigNotifiche()
        val notte = LocalDate.of(2026, 10, 5).atTime(23, 0).atZone(Tempo.ROMA).toInstant()
        assertNull(Regole.daInviare(seg, emptyMap(), notte, cfg))
        val giorno = LocalDate.of(2026, 10, 5).atTime(10, 0).atZone(Tempo.ROMA).toInstant()
        assertNotNull(Regole.daInviare(seg, emptyMap(), giorno, cfg))
        assertNull(Regole.daInviare(seg, mapOf("altra" to giorno.toEpochMilli() - 3_600_000), giorno, cfg))
        assertNull(Regole.daInviare(seg, mapOf("fc_riposo" to giorno.toEpochMilli() - 48 * 3_600_000L), giorno, cfg))
        assertNotNull(Regole.daInviare(seg, mapOf("fc_riposo" to giorno.toEpochMilli() - 80 * 3_600_000L), giorno, cfg))
    }

    @Test fun datiAssenti() {
        val d = F.dati(listOf(F.passi(F.OGGI.minusDays(5), 100.0)))
        assertNotNull(Regole.valuta(d, F.OGGI, ConfigNotifiche()).firstOrNull { it.regola == "dati_assenti" })
    }
}

class IaTest {
    private val d = F.demo(60)

    @Test fun fattiSoloDaCalcoliDeterministici() {
        val f = CostruttoreFatti.perRiepilogo(d, TipoPeriodo.SETTIMANA, F.OGGI.minusWeeks(1), F.OGGI)
        assertTrue(f.testo.contains("PERIODO:"))
        assertTrue(f.analisiUsate.contains("att.passi"))
        assertTrue(f.numeri.isNotEmpty())
    }

    @Test fun verificaNumeriInventati() {
        val f = CostruttoreFatti.perRiepilogo(d, TipoPeriodo.SETTIMANA, F.OGGI.minusWeeks(1), F.OGGI)
        val vero = f.testo.lineSequence().first { it.startsWith("- Media giornaliera") }.substringAfter(": ")
        assertTrue(Verifica.esamina("La media è stata $vero, in 7 giorni.", f).valida)
        val esame = Verifica.esamina("Hai fatto 123.457 passi e dormito 9,87 ore.", f)
        assertFalse(esame.valida)
        assertTrue(esame.numeriNonTrovati.contains(123457.0))
    }

    @Test fun verificaParoleVietate() {
        val f = CostruttoreFatti.perRiepilogo(d, TipoPeriodo.GIORNO, F.OGGI, F.OGGI)
        assertFalse(Verifica.esamina("Potrebbe essere una fibrillazione.", f).valida)
    }

    @Test fun numeriItaliani() {
        assertEquals(listOf(8432.0, 7.5, 62.0), Verifica.numeri("8.432 passi, 7,5 ore, 62 bpm"))
    }

    @Test fun interpretazioneDomande() {
        val q = Interprete.interpreta("Quanti passi ho fatto la settimana scorsa?", F.OGGI)
        assertTrue(Metrica.PASSI in q.metriche)
        assertEquals(LocalDate.of(2026, 9, 28), q.periodo.da)
        assertEquals(14, Interprete.interpreta("Come ho dormito negli ultimi 14 giorni?", F.OGGI).periodo.lunghezza)
        assertTrue(Interprete.interpreta("Come ho dormito negli ultimi 14 giorni?", F.OGGI).sonno)
        assertEquals(LocalDate.of(2026, 9, 1), Interprete.interpreta("battito a settembre", F.OGGI).periodo.da)
        assertEquals(LocalDate.of(2025, 12, 1), Interprete.interpreta("passi a dicembre", F.OGGI).periodo.da)
        assertEquals("record", Interprete.interpreta("qual è il mio record di passi?", F.OGGI).tipo)
    }

    @Test fun rispostaSenzaModelloSempreDisponibile() {
        val q = Interprete.interpreta("come ho dormito questa settimana", F.OGGI)
        val f = org.costalonga.polso.motore.ia.CostruttoreFatti.costruisci(d, q.periodo, F.OGGI, Interprete.analisi(q))
        val r = TestiDeterministici.risposta(q, f)
        assertTrue(r.contains("Durata del sonno"))
        assertTrue(Verifica.esamina(r, f).numeriNonTrovati.isEmpty())
    }
}

class FileDiProvaTest {
    /** Scrive file reali in build/prove, controllati poi da strumenti esterni (openpyxl). */
    @Test fun scriviFileDiProva() {
        val dir = java.io.File("build/prove").apply { mkdirs() }
        val d = F.demo(60)
        java.io.FileOutputStream(java.io.File(dir, "prova.xlsx")).use { Esportazione(d, F.ultimi(30), F.OGGI).excel(it) }
        Esportazione(d, F.ultimi(30), F.OGGI).csv().forEach { (n, t) -> java.io.File(dir, n).writeText(t) }
    }
}

class DocumentazioneTest {
    /** Genera CATALOGO.md dalle schede tecniche del codice (copiato nel deposito dal README). */
    @Test fun catalogo() {
        val sb = StringBuilder("# Catalogo delle elaborazioni\n\nGenerato automaticamente dalle schede tecniche del codice (`motore/.../analisi`). " +
            "Ogni elaborazione ha una prova automatica con il nome indicato. Totale: ${org.costalonga.polso.motore.analisi.Catalogo.tutte.size} elaborazioni, più le regole delle notifiche in fondo.\n")
        org.costalonga.polso.motore.analisi.Sezione.entries.forEach { s ->
            sb.append("\n## ${s.nome}\n")
            org.costalonga.polso.motore.analisi.Catalogo.perSezione(s).forEach { a ->
                with(a.def) {
                    sb.append("\n### $titolo (`$id`)\n\n| Voce | Contenuto |\n|---|---|\n")
                    listOf("Dati richiesti" to datiRichiesti, "Formula o metodo" to metodo, "Unità" to unita, "Minimo di osservazioni" to minimo,
                        "Dati mancanti" to mancanti, "Limiti interpretativi" to limiti, "Prova automatica" to "`$prova`",
                        "Se non disponibile" to "l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …»)").forEach { (k, v) ->
                        sb.append("| $k | ${v.replace("|", "\\|")} |\n")
                    }
                }
            }
        }
        sb.append("\n## Regole delle notifiche\n\n")
        Regole.tutte.forEach { sb.append("- **${it.nome}** (`${it.id}`): ${it.descrizione}\n") }
        sb.append("\nCriteri di «Cosa emerge dai tuoi dati»: ${org.costalonga.polso.motore.analisi.Evidenze.CRITERI}\n")
        java.io.File("build/prove").mkdirs()
        java.io.File("build/prove/CATALOGO.md").writeText(sb.toString())
    }
}
