package org.costalonga.sportintv

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.costalonga.sportintv.dati.Origine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Primo avvio senza rete: l'app dice "dati non disponibili", non "nessun evento". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppSenzaDatiTest {
    @get:Rule
    val regola = createEmptyComposeRule()

    @Test
    fun primoAvvioSenzaRete() {
        val app = ApplicationProvider.getApplicationContext<SportApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.impostazioni.origine(Origine.SERVIZIO)
            app.impostazioni.urlServizio("https://servizio-non-raggiungibile.invalid/eventi.json")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            regola.waitUntil(20_000) { regola.onAllNodesWithText("Dati non disponibili").fetchSemanticsNodes().isNotEmpty() }
            // Tre tentativi distanziati prima di arrendersi: può volerci un minuto.
            regola.waitUntil(150_000) { regola.onAllNodesWithText("Riprova").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
