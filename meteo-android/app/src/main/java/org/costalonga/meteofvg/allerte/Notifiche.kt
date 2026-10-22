package org.costalonga.meteofvg.allerte

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.costalonga.meteofvg.MainActivity
import org.costalonga.meteofvg.R

/**
 * La notifica dell'allerta: una sola, sostituita quando l'avviso cambia.
 */
object Notifiche {

    private const val CANALE = "allerte"
    private const val AVVISO = 1

    /** Il canale va creato prima di notificare, da Android 8 in poi. */
    fun preparaCanale(contesto: Context) {
        val canale = NotificationChannelCompat.Builder(
            CANALE,
            NotificationManager.IMPORTANCE_HIGH,
        )
            .setName(contesto.getString(R.string.avvisi_canale))
            .setDescription(contesto.getString(R.string.avvisi_canale_descrizione))
            .build()
        NotificationManagerCompat.from(contesto).createNotificationChannel(canale)
    }

    /** Vero se Android ci lascia notificare: da Android 13 serve il permesso. */
    fun permesso(contesto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(contesto).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            contesto,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Mostra l'avviso. Il testo e' quello del riquadro della Regione,
     * accorciato ma non riscritto.
     */
    fun avvisa(contesto: Context, comune: String, testo: String) {
        // Il permesso si controlla qui, sulla riga prima di notificare: senza,
        // da Android 13 la chiamata verrebbe rifiutata.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                contesto,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(contesto).areNotificationsEnabled()) return

        preparaCanale(contesto)

        val apri = Intent(contesto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tocco = PendingIntent.getActivity(
            contesto,
            0,
            apri,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val riga = Decisione.riassunto(testo)
        val notifica = NotificationCompat.Builder(contesto, CANALE)
            .setSmallIcon(R.drawable.ic_avviso)
            .setContentTitle(contesto.getString(R.string.avvisi_titolo, comune))
            .setContentText(riga)
            .setStyle(NotificationCompat.BigTextStyle().bigText(riga))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(tocco)
            .setAutoCancel(true)
            .build()

        // Se il permesso viene tolto fra il controllo e la consegna, l'avviso
        // salta: non deve far cadere l'app.
        runCatching { NotificationManagerCompat.from(contesto).notify(AVVISO, notifica) }
    }
}
