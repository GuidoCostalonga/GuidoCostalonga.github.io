package org.costalonga.sportintv.raccolta.connettori

import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Connettore
import org.costalonga.sportintv.raccolta.Elemento
import org.costalonga.sportintv.raccolta.Finestra
import org.costalonga.sportintv.raccolta.Http
import org.costalonga.sportintv.raccolta.RisultatoFonte
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.Testo
import org.costalonga.sportintv.raccolta.TipoFonte
import org.costalonga.sportintv.raccolta.TipoTrasmissione
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Palinsesto ufficiale di SuperTennis (canale 64 del digitale terrestre),
 * dalla pagina https://www.supertennis.tv/On-Demand/Palinsesto. La pagina
 * riporta gli orari in UTC (il sito li converte nel fuso del visitatore con
 * la funzione convertUtcDateToLocalDate). Copre circa una settimana.
 */
class SuperTennis : Connettore {
    override val id = "supertennis"
    override val nome = "SuperTennis · palinsesto"
    override val tipo = TipoFonte.PALINSESTO
    override val url = PAGINA
    override val copertura = "Incontri di tennis e padel trasmessi da SuperTennis"
    override val canali = listOf("SuperTennis")
    override val condizioni = "Pagina pubblica del palinsesto, letta come la legge un browser, senza autenticazione. " +
        "Struttura non garantita: può cambiare senza preavviso. Letta poche volte al giorno, con rimando alla pagina ufficiale."

    companion object {
        const val PAGINA = "https://www.supertennis.tv/On-Demand/Palinsesto"
        const val DIRETTA = "https://www.supertennis.tv/live-streaming"

        private val evento = Regex("<div class=\"cc-event\">(.*?)<!-- End Evento -->", RegexOption.DOT_MATCHES_ALL)
        private val orari = Regex("data-time-start=\"([^\"]+)\" data-time-end=\"([^\"]+)\"")
        private val testi = Regex("<p class=\"cc-title\">\\s*(.*?)\\s*</p>\\s*<p class=\"cc-text\">\\s*(.*?)\\s*</p>", RegexOption.DOT_MATCHES_ALL)
        private val formato = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a", Locale.US)

        fun istante(testo: String): Instant = LocalDateTime.parse(testo.trim(), formato).toInstant(ZoneOffset.UTC)

        private fun pulisci(html: String) = html.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace(' ', ' ')
            .replace(Regex("\\s+"), " ").trim()

        fun leggi(html: String, verificato: Instant): List<Elemento> =
            evento.findAll(html).mapNotNull { m ->
                val blocco = m.groupValues[1]
                val (inizioTesto, fineTesto) = orari.find(blocco)?.destructured ?: return@mapNotNull null
                val (titoloHtml, testoHtml) = testi.find(blocco)?.destructured ?: return@mapNotNull null
                val titolo = pulisci(titoloHtml)
                val dettaglio = pulisci(testoHtml)
                // Solo gli incontri: "Jannik SINNER (ITA) vs Alex DE MINAUR (AUS)".
                val partecipanti = Testo.partecipanti(dettaglio, atleti = true)
                if (partecipanti.isEmpty()) return@mapNotNull null
                val competizione = titolo.replace(Regex("(?i)^live\\s+"), "").replace(Regex("(?i)\\s*\\(replica\\)"), "").trim()
                val tipo = when {
                    titolo.contains("(replica)", ignoreCase = true) -> TipoTrasmissione.REPLICA
                    titolo.startsWith("LIVE", ignoreCase = true) -> TipoTrasmissione.DIRETTA
                    else -> TipoTrasmissione.NON_INDICATO
                }
                Elemento(
                    fonte = "supertennis",
                    sport = Sport.riconosci(competizione, "tennis").let { if (it == Sport.PADEL) it else Sport.TENNIS },
                    genere = Testo.genere(competizione),
                    competizione = competizione,
                    titolo = partecipanti.joinToString(" - "),
                    partecipanti = partecipanti,
                    inizio = istante(inizioTesto),
                    fine = istante(fineTesto),
                    verificato = verificato,
                    canale = "SuperTennis",
                    piattaforma = "SuperTennis · canale 64 del digitale terrestre",
                    tipo = tipo,
                    accesso = Accesso.IN_CHIARO,
                    link = if (tipo == TipoTrasmissione.DIRETTA) DIRETTA else PAGINA,
                    titoloOriginale = "$titolo · $dettaglio",
                )
            }.toList()
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte {
        val html = http.testo(PAGINA)
        val elementi = leggi(html, Instant.now()).filter { finestra.contiene(it.inizio) }
        return RisultatoFonte(elementi, riuscite = 1, fallite = 0)
    }
}
