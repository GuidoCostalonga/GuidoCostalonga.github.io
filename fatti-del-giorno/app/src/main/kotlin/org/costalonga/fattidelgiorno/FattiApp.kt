package org.costalonga.fattidelgiorno

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.costalonga.fattidelgiorno.ai.Gemini
import org.costalonga.fattidelgiorno.dati.FattiRepository
import org.costalonga.fattidelgiorno.dati.locale.CacheFatti
import org.costalonga.fattidelgiorno.dati.locale.RicorrenzeLocali
import org.costalonga.fattidelgiorno.dati.rete.ClientHttp
import org.costalonga.fattidelgiorno.dati.rete.ControlloRete
import org.costalonga.fattidelgiorno.dati.rete.FonteFatti
import org.costalonga.fattidelgiorno.dati.rete.WikidataPersone
import org.costalonga.fattidelgiorno.dati.rete.WikipediaOggi
import org.costalonga.fattidelgiorno.sicurezza.ChiaveSegreta
import java.io.File

/** Mette insieme le parti dell'app una volta sola; l'interfaccia le riceve da qui. */
class FattiApp : Application() {

    val impostazioni: Impostazioni by lazy { Impostazioni(this) }
    val chiavi: ChiaveSegreta by lazy { ChiaveSegreta(this) }
    val client: ClientHttp by lazy { ClientHttp() }
    val gemini: Gemini by lazy { Gemini(client) }
    val cache: CacheFatti by lazy { CacheFatti(File(cacheDir, "fatti")) }
    val locali: RicorrenzeLocali by lazy {
        RicorrenzeLocali { assets.open("ricorrenze.json").bufferedReader().use { it.readText() } }
    }

    val rete: ControlloRete = ControlloRete {
        val gestore = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@ControlloRete false
        val capacita = gestore.getNetworkCapabilities(gestore.activeNetwork) ?: return@ControlloRete false
        capacita.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacita.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Le fonti in rete seguono le impostazioni, quindi si ricostruiscono a ogni ricerca. */
    fun repository(): FattiRepository {
        val fonti = mutableListOf<FonteFatti>(
            WikipediaOggi(client, "it"),
            WikidataPersone(client, WikidataPersone.Ambito.NATI_FVG),
            WikidataPersone(client, WikidataPersone.Ambito.MORTI_FVG),
        )
        if (impostazioni.wikidataItalia) fonti += WikidataPersone(client, WikidataPersone.Ambito.NATI_ITALIA)
        if (impostazioni.wikipediaInglese) fonti += WikipediaOggi(client, "en")
        return FattiRepository(fonti, locali, cache, rete)
    }
}
