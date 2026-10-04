package org.costalonga.sportintv

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.costalonga.sportintv.dati.Archivio
import org.costalonga.sportintv.dati.Impostazioni
import org.costalonga.sportintv.dati.SportDatabase
import org.costalonga.sportintv.lavoro.LavoroAggiornamento
import org.costalonga.sportintv.promemoria.GestorePromemoria
import org.costalonga.sportintv.promemoria.Pianificatore

/** Contenitore delle dipendenze dell'app: semplice, senza framework di iniezione. */
class SportApp : Application(), Configuration.Provider {
    val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { SportDatabase.crea(this) }
    val impostazioni by lazy { Impostazioni(this) }
    val archivio by lazy { Archivio(db, impostazioni) }
    val promemoria by lazy { GestorePromemoria(this, db, archivio, impostazioni) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        Pianificatore.creaCanale(this)
        ambito.launch {
            LavoroAggiornamento.programma(this@SportApp, impostazioni.attuali().aggiornamentoAutomatico)
        }
    }
}
