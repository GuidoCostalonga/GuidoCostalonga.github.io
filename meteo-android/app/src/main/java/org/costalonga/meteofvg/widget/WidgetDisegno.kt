package org.costalonga.meteofvg.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.costalonga.meteofvg.MainActivity
import org.costalonga.meteofvg.R
import org.costalonga.meteofvg.data.Famiglia
import org.costalonga.meteofvg.data.Istantanea
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Scrive nel widget.
 *
 * Il widget vive nel processo del lanciatore: non puo' eseguire codice
 * nostro, riceve solo un elenco di istruzioni su viste consentite. I sei
 * fotogrammi dell'animazione li sceglie questo codice, a farli scorrere
 * pensa il ViewFlipper del sistema.
 */
object WidgetDisegno {

    private val ORA = DateTimeFormatter.ofPattern("HH:mm")

    private val FOTOGRAMMI = intArrayOf(
        R.id.fotogramma_0,
        R.id.fotogramma_1,
        R.id.fotogramma_2,
        R.id.fotogramma_3,
        R.id.fotogramma_4,
        R.id.fotogramma_5,
    )

    fun disegna(
        contesto: Context,
        gestore: AppWidgetManager,
        idWidget: Int,
        dati: Istantanea?,
        avviso: String? = null,
    ) {
        val viste = RemoteViews(contesto.packageName, layout(gestore, idWidget))

        if (dati != null) {
            val condizione = dati.condizione
            viste.setTextViewText(R.id.comune, dati.comune.nome)
            viste.setTextViewText(R.id.temperatura, dati.temperatura.roundToInt().toString())
            viste.setTextViewText(R.id.condizione, condizione.descrizione)
            viste.setTextViewText(
                R.id.estremi,
                "min ${dati.minima.roundToInt()}°  max ${dati.massima.roundToInt()}°" +
                    "   pioggia ${dati.pioggia}%",
            )
            viste.setTextViewText(
                R.id.aggiornato,
                avviso ?: Instant.ofEpochMilli(dati.quando)
                    .atZone(ZoneId.systemDefault())
                    .format(ORA),
            )
            fotogrammi(viste, condizione.famiglia)
        } else {
            viste.setTextViewText(R.id.comune, contesto.getString(R.string.app_nome))
            viste.setTextViewText(R.id.temperatura, "--")
            viste.setTextViewText(R.id.condizione, "")
            viste.setTextViewText(
                R.id.estremi,
                avviso ?: contesto.getString(R.string.widget_in_attesa),
            )
            viste.setTextViewText(R.id.aggiornato, "")
            fotogrammi(viste, Famiglia.NUVOLE)
        }

        // Tocco sulla scheda: apre l'app. Tocco sulla freccia: aggiorna subito.
        viste.setOnClickPendingIntent(R.id.radice, apriApp(contesto))
        viste.setOnClickPendingIntent(R.id.ricarica, ricarica(contesto, idWidget))

        gestore.updateAppWidget(idWidget, viste)
    }

    private fun fotogrammi(viste: RemoteViews, famiglia: Famiglia) {
        val disegni = famiglia.fotogrammi
        for (i in FOTOGRAMMI.indices) {
            viste.setImageViewResource(FOTOGRAMMI[i], disegni[i])
        }
    }

    /** Sotto i 180dp di larghezza il formato largo non ci sta. */
    private fun layout(gestore: AppWidgetManager, idWidget: Int): Int {
        val opzioni: Bundle? = runCatching { gestore.getAppWidgetOptions(idWidget) }.getOrNull()
        val larghezza = opzioni?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        return if (larghezza in 1 until 180) R.layout.widget_meteo_compatto else R.layout.widget_meteo
    }

    private fun apriApp(contesto: Context): PendingIntent {
        val intento = Intent(contesto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            contesto,
            0,
            intento,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ricarica(contesto: Context, idWidget: Int): PendingIntent {
        val intento = Intent(contesto, MeteoWidgetProvider::class.java).apply {
            action = MeteoWidgetProvider.AZIONE_RICARICA
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget)
        }
        return PendingIntent.getBroadcast(
            contesto,
            idWidget,
            intento,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
