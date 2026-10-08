package org.costalonga.polso

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.costalonga.polso.dati.Archivio
import org.costalonga.polso.dati.ArchivioImpostazioni
import org.costalonga.polso.dati.PolsoDb
import org.costalonga.polso.dati.Segreti
import org.costalonga.polso.fonti.ClientHc
import org.costalonga.polso.fonti.ClientHcReale
import org.costalonga.polso.ia.GestoreModelli
import org.costalonga.polso.ia.IaLocale
import org.costalonga.polso.lavori.SincronizzazioneWorker
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Preferenze
import org.costalonga.polso.motore.Tempo
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Dipendenze dell'app, create una volta sola (niente librerie di iniezione). */
class Contenitore(val context: Context) {
    val impostazioni = ArchivioImpostazioni(context)
    val segreti = Segreti(context)
    private val dbReale by lazy { PolsoDb.apri(context, PolsoDb.NOME) }
    private val dbDemo by lazy { PolsoDb.apri(context, PolsoDb.NOME_DEMO) }
    val archivioReale by lazy { Archivio(dbReale) }
    val archivioDemo by lazy { Archivio(dbDemo) }
    val modelli = GestoreModelli(context)
    val iaLocale = IaLocale(context, modelli)
    val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fabbrica del client: nelle prove viene sostituita con un finto client. */
    var fabbricaClientHc: () -> ClientHc? = {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) ClientHcReale(context) else null
    }

    fun archivio(demo: Boolean) = if (demo) archivioDemo else archivioReale

    /**
     * Carica in memoria i dati utili a un periodo: tutte le misure da 120
     * giorni prima dell'inizio (per i riferimenti personali) e, per tutto lo
     * storico, le grandezze leggere usate da record e confronti annuali.
     */
    suspend fun carica(archivio: Archivio, pref: Preferenze, da: LocalDate, a: LocalDate): Dati {
        val inizio = Tempo.inizioGiorno(da.minusDays(120), pref.zona)
        val fine = Tempo.inizioGiorno(a.plusDays(2), pref.zona)
        val vicine = archivio.misure(inizio, fine)
        val storiche = archivio.misureDi(listOf(Metrica.PASSI, Metrica.DISTANZA, Metrica.PESO, Metrica.FC_RIPOSO, Metrica.CALORIE_ATTIVE).map { it.codice })
            .filter { it.inizio < inizio }
        val annoPrima = archivio.misure(Tempo.inizioGiorno(da.minusYears(1), pref.zona), Tempo.inizioGiorno(a.minusYears(1).plusDays(1), pref.zona))
            .filter { it.inizio < inizio && it.metrica !in setOf(Metrica.PASSI.codice, Metrica.DISTANZA.codice, Metrica.PESO.codice, Metrica.FC_RIPOSO.codice, Metrica.CALORIE_ATTIVE.codice) }
        return Dati(vicine + storiche + annoPrima, archivio.sonni(0, Long.MAX_VALUE), archivio.allenamenti(0, Long.MAX_VALUE), archivio.diario(), pref)
    }

    fun pianificaSincronizzazione(ore: Int, attiva: Boolean) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!attiva) { wm.cancelUniqueWork(SincronizzazioneWorker.NOME); return }
        val r = PeriodicWorkRequestBuilder<SincronizzazioneWorker>(ore.coerceIn(1, 24).toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(SincronizzazioneWorker.NOME, ExistingPeriodicWorkPolicy.UPDATE, r)
    }
}

class PolsoApp : Application() {
    lateinit var contenitore: Contenitore

    override fun onCreate() {
        super.onCreate()
        contenitore = Contenitore(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANALE_VARIAZIONI, getString(R.string.canale_variazioni), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = getString(R.string.canale_variazioni_descrizione)
        })
        contenitore.ambito.launch {
            val i = contenitore.impostazioni.attuali()
            runCatching { contenitore.pianificaSincronizzazione(i.oreSincronizzazione, i.sincronizzazioneAutomatica && !i.modalitaDemo) }
        }
    }

    companion object {
        const val CANALE_VARIAZIONI = "variazioni"
    }
}

val Context.contenitore: Contenitore get() = (applicationContext as PolsoApp).contenitore
