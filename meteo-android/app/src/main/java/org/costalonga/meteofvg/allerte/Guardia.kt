package org.costalonga.meteofvg.allerte

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.costalonga.meteofvg.data.Preferenze

/**
 * Il controllo in sottofondo del riquadro della Protezione Civile.
 *
 * Gira circa ogni ora quando c'e' rete, legge il riquadro ufficiale per il
 * Comune scelto e avvisa solo se e' comparsa un'allerta nuova.
 */
class GuardiaAllerteLavoro(
    contesto: Context,
    parametri: WorkerParameters,
) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        if (!Preferenze.avvisiAttivi(contesto)) {
            Guardia.disattiva(contesto)
            return Result.success()
        }

        val comune = Preferenze.comune(contesto)

        // La WebView vive sul filo principale, anche quando nessuno la guarda.
        val esito = withContext(Dispatchers.Main) {
            leggiAllerta(contesto, comune.istat)
        }

        // Il riquadro non raggiungibile non e' una notizia e non cancella
        // quello che sapevamo: si riprova al giro dopo.
        if (esito.stato == StatoAllerte.NON_RAGGIUNGIBILE) return Result.success()

        val impronta = Decisione.impronta(esito.testo)
        val memoria = Preferenze.memoriaAvviso(contesto)
        val stessoComune = memoria?.comune == comune.nome

        val avvisare = Decisione.daAvvisare(
            precedente = if (stessoComune) memoria?.stato else null,
            improntaPrecedente = if (stessoComune) memoria?.impronta else null,
            adesso = esito.stato,
            improntaAdesso = impronta,
        )

        Preferenze.salvaMemoriaAvviso(contesto, comune, esito.stato, impronta)

        if (avvisare) Notifiche.avvisa(contesto, comune.nome, esito.testo)

        return Result.success()
    }
}

/** Accende e spegne il controllo. */
object Guardia {

    private const val LAVORO = "guardia-allerte"

    fun attiva(contesto: Context) {
        val richiesta = PeriodicWorkRequestBuilder<GuardiaAllerteLavoro>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(contesto).enqueueUniquePeriodicWork(
            LAVORO,
            ExistingPeriodicWorkPolicy.UPDATE,
            richiesta,
        )
    }

    fun disattiva(contesto: Context) {
        WorkManager.getInstance(contesto).cancelUniqueWork(LAVORO)
    }
}
