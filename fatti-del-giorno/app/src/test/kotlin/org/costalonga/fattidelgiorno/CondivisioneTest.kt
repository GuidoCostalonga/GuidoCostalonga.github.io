package org.costalonga.fattidelgiorno

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.costalonga.fattidelgiorno.ui.Condivisione
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Copia e condivisione passano dalle funzioni di Android, non da accessi ai social. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CondivisioneTest {

    private val contesto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `la condivisione apre il menu di Android con il testo`() {
        val scelta = Condivisione.intentCondividi("Bozza da condividere", "Condividi la bozza")
        assertEquals(Intent.ACTION_CHOOSER, scelta.action)
        @Suppress("DEPRECATION")
        val invio = scelta.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, invio.action)
        assertEquals("text/plain", invio.type)
        assertEquals("Bozza da condividere", invio.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `la copia mette il testo negli appunti`() {
        assertTrue(Condivisione.copia(contesto, "Prompt", "Testo del prompt"))
        val appunti = contesto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("Testo del prompt", appunti.primaryClip!!.getItemAt(0).text.toString())
        assertEquals("Prompt", appunti.primaryClipDescription!!.label.toString())
    }

    @Test
    fun `le fonti si aprono nel browser`() {
        val intent = Condivisione.intentApri("https://it.wikipedia.org/wiki/Trieste")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://it.wikipedia.org/wiki/Trieste", intent.dataString)
    }
}
