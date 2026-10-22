package org.costalonga.fattidelgiorno.dati.locale

import kotlinx.serialization.json.Json
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.RisultatoRicerca
import java.io.File

/** Memoria dei risultati per giorno e mese, un file JSON per data, nella cache dell'app. */
class CacheFatti(private val cartella: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(giorno: Int, mese: Int) = File(cartella, "${Date.dueCifre(mese)}-${Date.dueCifre(giorno)}.json")

    fun salva(risultato: RisultatoRicerca) {
        cartella.mkdirs()
        file(risultato.giorno, risultato.mese).writeText(json.encodeToString(RisultatoRicerca.serializer(), risultato))
    }

    fun leggi(giorno: Int, mese: Int): RisultatoRicerca? {
        val f = file(giorno, mese)
        if (!f.isFile) return null
        return runCatching { json.decodeFromString(RisultatoRicerca.serializer(), f.readText()) }.getOrNull()
    }

    fun svuota() {
        cartella.listFiles()?.forEach { it.delete() }
    }

    fun quanteDate(): Int = cartella.listFiles()?.count { it.name.endsWith(".json") } ?: 0
}
