package org.costalonga.sportintv.raccolta

import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

/**
 * Raccolta da riga di comando, usata dal servizio su GitHub Actions.
 *
 *   --uscita FILE        dove scrivere il pacchetto (predefinito: eventi.json)
 *   --precedente FILE|URL  pacchetto precedente, per conservare le fonti che non rispondono
 *   --giorni N           ampiezza della finestra (predefinito: 15, cioè oggi più 14)
 */
fun main(args: Array<String>) {
    val opzioni = args.toList().chunked(2).associate { it[0].removePrefix("--") to it.getOrElse(1) { "" } }
    val uscita = File(opzioni["uscita"] ?: "eventi.json")
    val giorni = opzioni["giorni"]?.toIntOrNull() ?: 15
    val precedente = opzioni["precedente"]?.let { leggiPrecedente(it) }

    val pacchetto = runBlocking {
        Raccoglitore().raccogli(Finestra.standard().copy(giorni = giorni), precedente)
    }

    println("Raccolta del ${pacchetto.generato}")
    for (f in pacchetto.fonti) {
        println(" - ${f.nome}: ${f.stato} · ${f.eventi} elementi${f.messaggio?.let { " · $it" } ?: ""}")
    }
    val confermati = pacchetto.eventi.count { it.trasmissioneVerificata }
    println("Eventi: ${pacchetto.eventi.size} (con trasmissione confermata: $confermati, da confermare: ${pacchetto.eventi.size - confermati})")

    if (pacchetto.eventi.isEmpty()) {
        System.err.println("Nessun evento raccolto: il file non viene scritto, resta valido il precedente.")
        exitProcess(2)
    }
    uscita.absoluteFile.parentFile?.mkdirs()
    uscita.writeText(JsonSportTv.encodeToString(Pacchetto.serializer(), pacchetto))
    println("Scritto ${uscita.path} (${uscita.length() / 1024} kB)")
}

private fun leggiPrecedente(origine: String): Pacchetto? = runCatching {
    val testo = if (origine.startsWith("http")) URI(origine).toURL().readText() else File(origine).readText()
    JsonSportTv.decodeFromString(Pacchetto.serializer(), testo)
}.onFailure { println("Pacchetto precedente non disponibile ($origine): ${it.message}") }.getOrNull()
