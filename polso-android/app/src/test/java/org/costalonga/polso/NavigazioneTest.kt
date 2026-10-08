package org.costalonga.polso

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.costalonga.polso.ui.MainActivity
import org.costalonga.polso.ui.Pagina
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Avvio in modalità dimostrativa e visita di tutte le pagine dal menu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PolsoApp::class, qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigazioneTest {
    @get:Rule(order = 0) val lavori = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
            // Le impostazioni restano in memoria fra una prova e l'altra: si riparte da zero.
            kotlinx.coroutines.runBlocking { ctx.contenitore.impostazioni.modifica { org.costalonga.polso.dati.Impostazioni() } }
        }
    }
    @get:Rule(order = 1) val regola = createAndroidComposeRule<MainActivity>()

    private fun attendi(testo: String, ms: Long = 60_000) =
        regola.waitUntil(ms) { regola.onAllNodesWithText(testo, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun primoAvvioDemoEPagine() {
        attendi("Benvenuto in Polso")
        regola.onNodeWithText("Prova con dati dimostrativi").performClick()
        attendi("MODALITÀ DIMOSTRATIVA")
        attendi("Oggi,")
        for (p in Pagina.entries.drop(1)) {
            regola.onNodeWithContentDescription("Menu").performClick()
            regola.waitForIdle()
            regola.onAllNodesWithText(p.titolo).onFirst().performScrollTo().performClick()
            regola.waitForIdle()
            regola.waitUntil(30_000) { regola.onAllNodesWithText(p.titolo).fetchSemanticsNodes().isNotEmpty() }
        }
        // La sezione Sonno mostra analisi calcolate sui dati dimostrativi
        regola.onNodeWithContentDescription("Menu").performClick()
        regola.onAllNodesWithText("Sonno").onFirst().performClick()
        attendi("Durata del sonno")
    }
}
