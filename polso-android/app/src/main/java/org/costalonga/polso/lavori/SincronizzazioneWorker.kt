package org.costalonga.polso.lavori

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.costalonga.polso.PolsoApp
import org.costalonga.polso.R
import org.costalonga.polso.contenitore
import org.costalonga.polso.dati.NotificaEntita
import org.costalonga.polso.fonti.SincronizzatoreHc
import org.costalonga.polso.motore.Regole
import org.costalonga.polso.motore.Segnalazione
import org.costalonga.polso.ui.MainActivity
import java.time.Instant
import java.time.LocalDate

/**
 * Lavoro periodico: legge le novità da Health Connect (se la lettura in
 * secondo piano è concessa) e valuta le regole delle notifiche. Android e
 * MagicOS possono rinviarlo per risparmiare batteria: l'ultima
 * sincronizzazione riuscita è sempre mostrata nell'app.
 */
class SincronizzazioneWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object { const val NOME = "sincronizzazione_polso" }

    override suspend fun doWork(): Result {
        val c = applicationContext.contenitore
        val imp = c.impostazioni.attuali()
        if (imp.modalitaDemo) return Result.success()
        c.fabbricaClientHc()?.let { client ->
            runCatching { SincronizzatoreHc(client, c.archivioReale, c.impostazioni).sincronizza(soloSecondoPiano = true) }
        }
        valutaNotifiche(applicationContext)
        return Result.success()
    }
}

/** Valuta le regole sui dati reali e, se è il momento, invia una sola notifica. */
suspend fun valutaNotifiche(context: Context, adesso: Instant = Instant.now()): Segnalazione? {
    val c = context.contenitore
    val imp = c.impostazioni.attuali()
    val pref = imp.preferenze()
    val oggi = LocalDate.now(pref.zona)
    val dati = c.carica(c.archivioReale, pref, oggi.minusDays(40), oggi)
    val segnalazioni = Regole.valuta(dati, oggi, imp.notifiche)
    val storico = c.archivioReale.dao.ultimeNotifiche().associate { it.regola to it.ultimo }
    val s = Regole.daInviare(segnalazioni, storico, adesso, imp.notifiche, pref.zona) ?: return null
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return null
    val apri = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("sezione", "notifiche"), PendingIntent.FLAG_IMMUTABLE)
    val testo = "${s.testo}\n\nDato: ${s.dato}. Periodo: ${s.periodo}. Motivo: ${s.motivo}."
    val n = NotificationCompat.Builder(context, PolsoApp.CANALE_VARIAZIONI)
        .setSmallIcon(R.drawable.ic_notifica)
        .setContentTitle(s.titolo)
        .setContentText(s.testo.take(120))
        .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
        .setContentIntent(apri)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(s.regola.hashCode(), n)
    c.archivioReale.dao.registraNotifica(NotificaEntita(regola = s.regola, titolo = s.titolo, testo = s.testo, motivo = "${s.dato} · ${s.periodo} · ${s.motivo}", inviataIl = adesso.toEpochMilli()))
    return s
}
