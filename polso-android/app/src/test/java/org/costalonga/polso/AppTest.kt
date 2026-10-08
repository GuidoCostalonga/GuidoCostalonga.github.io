package org.costalonga.polso

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.ChangesResponse
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.costalonga.polso.dati.Archivio
import org.costalonga.polso.dati.ArchivioImpostazioni
import org.costalonga.polso.dati.PolsoDb
import org.costalonga.polso.esporta.GestoreBackup
import org.costalonga.polso.esporta.RapportoPdf
import org.costalonga.polso.fonti.ClientHc
import org.costalonga.polso.fonti.MappaHc
import org.costalonga.polso.fonti.SincronizzatoreHc
import org.costalonga.polso.ia.IaOnline
import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Demo
import org.costalonga.polso.motore.FaseSonno
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Preferenze
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.motore.backup.PassphraseErrata
import org.costalonga.polso.motore.ia.Assistente
import org.costalonga.polso.motore.ia.CostruttoreFatti
import org.costalonga.polso.motore.ia.IaNonDisponibile
import org.costalonga.polso.motore.ia.MotoreTesto
import org.costalonga.polso.motore.importa.RisultatoImportazione
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.reflect.KClass

private val OFF = ZoneOffset.ofHours(2)
private fun md(id: String) = Metadata.unknownRecordingMethodWithId(id, Device(Device.TYPE_WATCH, "HONOR", "Choice Watch"))
private fun passi(id: String, ini: String, n: Long) = StepsRecord(Instant.parse(ini), OFF, Instant.parse(ini).plusSeconds(3600), OFF, n, md(id))

/** Health Connect finto: record e modifiche preparati dalla prova. */
class ClientFinto(var permessi: Set<String>) : ClientHc {
    val archivio = mutableMapOf<KClass<out Record>, MutableList<Record>>()
    val modifiche = ArrayDeque<ChangesResponse>()
    var letture = 0
    override suspend fun permessiConcessi() = permessi
    override suspend fun funzioneDisponibile(funzione: Int) = true
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> leggi(tipo: KClass<T>, da: Instant, a: Instant, pagina: String?): Pair<List<T>, String?> {
        if (HealthPermission.getReadPermission(tipo) !in permessi) throw SecurityException("negato")
        letture++
        return (archivio[tipo].orEmpty() as List<T>) to null
    }
    override suspend fun token(tipi: Set<KClass<out Record>>) = "t0"
    override suspend fun cambiamenti(token: String): ChangesResponse = modifiche.removeFirstOrNull() ?: ChangesResponse(emptyList(), token, false, false)
    override suspend fun aggregatoAllenamento(a: Allenamento, permessi: Set<String>) = a.copy(distanzaM = a.distanzaM ?: 5000.0)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PolsoApp::class)
class ArchivioEFontiTest {
    private lateinit var ctx: Context
    private lateinit var db: PolsoDb
    private lateinit var archivio: Archivio
    private lateinit var imp: ArchivioImpostazioni

    @Before fun prepara() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, PolsoDb::class.java).allowMainThreadQueries().build()
        archivio = Archivio(db)
        imp = ArchivioImpostazioni(ctx)
        runBlocking { imp.modifica { org.costalonga.polso.dati.Impostazioni() } }
    }

    @After fun chiudi() { db.close() }

    @Test fun mappaturaHealthConnect() {
        val hr = HeartRateRecord(Instant.parse("2026-10-01T06:00:00Z"), OFF, Instant.parse("2026-10-01T06:02:00Z"), OFF,
            listOf(HeartRateRecord.Sample(Instant.parse("2026-10-01T06:00:00Z"), 60), HeartRateRecord.Sample(Instant.parse("2026-10-01T06:01:00Z"), 62)), md("hr1"))
        val sonno = SleepSessionRecord(Instant.parse("2026-09-30T21:30:00Z"), OFF, Instant.parse("2026-10-01T05:00:00Z"), OFF, md("s1"), null, null,
            listOf(SleepSessionRecord.Stage(Instant.parse("2026-09-30T21:30:00Z"), Instant.parse("2026-10-01T01:00:00Z"), SleepSessionRecord.STAGE_TYPE_LIGHT),
                SleepSessionRecord.Stage(Instant.parse("2026-10-01T01:00:00Z"), Instant.parse("2026-10-01T05:00:00Z"), SleepSessionRecord.STAGE_TYPE_DEEP)))
        val corsa = ExerciseSessionRecord(Instant.parse("2026-10-01T16:00:00Z"), OFF, Instant.parse("2026-10-01T16:40:00Z"), OFF, md("e1"), ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Corsa", null, emptyList(), emptyList(), null, null)
        val r = MappaHc.converti(listOf(passi("p1", "2026-10-01T08:00:00Z", 1500), hr, sonno, corsa))
        assertEquals(3, r.misure.size)
        assertEquals("hr1#" + Instant.parse("2026-10-01T06:01:00Z").toEpochMilli(), r.misure.last().idEsterno)
        assertEquals(7200, r.misure.first().scartoSec)
        assertEquals(listOf(FaseSonno.LEGGERO.codice, FaseSonno.PROFONDO.codice), r.sonni.single().fasi.map { it.fase })
        assertEquals("corsa", r.allenamenti.single().sport)
        assertEquals(null, r.allenamenti.single().distanzaM) // nessun valore inventato
    }

    @Test fun importazioneRipetutaAggiornaSenzaDoppioni() = runTest {
        val r = MappaHc.converti(listOf(passi("p1", "2026-10-01T08:00:00Z", 1500), passi("p2", "2026-10-01T09:00:00Z", 900)))
        assertEquals(2, archivio.scrivi(r, 1).nuovi)
        val ancora = archivio.scrivi(r, 2)
        assertEquals(0, ancora.nuovi); assertEquals(2, ancora.aggiornati)
        // Record modificato alla fonte: stesso id, nuovo valore → aggiornato
        archivio.scrivi(MappaHc.converti(listOf(passi("p1", "2026-10-01T08:00:00Z", 1600))), 3)
        val m = archivio.misure(0, Long.MAX_VALUE)
        assertEquals(2, m.size)
        assertEquals(2500.0, m.sumOf { it.valore }, 1e-9)
    }

    @Test fun cancellazioneDallaFonteRimuoveAncheICampioni() = runTest {
        val hr = HeartRateRecord(Instant.parse("2026-10-01T06:00:00Z"), OFF, Instant.parse("2026-10-01T06:02:00Z"), OFF,
            listOf(HeartRateRecord.Sample(Instant.parse("2026-10-01T06:00:00Z"), 60), HeartRateRecord.Sample(Instant.parse("2026-10-01T06:01:00Z"), 62)), md("hr9"))
        archivio.scrivi(MappaHc.converti(listOf(hr, passi("p1", "2026-10-01T08:00:00Z", 10))), 1)
        assertEquals(2, archivio.elimina(MappaHc.FONTE, "hr9"))
        assertEquals(1, archivio.misure(0, Long.MAX_VALUE).size)
    }

    @Test fun sincronizzazioneInizialeEModifiche() = runTest {
        val tutti = MappaHc.TIPI.map { HealthPermission.getReadPermission(it.first) }.toSet() + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        val f = ClientFinto(tutti)
        f.archivio[StepsRecord::class] = mutableListOf(passi("a", "2026-10-01T08:00:00Z", 1000), passi("b", "2026-10-01T09:00:00Z", 2000))
        val s = SincronizzatoreHc(f, archivio, imp)
        val e1 = s.sincronizza(Instant.parse("2026-10-05T10:00:00Z"))
        assertTrue(e1.errori.toString(), e1.riuscita)
        assertEquals(2, e1.nuovi)
        assertEquals(SincronizzatoreHc.INIZIO_STORICO, e1.da) // con il permesso dello storico si legge tutto
        // Seconda volta: solo modifiche (un nuovo record e una cancellazione)
        f.modifiche += ChangesResponse(listOf(UpsertionChange(passi("c", "2026-10-02T08:00:00Z", 500)), DeletionChange("a")), "t1", false, false)
        val e2 = s.sincronizza(Instant.parse("2026-10-05T12:00:00Z"))
        assertEquals(1, e2.nuovi); assertEquals(1, e2.eliminati)
        assertEquals(setOf(2000.0, 500.0), archivio.misure(0, Long.MAX_VALUE).map { it.valore }.toSet())
        assertEquals("riuscita", db.dao().ultimaRiuscita("health_connect")!!.esito)
    }

    @Test fun senzaStoricoSoloTrentaGiorni() = runTest {
        val f = ClientFinto(setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        val e = SincronizzatoreHc(f, archivio, imp).sincronizza(Instant.parse("2026-10-05T10:00:00Z"))
        assertEquals(Instant.parse("2026-09-05T10:00:00Z"), e.da)
    }

    @Test fun permessiRevocati() = runTest {
        val f = ClientFinto(emptySet())
        val e = SincronizzatoreHc(f, archivio, imp).sincronizza()
        assertFalse(e.riuscita)
        assertTrue(e.errori.first().contains("permess"))
        assertEquals("non riuscita", db.dao().registro().first().first().esito)
    }

    @Test fun permessoRevocatoDuranteLaLettura() = runTest {
        val f = object : ClientHc by ClientFinto(setOf(HealthPermission.getReadPermission(StepsRecord::class))) {
            override suspend fun <T : Record> leggi(tipo: KClass<T>, da: Instant, a: Instant, pagina: String?): Pair<List<T>, String?> = throw SecurityException("revocato")
        }
        val e = SincronizzatoreHc(f, archivio, imp).sincronizza()
        assertTrue(e.errori.single().contains("Passi: permesso revocato"))
    }

    @Test fun tokenScadutoRileggeTrentaGiorni() = runTest {
        val f = ClientFinto(setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        val s = SincronizzatoreHc(f, archivio, imp)
        s.sincronizza(Instant.parse("2026-10-05T10:00:00Z"))
        f.modifiche += ChangesResponse(emptyList(), "", false, true)
        val prima = f.letture
        val e = s.sincronizza(Instant.parse("2026-11-20T10:00:00Z"))
        assertEquals(prima + 1, f.letture)
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), e.da)
    }

    @Test fun backupERipristinoVerificato() = runTest {
        val a = Demo.genera(LocalDate.of(2026, 10, 5), 20)
        archivio.scrivi(RisultatoImportazione(a.misure, a.sonni, a.allenamenti), 1)
        a.diario.forEach { archivio.salvaVoce(it) }
        val file = File(ctx.cacheDir, "prova.polso")
        val g = GestoreBackup(ctx, archivio, imp, "test")
        val pass = "una-frase-segreta-lunga".toCharArray()
        val n = g.salva(Uri.fromFile(file), pass.copyOf())
        assertEquals(a.diario.size, n["diario"])
        db.dao().svuotaTutto()
        assertEquals(0, archivio.misure(0, Long.MAX_VALUE).size)
        val r = g.ripristina(Uri.fromFile(file), pass.copyOf())
        assertTrue(r.dettaglio, r.verificato)
        assertEquals(a.misure.distinctBy { it.chiave() }.size, archivio.misure(Long.MIN_VALUE, Long.MAX_VALUE).size)
        // passphrase errata: nessuna modifica all'archivio
        try { g.ripristina(Uri.fromFile(file), "sbagliata-ma-lunga".toCharArray()); error("doveva fallire") } catch (_: PassphraseErrata) {}
        assertEquals(a.misure.distinctBy { it.chiave() }.size, archivio.misure(Long.MIN_VALUE, Long.MAX_VALUE).size)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun rapportoPdf() {
        val a = Demo.genera(LocalDate.of(2026, 10, 5), 40)
        val d = Dati(a.misure, a.sonni, a.allenamenti, a.diario, Preferenze(annoNascita = 1976))
        // PdfDocument non è simulato da Robolectric: si disegna su bitmap A4 e si
        // verifica che il rapporto componga più pagine con contenuto.
        val disegnate = mutableListOf<android.graphics.Bitmap>()
        val fogli = object : org.costalonga.polso.esporta.Fogli {
            override fun nuova(numero: Int, larghezza: Int, altezza: Int) = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(larghezza, altezza, android.graphics.Bitmap.Config.ARGB_8888).also { disegnate += it })
            override fun chiudi() {}
            override fun scrivi(out: java.io.OutputStream) { out.write(byteArrayOf(1)) }
        }
        val r = RapportoPdf(d, Periodo.di(TipoPeriodo.SETTIMANA, LocalDate.of(2026, 9, 28)), LocalDate.of(2026, 10, 5), Sezione.entries.toSet(), demo = true, fogli = fogli)
        r.scrivi(ByteArrayOutputStream())
        assertTrue("pagine: ${r.pagine}", r.pagine >= 5)
        val b = disegnate.first()
        val pixelScritti = (0 until b.height step 4).sumOf { yy -> (0 until b.width step 4).count { xx -> b.getPixel(xx, yy) != 0 } }
        assertTrue(pixelScritti > 500)
    }

    @Test fun iaNonDisponibileEReteAssente() = runTest {
        val a = Demo.genera(LocalDate.of(2026, 10, 5), 30)
        val d = Dati(a.misure, a.sonni, a.allenamenti, a.diario)
        val f = CostruttoreFatti.perRiepilogo(d, TipoPeriodo.SETTIMANA, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5))
        // Senza rete: indirizzo irraggiungibile → ripiego sul testo calcolato, con nota.
        val online = IaOnline("http://127.0.0.1:9/v1/chat/completions", "modello", "chiave")
        val r = Assistente.rispondi(f, "riepilogo", online) { "calcolato" }
        assertEquals("calcolato", r.testo); assertFalse(r.daIa); assertTrue(r.nota!!.contains("Rete non disponibile"))
        // Modello che inventa numeri → scartato
        val bugiardo = object : MotoreTesto { override val nome = "finto"; override suspend fun genera(sistema: String, richiesta: String) = "Hai fatto 987.654 passi." }
        assertEquals("calcolato", Assistente.rispondi(f, "x", bugiardo) { "calcolato" }.testo)
        // Modello assente → testo calcolato, senza nota d'errore
        val assente = object : MotoreTesto { override val nome = "x"; override suspend fun genera(sistema: String, richiesta: String): String = throw IaNonDisponibile("Modello non scaricato.") }
        assertTrue(Assistente.rispondi(f, "x", assente) { "calcolato" }.nota!!.startsWith("Modello non scaricato"))
        assertEquals(null, Assistente.rispondi(f, "x", null) { "calcolato" }.nota)
    }

    @Test fun demoSeparataDaiDatiReali() {
        assertTrue(PolsoDb.NOME != PolsoDb.NOME_DEMO)
        assertTrue(Demo.genera(LocalDate.of(2026, 10, 5), 3).misure.all { it.fonte == Demo.FONTE })
        assertTrue(Metrica.entries.isNotEmpty())
    }
}
