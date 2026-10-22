package org.costalonga.fattidelgiorno.dati

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.costalonga.fattidelgiorno.dati.locale.CacheFatti
import org.costalonga.fattidelgiorno.dati.locale.RicorrenzeLocali
import org.costalonga.fattidelgiorno.dati.rete.ControlloRete
import org.costalonga.fattidelgiorno.dati.rete.EsitoFonte
import org.costalonga.fattidelgiorno.dati.rete.FonteFatti
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Avviso
import org.costalonga.fattidelgiorno.dominio.Classificatore
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.RisultatoRicerca
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Mette insieme le fonti: archivio locale, fonti in rete, memoria sul telefono.
 * Decide quando usare la memoria, toglie i doppioni e ordina le schede.
 */
class FattiRepository(
    private val fontiRete: List<FonteFatti>,
    private val locali: RicorrenzeLocali,
    private val cache: CacheFatti,
    private val rete: ControlloRete,
    private val clock: Clock = Clock.systemUTC(),
    private val tempoMassimoFonteMs: Long = 25_000,
    private val validitaMemoria: Duration = Duration.ofHours(24),
) {

    suspend fun cerca(giorno: Int, mese: Int, forzaAggiornamento: Boolean = false): RisultatoRicerca {
        require(Date.giornoValido(giorno, mese)) { "data non valida: $giorno/$mese" }
        val daArchivio = locali.perData(giorno, mese)
        val memoria = cache.leggi(giorno, mese)
        val adesso = Date.adesso(clock)

        if (!rete.online()) {
            val avviso = Avviso("Connessione", TipoAvviso.OFFLINE, "Nessuna connessione: vengono mostrati solo i contenuti già memorizzati")
            return if (memoria != null) {
                memoria.copy(fatti = unisci(daArchivio, memoria.fatti), avvisi = listOf(avviso), daMemoria = true)
            } else {
                RisultatoRicerca(giorno, mese, ordina(daArchivio), listOf(avviso), daMemoria = true, consultatoIl = adesso)
                    .conAvvisoSeVuoto()
            }
        }

        if (!forzaAggiornamento && memoria != null && recente(memoria.consultatoIl)) {
            return memoria.copy(fatti = unisci(daArchivio, memoria.fatti), avvisi = emptyList(), daMemoria = true)
        }

        val esiti: List<EsitoFonte> = coroutineScope {
            fontiRete.map { fonte ->
                async {
                    withTimeoutOrNull(tempoMassimoFonteMs) { runCatching { fonte.cerca(giorno, mese) }.getOrElse { errore(fonte, it) } }
                        ?: EsitoFonte(emptyList(), Avviso(fonte.nome, TipoAvviso.TIMEOUT, "La fonte non ha risposto entro ${tempoMassimoFonteMs / 1000} secondi"))
                }
            }.map { it.await() }
        }
        val avvisi = esiti.mapNotNull { it.avviso }
        val qualcunoHaRisposto = esiti.any { it.haRisposto }

        if (!qualcunoHaRisposto && memoria != null) {
            val avvisoMemoria = Avviso("Memoria", TipoAvviso.ERRORE, "Le fonti non hanno risposto: vengono mostrati i contenuti memorizzati il ${Date.momentoLeggibile(memoria.consultatoIl)}")
            return memoria.copy(fatti = unisci(daArchivio, memoria.fatti), avvisi = avvisi + avvisoMemoria, daMemoria = true)
        }

        val fattiRete = esiti.flatMap { it.fatti }
        val risultato = RisultatoRicerca(giorno, mese, unisci(daArchivio, fattiRete), avvisi, daMemoria = false, consultatoIl = adesso)
        if (qualcunoHaRisposto) cache.salva(risultato.copy(fatti = risultato.fatti.filterNot { it.id.startsWith("locale-") }, avvisi = emptyList()))
        return risultato.conAvvisoSeVuoto()
    }

    private fun errore(fonte: FonteFatti, e: Throwable) =
        EsitoFonte(emptyList(), Avviso(fonte.nome, TipoAvviso.ERRORE, "Errore della fonte: ${e.message ?: e::class.simpleName}"))

    private fun recente(iso: String): Boolean = runCatching {
        Duration.between(OffsetDateTime.parse(iso).toInstant(), Instant.now(clock)) < validitaMemoria
    }.getOrDefault(false)

    private fun RisultatoRicerca.conAvvisoSeVuoto(): RisultatoRicerca =
        if (fatti.isEmpty()) copy(avvisi = avvisi + Avviso("Ricerca", TipoAvviso.VUOTO, "Nessun risultato verificabile per questa data nelle fonti consultate"))
        else this

    /** Toglie i doppioni, fonde Wikipedia e Wikidata quando parlano della stessa entità, poi ordina. */
    fun unisci(vararg gruppi: List<Fatto>): List<Fatto> {
        val tutti = gruppi.flatMap { it }
        val perEntita = LinkedHashMap<String, Fatto>()
        val senzaEntita = mutableListOf<Fatto>()
        for (f in tutti) {
            val chiave = f.entita?.let { "${it}|${f.collegamento}" }
            if (chiave == null) { senzaEntita += f; continue }
            val presente = perEntita[chiave]
            perEntita[chiave] = if (presente == null) f else fondi(presente, f)
        }
        val visti = HashSet<String>()
        return ordina((perEntita.values + senzaEntita).filter { visti.add(it.id) }.filter { visti.add(Classificatore.chiaveDoppione(it.titolo, it.anno)) })
    }

    private fun fondi(a: Fatto, b: Fatto): Fatto {
        val (principale, secondario) = if (a.verifica.ordinal <= b.verifica.ordinal) a to b else b to a
        val fonti = (principale.fonti + secondario.fonti).distinctBy { it.url }
        val discordanti = principale.anno != null && secondario.anno != null && principale.anno != secondario.anno
        val note = (principale.note + secondario.note + listOf(
            if (discordanti) "Le fonti indicano anni diversi: ${principale.anno} e ${secondario.anno}"
            else "Stesso fatto presente sia in ${principale.origine.substringBefore(',')} sia in ${secondario.origine.substringBefore(',')}",
        )).distinct()
        return principale.copy(
            fonti = fonti,
            note = note,
            verifica = if (discordanti) LivelloVerifica.INCOERENTE else principale.verifica,
        )
    }

    private fun ordina(fatti: List<Fatto>): List<Fatto> = fatti.sortedWith(
        compareBy<Fatto> { it.area.ordinal }
            .thenBy { it.verifica.ordinal }
            .thenBy { it.categoria.ordinal }
            .thenByDescending { it.anno ?: Int.MIN_VALUE },
    )

    companion object {
        val ordineAree = listOf(Area.FVG, Area.ITALIA, Area.MONDO)
    }
}
