package org.costalonga.polso

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.costalonga.polso.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Schermate in modalità dimostrativa, salvate in app/build/schermate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PolsoApp::class, qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchermateTest {
    @get:Rule(order = 0) val lavori = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
            // Le impostazioni restano in memoria fra una prova e l'altra: si riparte da zero.
            kotlinx.coroutines.runBlocking { ctx.contenitore.impostazioni.modifica { org.costalonga.polso.dati.Impostazioni() } }
        }
    }
    @get:Rule(order = 1) val regola = createAndroidComposeRule<MainActivity>()

    private fun salva(nome: String) {
        val vm = androidx.lifecycle.ViewModelProvider(regola.activity)[org.costalonga.polso.ui.AppViewModel::class.java]
        regola.waitUntil(120_000) { !vm.caricamento.value }
        regola.waitForIdle()
        val dir = File("build/schermate").apply { mkdirs() }
        val v = regola.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        regola.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(dir, "$nome.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun vai(titolo: String, attesa: String) {
        regola.onNodeWithContentDescription("Menu").performClick()
        regola.waitForIdle()
        regola.onAllNodesWithText(titolo).onFirst().performScrollTo().performClick()
        regola.waitUntil(60_000) { regola.onAllNodesWithText(attesa, substring = true).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
    }

    @Test fun schermate() {
        regola.waitUntil(60_000) { regola.onAllNodesWithText("Benvenuto in Polso").fetchSemanticsNodes().isNotEmpty() }
        salva("01-primo-avvio")
        regola.onNodeWithText("Prova con dati dimostrativi").performClick()
        regola.waitUntil(120_000) { regola.onAllNodesWithText("Oggi,", substring = true).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1500)
        salva("02-panoramica")
        vai("Sonno", "Durata del sonno"); salva("03-sonno")
        vai("Cuore", "Frequenza cardiaca giornaliera"); salva("04-cuore")
        vai("Relazioni", "Attività e sonno"); salva("05-relazioni")
        vai("Catalogo delle analisi", "analisi disponibili"); salva("06-catalogo")
        vai("Assistente", "Chi scrive la risposta"); salva("07-assistente")
        vai("Fonti dati e importazione", "HONOR Health Kit"); salva("08-fonti")
        vai("Esporta e backup", "Backup cifrato"); salva("09-esporta")
        vai("Impostazioni", "Obiettivi"); salva("10-impostazioni")
    }
}
