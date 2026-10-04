package org.costalonga.sportintv.promemoria

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.costalonga.sportintv.MainActivity
import org.costalonga.sportintv.R
import org.costalonga.sportintv.SportApp
import org.costalonga.sportintv.dati.Promemoria
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.ui.Formato
import java.time.Instant

/**
 * Pianificazione dei promemoria con AlarmManager.setAndAllowWhileIdle.
 *
 * Non si usano allarmi esatti: richiederebbero il permesso
 * SCHEDULE_EXACT_ALARM, che Android riserva a sveglie e calendari. Con
 * setAndAllowWhileIdle l'avviso arriva anche col telefono in riposo, ma Android
 * può ritardarlo di alcuni minuti (fino a una quindicina in Doze profondo).
 *
 * Ogni evento ha un solo allarme (codice = hash dell'identificativo): ripianificare
 * sostituisce l'allarme precedente, quindi non nascono duplicati.
 */
class Pianificatore(private val context: Context) {

    private val allarmi = context.getSystemService(AlarmManager::class.java)

    private fun intento(eventoId: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            eventoId.hashCode(),
            Intent(context, RicevitorePromemoria::class.java).setAction(AZIONE).putExtra(EXTRA_ID, eventoId),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    fun pianifica(p: Promemoria, adesso: Instant = Instant.now()) {
        if (p.notificato) return cancella(p.eventoId)
        val inizio = Instant.ofEpochMilli(p.inizio)
        if (!inizio.isAfter(adesso)) return cancella(p.eventoId)
        // Se l'anticipo è già passato ma l'evento non è cominciato, l'avviso parte subito.
        val quando = maxOf(p.allarme, adesso.toEpochMilli() + 5_000)
        val pi = intento(p.eventoId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        allarmi.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
    }

    fun cancella(eventoId: String) {
        // Stesso codice e stesso intento dell'allarme: AlarmManager lo riconosce e lo toglie.
        intento(eventoId, PendingIntent.FLAG_UPDATE_CURRENT)?.let { allarmi.cancel(it); it.cancel() }
    }

    companion object {
        const val AZIONE = "org.costalonga.sportintv.PROMEMORIA"
        const val EXTRA_ID = "evento_id"
        const val CANALE = "promemoria"

        fun creaCanale(context: Context) {
            val canale = NotificationChannel(CANALE, context.getString(R.string.canale_promemoria), NotificationManager.IMPORTANCE_HIGH)
            canale.description = context.getString(R.string.canale_promemoria_descrizione)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(canale)
        }

        fun notificheConsentite(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

        fun mostra(context: Context, eventoId: String, titolo: String, testo: String) {
            if (!notificheConsentite(context)) return
            val apri = PendingIntent.getActivity(
                context, eventoId.hashCode(),
                Intent(context, MainActivity::class.java).putExtra(EXTRA_ID, eventoId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(apri)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(eventoId.hashCode(), n)
            } catch (_: SecurityException) {
                // Permesso revocato nel frattempo: niente avviso.
            }
        }

        fun testoPromemoria(e: Evento?, p: Promemoria): String {
            if (e == null) return "Comincia alle ${Formato.ora(Instant.ofEpochMilli(p.inizio))}."
            val canali = e.trasmissioni.map { it.canale }.distinct().joinToString(", ")
            return "Comincia alle ${Formato.ora(e.inizio)}" + if (canali.isNotEmpty()) " su $canali." else ". Trasmissione in Italia da confermare."
        }
    }
}

private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Allarme scattato: mostra l'avviso e segna il promemoria come già notificato. */
class RicevitorePromemoria : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Pianificatore.EXTRA_ID) ?: return
        val app = context.applicationContext as SportApp
        val attesa = goAsync()
        ambito.launch {
            try {
                val dao = app.db.preferiti()
                val p = dao.promemoriaDi(id) ?: return@launch
                if (p.notificato) return@launch
                val evento = app.archivio.eventiSalvati().firstOrNull { it.id == id }
                Pianificatore.mostra(context, id, evento?.titolo ?: p.titolo, Pianificatore.testoPromemoria(evento, p))
                dao.salvaPromemoria(p.copy(notificato = true))
            } finally {
                attesa.finish()
            }
        }
    }
}

/** Riavvio, aggiornamento dell'app, cambio di ora o di fuso: si ripianifica tutto. */
class RicevitoreSistema : BroadcastReceiver() {
    private val azioni = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in azioni) return
        val app = context.applicationContext as SportApp
        val attesa = goAsync()
        ambito.launch {
            try {
                app.promemoria.ripianificaTutti()
            } finally {
                attesa.finish()
            }
        }
    }
}
