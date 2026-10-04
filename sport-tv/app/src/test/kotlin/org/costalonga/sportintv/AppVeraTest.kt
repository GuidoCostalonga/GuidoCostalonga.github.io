package org.costalonga.sportintv

import android.Manifest
import android.app.AlarmManager
import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.costalonga.sportintv.raccolta.JsonSportTv
import org.costalonga.sportintv.raccolta.Pacchetto
import org.costalonga.sportintv.dati.Origine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * L'app vera, eseguita sulla JVM con Robolectric e alimentata dal pacchetto
 * reale raccolto il 4 ottobre 2026. Nessun accesso alla rete: l'origine dei
 * dati è impostata su "solo servizio" con un indirizzo non raggiungibile, così
 * si verifica anche la consultazione offline.
 *
 * Le schermate fotografate finiscono in build/schermate/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class AppVeraTest {

    @get:Rule
    val regola = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private val app get() = ApplicationProvider.getApplicationContext<SportApp>()

    companion object {
        private val pacchetto: Pacchetto by lazy {
            val testo = AppVeraTest::class.java.getResource("/pacchetto-reale-2026-10-04.json")!!.readText()
            JsonSportTv.decodeFromString(Pacchetto.serializer(), testo)
        }

    }

    @Before
    fun prepara() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Ogni prova parte da un telefono senza dati né promemoria.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.db.clearAllTables() }
        // Nessuna rete nelle prove: solo servizio, a un indirizzo che non esiste.
        app.impostazioni.origine(Origine.SERVIZIO)
        app.impostazioni.urlServizio("https://servizio-non-raggiungibile.invalid/eventi.json")
    }

    @org.junit.After
    fun chiudi() {
        scenario?.close()
        // Robolectric riusa i file fra una prova e l'altra: si chiude il database.
        app.db.close()
    }

    /** Dati reali già sul telefono, poi si apre l'app. */
    private fun preparaDati() {
        runBlocking { app.archivio.importa(pacchetto, "servizio", Instant.now()) }
        avvia()
    }

    private fun avvia() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun fotografa(nome: String) {
        regola.waitForIdle()
        // Si disegna la finestra in un'immagine (captureToImage non è supportato da Robolectric).
        var bmp: Bitmap? = null
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        val cartella = File("build/schermate").apply { mkdirs() }
        FileOutputStream(File(cartella, "$nome.png")).use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun attendiTesto(testo: String) {
        try {
            regola.waitUntil(15_000) { regola.onAllNodesWithText(testo, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            System.err.println("EVENTI NEL DB AL FALLIMENTO: " + runBlocking { app.archivio.eventiSalvati().size } + " stato " + runBlocking { app.impostazioni.statoAttuale() } + " pref " + runBlocking { app.impostazioni.attuali() })
            System.err.println(regola.onRoot(useUnmergedTree = true).printToString())
            throw AssertionError("Testo non trovato: $testo", e)
        }
    }

    @Test
    fun programma_dettaglio_promemoria_offline() {
        preparaDati()
        attendiTesto("Oggi ·")
        fotografa("01-programma")

        // Ricerca: la partita di Serie A dell'11 ottobre trasmessa da DAZN.
        regola.onNodeWithText("Cerca").performTextInput("Sassuolo")
        attendiTesto("Sassuolo - Milan")
        fotografa("02-ricerca")
        regola.onAllNodesWithText("Sassuolo - Milan", substring = true).onFirst().performClick()
        attendiTesto("Dove vederlo in Italia")
        fotografa("03-dettaglio")

        // Promemoria: allarme pianificato senza permesso per sveglie esatte.
        regola.onNode(hasText("Promemoria", substring = true) and hasClickAction()).performClick()
        attendiTesto("Promemoria attivo")
        val allarmi = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, allarmi.size)
        val promemoria = runBlocking { app.db.preferiti().elencoPromemoria() }
        assertEquals(1, promemoria.size)
        // 15 minuti prima dell'inizio (anticipo predefinito).
        assertEquals(promemoria.single().inizio - 15 * 60_000, promemoria.single().allarme)
        assertEquals(promemoria.single().allarme, allarmi.single().triggerAtMs)

        // Riattivarlo non crea duplicati; disattivarlo cancella l'allarme.
        regola.onNode(hasText("Promemoria attivo", substring = true) and hasClickAction()).performClick()
        regola.waitUntil(5_000) { runBlocking { app.db.preferiti().elencoPromemoria().isEmpty() } }
        // La cancellazione dell'allarme è verificata a parte in PianificatoreTest.
    }

    @Test
    fun daConfermare_sezioneSeparata() {
        preparaDati()
        attendiTesto("Oggi ·")
        regola.onNodeWithText("Da confermare").performClick()
        attendiTesto("Che cosa sono questi eventi")
        fotografa("04-da-confermare")
    }

    @Test
    fun impostazioniEInformazioni() {
        preparaDati()
        attendiTesto("Oggi ·")
        regola.onNodeWithText("Impostazioni").performClick()
        attendiTesto("Anticipo dell'avviso")
        fotografa("05-impostazioni")
        regola.onNode(hasScrollAction()).performScrollToNode(hasText("Informazioni, fonti e licenze"))
        regola.onNodeWithText("Informazioni, fonti e licenze").performClick()
        attendiTesto("Realizzato da Guido Costalonga. Tutti i diritti riservati.")
        fotografa("06-informazioni")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun schermoPiccoloECaratteriDoppi() {
        RuntimeEnvironment.setFontScale(2.0f)
        preparaDati()
        attendiTesto("Oggi ·")
        fotografa("07-piccolo-caratteri-200")
        regola.onAllNodesWithText("Sport in TV").onFirst().performClick()
    }

    @Test
    @Config(qualifiers = "night")
    fun modalitaScura() {
        preparaDati()
        attendiTesto("Oggi ·")
        fotografa("08-scuro")
    }

}
