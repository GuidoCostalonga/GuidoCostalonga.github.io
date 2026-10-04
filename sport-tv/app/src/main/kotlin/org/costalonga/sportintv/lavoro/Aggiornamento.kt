package org.costalonga.sportintv.lavoro

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.costalonga.sportintv.SportApp
import org.costalonga.sportintv.dati.EsitoAggiornamento
import java.util.concurrent.TimeUnit

/**
 * Aggiornamento automatico in background, ogni 6 ore e solo con la rete.
 * Il servizio online si aggiorna ogni 3 ore: chiederlo più spesso non
 * porterebbe dati nuovi. Android può spostare l'esecuzione per risparmiare
 * batteria.
 */
class LavoroAggiornamento(context: Context, parametri: WorkerParameters) : CoroutineWorker(context, parametri) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SportApp
        return when (app.archivio.aggiorna()) {
            is EsitoAggiornamento.Riuscito -> {
                app.promemoria.sincronizza()
                Result.success()
            }
            is EsitoAggiornamento.Fallito -> if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NOME = "aggiornamento-periodico"

        fun programma(context: Context, attivo: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!attivo) {
                wm.cancelUniqueWork(NOME)
                return
            }
            val richiesta = PeriodicWorkRequestBuilder<LavoroAggiornamento>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }
    }
}
