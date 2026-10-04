package org.costalonga.sportintv

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.costalonga.sportintv.dati.Promemoria
import org.costalonga.sportintv.promemoria.Pianificatore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/** Allarmi dei promemoria: uno per evento, sostituiti e cancellati senza duplicati. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PianificatoreTest {
    private val app = ApplicationProvider.getApplicationContext<SportApp>()
    private val allarmi get() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private val adesso = Instant.parse("2026-10-11T12:00:00Z")

    private fun p(id: String, inizio: String, anticipo: Long = 15) = Instant.parse(inizio).let {
        Promemoria(id, "impronta", "Titolo", it.toEpochMilli(), it.minusSeconds(anticipo * 60).toEpochMilli(), automatico = false)
    }

    @Test
    fun pianificaSostituisceECancella() {
        val pian = Pianificatore(app)
        pian.pianifica(p("a", "2026-10-11T16:00:00Z"), adesso)
        pian.pianifica(p("b", "2026-10-11T18:00:00Z"), adesso)
        assertEquals(2, allarmi.size)
        // Nuovo orario per "a": l'allarme si sostituisce, non si aggiunge.
        pian.pianifica(p("a", "2026-10-11T18:45:00Z"), adesso)
        assertEquals(2, allarmi.size)
        assertTrue(allarmi.any { it.triggerAtMs == Instant.parse("2026-10-11T18:30:00Z").toEpochMilli() })
        pian.cancella("a")
        assertEquals(1, allarmi.size)
        pian.cancella("b")
        assertTrue(allarmi.isEmpty())
    }

    @Test
    fun eventoPassatoONotificatoNonSuona() {
        val pian = Pianificatore(app)
        pian.pianifica(p("vecchio", "2026-10-11T11:00:00Z"), adesso)
        pian.pianifica(p("fatto", "2026-10-11T16:00:00Z").copy(notificato = true), adesso)
        assertTrue(allarmi.isEmpty())
        // Anticipo già passato ma evento futuro: avviso subito (entro pochi secondi).
        pian.pianifica(p("imminente", "2026-10-11T12:05:00Z"), adesso)
        assertEquals(adesso.toEpochMilli() + 5_000, allarmi.single().triggerAtMs)
    }
}
