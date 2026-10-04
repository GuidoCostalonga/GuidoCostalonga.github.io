package org.costalonga.sportintv.raccolta

import java.time.Duration
import java.time.format.DateTimeFormatter

/**
 * Unisce gli elementi delle varie fonti in eventi.
 *
 * Due messe in onda appartengono allo stesso evento quando:
 * - sono una sfida fra gli stessi due partecipanti (in qualunque ordine), dello
 *   stesso sport e dello stesso genere, e cominciano a meno di 4 ore l'una
 *   dall'altra;
 * - oppure non hanno partecipanti, hanno titoli molto simili e cominciano a
 *   meno di 45 minuti l'una dall'altra.
 *
 * Le repliche lontane nel tempo restano eventi a sé, con il loro orario: così
 * il filtro "solo dirette" e l'ordinamento per orario restano affidabili.
 * Ogni singola trasmissione viene conservata con la sua fonte.
 */
object Unione {

    private val FINESTRA_SFIDA: Duration = Duration.ofHours(4)
    private val FINESTRA_TITOLO: Duration = Duration.ofMinutes(45)
    private val oraRoma = DateTimeFormatter.ofPattern("HH:mm").withZone(ROMA)
    private val giornoRoma = DateTimeFormatter.ofPattern("d/M HH:mm").withZone(ROMA)

    /** Ordine di preferenza per nomi e competizioni quando le fonti differiscono. */
    private val prioritaNomi = listOf("dazn", "raiplay", "mediaset", "supertennis", "openfootball", "jolpica")

    private class Gruppo(primo: Elemento) {
        val elementi = mutableListOf(primo)
        val riferimento: Elemento get() = elementi.first()
        val calendario = mutableListOf<Elemento>()

        fun compatibile(e: Elemento): Boolean {
            val r = riferimento
            if (r.genere != e.genere) return false
            if (r.sport != e.sport && r.sport != Sport.ALTRO && e.sport != Sport.ALTRO) return false
            val distanza = Duration.between(r.inizio, e.inizio).abs()
            return when {
                r.partecipanti.size == 2 && e.partecipanti.size == 2 ->
                    distanza <= FINESTRA_SFIDA && Testo.stessaSfida(r.partecipanti, e.partecipanti)
                r.partecipanti.isEmpty() && e.partecipanti.isEmpty() ->
                    distanza <= FINESTRA_TITOLO && Testo.somiglianza(r.titolo, e.titolo) >= 0.6
                else -> false
            }
        }
    }

    fun unisci(elementi: List<Elemento>, finestra: Finestra? = null): List<Evento> {
        val gruppi = mutableListOf<Gruppo>()
        for (e in elementi.filter { !it.calendario }.sortedWith(compareBy({ it.inizio }, { prioritaNomi.indexOf(it.fonte) }))) {
            gruppi.firstOrNull { it.compatibile(e) }?.elementi?.add(e) ?: gruppi.add(Gruppo(e))
        }
        for (c in elementi.filter { it.calendario }.sortedBy { it.inizio }) {
            val g = gruppi.firstOrNull { it.calendario.isEmpty() && it.compatibile(c) }
            if (g != null) g.calendario += c else gruppi.add(Gruppo(c).also { it.elementi.clear(); it.calendario += c })
        }
        val eventi = gruppi.map { componi(it) }
            .filter { finestra == null || finestra.contiene(it.inizio) || it.trasmissioni.any { t -> finestra.contiene(t.inizio) } }
            .sortedWith(compareBy({ it.inizio }, { it.titolo }))
        return assegnaIdentificativi(eventi)
    }

    private fun Gruppo.calendarioOppureRiferimento(): Elemento = calendario.firstOrNull() ?: elementi.first()

    private fun componi(g: Gruppo): Evento {
        val tutti = g.elementi + g.calendario
        val perPriorita = tutti.sortedBy { prioritaNomi.indexOf(it.fonte).let { i -> if (i < 0) 99 else i } }
        val cal = g.calendario.firstOrNull()

        val trasmissioni = g.elementi
            .distinctBy { Triple(it.canale, it.inizio, it.tipo) }
            .sortedWith(compareBy({ it.inizio }, { it.canale }))
            .map {
                Trasmissione(
                    canale = it.canale!!, piattaforma = it.piattaforma!!, tipo = it.tipo, accesso = it.accesso,
                    link = it.link!!, inizio = it.inizio, fine = it.fine, fonte = it.fonte, verificato = it.verificato,
                    titoloOriginale = it.titoloOriginale, nota = it.nota,
                )
            }

        val sport = tutti.map { it.sport }.filter { it != Sport.ALTRO }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: Sport.ALTRO
        val partecipanti = perPriorita.firstOrNull { it.partecipanti.size == 2 }?.partecipanti ?: emptyList()
        val competizione = (listOfNotNull(cal) + perPriorita).firstNotNullOfOrNull { it.competizione }
        val titolo = if (partecipanti.size == 2) partecipanti.joinToString(" - ") else (cal ?: perPriorita.first()).titolo

        val principali = trasmissioni.filter { it.tipo == TipoTrasmissione.DIRETTA || it.tipo == TipoTrasmissione.NON_INDICATO }
            .ifEmpty { trasmissioni }
        val inizio = cal?.inizio ?: principali.minOf { it.inizio }
        val fine = principali.filter { it.tipo != TipoTrasmissione.REPLICA }.mapNotNull { it.fine }.maxOrNull()
            ?.takeIf { it.isAfter(inizio) }

        val incertezze = mutableListOf<String>()
        if (cal != null) {
            for (t in principali) {
                // Una diretta può cominciare fino a un'ora prima (studio, prepartita) ma non dopo il calcio d'inizio.
                val anticipo = Duration.between(t.inizio, cal.inizio)
                if (anticipo.isNegative && anticipo.abs() > Duration.ofMinutes(10) || anticipo > Duration.ofMinutes(75)) {
                    incertezze += "Orari diversi: il calendario indica le ${oraRoma.format(cal.inizio)}, " +
                        "il palinsesto di ${t.canale} le ${oraRoma.format(t.inizio)}."
                }
            }
        } else {
            val dirette = principali.filter { it.tipo == TipoTrasmissione.DIRETTA }
            if (dirette.size >= 2) {
                val primo = dirette.minBy { it.inizio }
                val ultimo = dirette.maxBy { it.inizio }
                if (Duration.between(primo.inizio, ultimo.inizio) > Duration.ofMinutes(75)) {
                    incertezze += "Orari diversi fra le fonti: ${primo.canale} alle ${giornoRoma.format(primo.inizio)}, " +
                        "${ultimo.canale} alle ${giornoRoma.format(ultimo.inizio)}."
                }
            }
        }
        val sportDiversi = g.elementi.map { it.sport }.filter { it != Sport.ALTRO }.toSet()
        if (sportDiversi.size > 1) incertezze += "Le fonti classificano l'evento in discipline diverse: ${sportDiversi.joinToString { it.nome }}."

        val statoFonte = tutti.mapNotNull { it.stato }
        val stato = when {
            StatoEvento.ANNULLATO in statoFonte -> StatoEvento.ANNULLATO
            StatoEvento.RINVIATO in statoFonte -> StatoEvento.RINVIATO
            trasmissioni.isNotEmpty() -> StatoEvento.CONFERMATO
            else -> StatoEvento.DA_CONFERMARE
        }
        val nota = listOfNotNull(
            cal?.notaEvento,
            if (trasmissioni.isEmpty()) cal?.notaSeNonTrasmesso else null,
        ).joinToString(". ").ifEmpty { null }

        return Evento(
            id = "",
            sport = sport.chiave,
            genere = g.calendarioOppureRiferimento().genere,
            competizione = competizione,
            titolo = titolo,
            partecipanti = partecipanti,
            inizio = inizio,
            fine = fine,
            stato = stato,
            trasmissioni = trasmissioni,
            fonti = tutti.map { it.fonte }.distinct(),
            incertezze = incertezze,
            linkUfficiale = cal?.linkUfficiale ?: g.elementi.firstNotNullOfOrNull { it.linkUfficiale },
            nota = nota,
            inizioCalendario = cal?.inizio,
            fonteCalendario = cal?.fonte,
        )
    }

    /**
     * Identificativo stabile fra un aggiornamento e l'altro: sport, genere,
     * partecipanti (o titolo) e giorno in ora italiana. Se nello stesso giorno
     * ci sono due eventi con la stessa chiave (una diretta e una replica), il
     * secondo riceve un numero progressivo.
     */
    private fun assegnaIdentificativi(eventi: List<Evento>): List<Evento> {
        val usati = mutableMapOf<String, Int>()
        return eventi.map { e ->
            val base = chiaveBase(e)
            val n = usati.merge(base, 1, Int::plus)!! - 1
            e.copy(id = "ev_" + Testo.impronta(if (n == 0) base else "$base#$n"))
        }
    }

    fun chiaveBase(e: Evento): String {
        val chi = if (e.partecipanti.size == 2) {
            e.partecipanti.map { Testo.gettoni(it).sorted().joinToString(" ") }.sorted().joinToString("|")
        } else {
            Testo.semplifica(e.titolo).replace(Regex("\\d+"), "").replace(Regex("\\s+"), " ").trim()
        }
        val giorno = e.inizio.atZone(ROMA).toLocalDate()
        return "${e.sport}|${e.genere}|$chi|$giorno"
    }

    /**
     * Ricostruisce gli elementi grezzi di una fonte da un pacchetto precedente:
     * servono a conservare i dati di una fonte che in questo giro non ha risposto.
     */
    fun elementiDi(fonte: String, pacchetto: Pacchetto): List<Elemento> = pacchetto.eventi.flatMap { e ->
        val sport = Sport.daChiave(e.sport)
        val daTrasmissioni = e.trasmissioni.filter { it.fonte == fonte }.map { t ->
            Elemento(
                fonte = fonte, sport = sport, genere = e.genere, competizione = e.competizione, titolo = e.titolo,
                partecipanti = e.partecipanti, inizio = t.inizio, fine = t.fine, verificato = t.verificato,
                canale = t.canale, piattaforma = t.piattaforma, tipo = t.tipo, accesso = t.accesso, link = t.link,
                titoloOriginale = t.titoloOriginale, nota = t.nota,
                stato = e.stato.takeIf { it == StatoEvento.RINVIATO || it == StatoEvento.ANNULLATO },
            )
        }
        val daCalendario = if (e.fonteCalendario == fonte) listOf(
            Elemento(
                fonte = fonte, sport = sport, genere = e.genere, competizione = e.competizione, titolo = e.titolo,
                partecipanti = e.partecipanti, inizio = e.inizioCalendario ?: e.inizio, fine = null,
                verificato = pacchetto.generato, linkUfficiale = e.linkUfficiale,
                notaEvento = e.nota?.substringBefore(". Indicazione generale")?.takeIf { !it.startsWith("Indicazione generale") },
                notaSeNonTrasmesso = if (fonte == "openfootball") org.costalonga.sportintv.raccolta.connettori.OpenFootballSerieA.NOTA_DIRITTI else null,
            )
        ) else emptyList()
        daTrasmissioni + daCalendario
    }
}
