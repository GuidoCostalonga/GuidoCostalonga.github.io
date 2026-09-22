package org.costalonga.meteofvg.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.costalonga.meteofvg.data.MeteoRete
import org.costalonga.meteofvg.data.Preferenze
import java.util.concurrent.Executors

/**
 * Il widget della schermata iniziale.
 *
 * A ogni risveglio mostra subito l'ultimo dato salvato, poi chiede a
 * Open-Meteo quello aggiornato e riscrive. Il sistema lo risveglia ogni mezz'ora
 * (updatePeriodMillis nel file widget_meteo_info.xml); la freccia lo aggiorna
 * quando si vuole, e l'app lo riscrive appena riceve dati nuovi.
 */
class MeteoWidgetProvider : AppWidgetProvider() {

    override fun onReceive(contesto: Context, intento: Intent) {
        when (intento.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, AZIONE_RICARICA -> {
                val gestore = AppWidgetManager.getInstance(contesto)
                val identificativi = identificativi(contesto, gestore, intento)
                if (identificativi.isEmpty()) return

                // Primo passaggio: quel che si sa gia', senza attese.
                val salvato = Preferenze.istantanea(contesto)
                identificativi.forEach { WidgetDisegno.disegna(contesto, gestore, it, salvato) }

                // Secondo passaggio: il dato aggiornato.
                val attesa = goAsync()
                ESECUTORE.execute {
                    try {
                        val comune = Preferenze.comune(contesto)
                        val fresco = MeteoRete.istantanea(comune)
                        Preferenze.salvaIstantanea(contesto, fresco)
                        identificativi.forEach {
                            WidgetDisegno.disegna(contesto, gestore, it, fresco)
                        }
                    } catch (e: Exception) {
                        Log.w(ETICHETTA, "aggiornamento del widget non riuscito", e)
                        // Il dato vecchio resta, con l'avviso al posto dell'ora.
                        identificativi.forEach {
                            WidgetDisegno.disegna(contesto, gestore, it, salvato, avviso = "dati non aggiornati")
                        }
                    } finally {
                        attesa.finish()
                    }
                }
            }

            else -> super.onReceive(contesto, intento)
        }
    }

    /** Al cambio di formato il widget va ridisegnato con il layout giusto. */
    override fun onAppWidgetOptionsChanged(
        contesto: Context,
        gestore: AppWidgetManager,
        idWidget: Int,
        nuoveOpzioni: Bundle?,
    ) {
        WidgetDisegno.disegna(contesto, gestore, idWidget, Preferenze.istantanea(contesto))
    }

    private fun identificativi(
        contesto: Context,
        gestore: AppWidgetManager,
        intento: Intent,
    ): IntArray {
        val daIntento = intento.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
        if (daIntento != null && daIntento.isNotEmpty()) return daIntento
        val uno = intento.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (uno != AppWidgetManager.INVALID_APPWIDGET_ID) return intArrayOf(uno)
        return gestore.getAppWidgetIds(ComponentName(contesto, MeteoWidgetProvider::class.java))
    }

    companion object {
        const val AZIONE_RICARICA = "org.costalonga.meteofvg.RICARICA_WIDGET"
        private const val ETICHETTA = "MeteoWidget"
        private val ESECUTORE = Executors.newSingleThreadExecutor()

        /** Chiede al sistema di riscrivere tutti i widget in opera. */
        fun aggiornaTutti(contesto: Context) {
            val gestore = AppWidgetManager.getInstance(contesto)
            val identificativi = gestore.getAppWidgetIds(
                ComponentName(contesto, MeteoWidgetProvider::class.java),
            )
            if (identificativi.isEmpty()) return
            val salvato = Preferenze.istantanea(contesto)
            identificativi.forEach { WidgetDisegno.disegna(contesto, gestore, it, salvato) }
        }
    }
}
