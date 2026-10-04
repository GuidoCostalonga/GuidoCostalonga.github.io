package org.costalonga.sportintv.dati

import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.ROMA
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.Testo
import org.costalonga.sportintv.raccolta.TipoTrasmissione
import org.costalonga.sportintv.raccolta.Trasmissione
import java.time.Instant
import java.time.LocalDate

enum class FiltroAccesso(val etichetta: String) { TUTTI("Gratis e a pagamento"), GRATUITI("Solo gratis"), A_PAGAMENTO("Solo a pagamento") }

data class Filtri(
    /** null = tutti i giorni. */
    val giorno: LocalDate? = null,
    val testo: String = "",
    val sport: Set<String> = emptySet(),
    val competizioni: Set<String> = emptySet(),
    val canali: Set<String> = emptySet(),
    val piattaforme: Set<String> = emptySet(),
    val accesso: FiltroAccesso = FiltroAccesso.TUTTI,
    val soloDirette: Boolean = false,
    val soloPreferiti: Boolean = false,
    /** Di norma gli eventi già finiti si nascondono. */
    val mostraConclusi: Boolean = false,
) {
    val attivi: Int
        get() = listOf(sport.isNotEmpty(), competizioni.isNotEmpty(), canali.isNotEmpty(), piattaforme.isNotEmpty(),
            accesso != FiltroAccesso.TUTTI, soloDirette, soloPreferiti, testo.isNotBlank()).count { it }
}

/** "DAZN · streaming" diventa "DAZN", "Rai · digitale terrestre e RaiPlay" diventa "Rai". */
val Trasmissione.gruppoPiattaforma: String get() = piattaforma.substringBefore(" ·").trim()

fun Evento.giorno(): LocalDate = inizio.atZone(ROMA).toLocalDate()

/**
 * "In corso" solo quando gli orari lo rendono attendibile: esiste una
 * trasmissione in diretta, con inizio e fine dati dalla fonte, e adesso è
 * dentro quell'intervallo.
 */
fun Evento.inCorso(adesso: Instant): Boolean = trasmissioni.any {
    it.tipo == TipoTrasmissione.DIRETTA && it.fine != null && !adesso.isBefore(it.inizio) && adesso.isBefore(it.fine)
}

/** In onda adesso, ma non dichiarato come diretta (tipo non indicato o replica). */
fun Evento.inOnda(adesso: Instant): Boolean = !inCorso(adesso) && trasmissioni.any {
    it.fine != null && !adesso.isBefore(it.inizio) && adesso.isBefore(it.fine)
}

/** Evento finito: tutte le trasmissioni con fine nota sono terminate, oppure è passato da più di 4 ore. */
fun Evento.concluso(adesso: Instant): Boolean {
    val fineNota = (trasmissioni.mapNotNull { it.fine } + listOfNotNull(fine)).maxOrNull()
    return if (fineNota != null) !adesso.isBefore(fineNota) else adesso.isAfter(inizio.plusSeconds(4 * 3600))
}

object Preferiti {

    private val sportIndividuali = setOf(
        Sport.TENNIS, Sport.PADEL, Sport.TENNIS_TAVOLO, Sport.PUGILATO, Sport.ARTI_MARZIALI, Sport.SCHERMA,
        Sport.GOLF, Sport.ATLETICA, Sport.NUOTO, Sport.GINNASTICA, Sport.FRECCETTE, Sport.BILIARDO,
    ).map { it.chiave }.toSet()

    /** I partecipanti degli sport individuali sono atleti, gli altri squadre. */
    fun tipoPartecipante(e: Evento): TipoSeguito = if (e.sport in sportIndividuali) TipoSeguito.ATLETA else TipoSeguito.SQUADRA

    /** Vero se l'evento riguarda qualcosa che l'utente segue. */
    fun corrisponde(e: Evento, seguiti: List<Seguito>): Boolean = seguiti.any { corrisponde(e, it) }

    fun corrisponde(e: Evento, s: Seguito): Boolean = when (s.tipo) {
        TipoSeguito.COMPETIZIONE -> e.competizione?.let { stessaCompetizione(it, s.nome) } ?: false
        TipoSeguito.SQUADRA, TipoSeguito.ATLETA ->
            e.partecipanti.any { Testo.stessoPartecipante(it, s.nome) } ||
                // Atleti senza sfida diretta: il nome può comparire nel titolo ("Gara di Sinner").
                (e.partecipanti.isEmpty() && Testo.gettoni(s.nome).let { g -> g.isNotEmpty() && Testo.gettoni(e.titolo).containsAll(g) })
    }

    /** "Serie A" segue "Serie A Enilive" ma non "Serie A Women" né "Serie A2". */
    fun stessaCompetizione(competizione: String, seguita: String): Boolean {
        val c = Testo.semplifica(competizione)
        val s = Testo.semplifica(seguita)
        if (c == s) return true
        if (!c.startsWith("$s ")) return false
        val resto = c.removePrefix("$s ").split(' ').first()
        return resto !in setOf("women", "femminile", "2", "b", "c", "u21", "u19", "primavera") && !resto.first().isDigit()
    }
}

/** Applica i filtri combinati. La ricerca guarda squadre, atleti, competizione, titolo, sport e canali. */
fun List<Evento>.filtra(f: Filtri, seguiti: List<Seguito>, salvati: Set<String>, adesso: Instant? = null): List<Evento> {
    val cerca = Testo.semplifica(f.testo)
    return filter { e ->
        (f.mostraConclusi || adesso == null || !e.concluso(adesso)) &&
            (f.giorno == null || e.giorno() == f.giorno) &&
            (f.sport.isEmpty() || e.sport in f.sport) &&
            (f.competizioni.isEmpty() || e.competizione in f.competizioni) &&
            (f.canali.isEmpty() || e.trasmissioni.any { it.canale in f.canali }) &&
            (f.piattaforme.isEmpty() || e.trasmissioni.any { it.gruppoPiattaforma in f.piattaforme }) &&
            when (f.accesso) {
                FiltroAccesso.TUTTI -> true
                FiltroAccesso.GRATUITI -> e.trasmissioni.any { it.accesso.gratuito }
                FiltroAccesso.A_PAGAMENTO -> e.trasmissioni.any { it.accesso.aPagamento }
            } &&
            (!f.soloDirette || e.trasmissioni.any { it.tipo == TipoTrasmissione.DIRETTA }) &&
            (!f.soloPreferiti || e.id in salvati || Preferiti.corrisponde(e, seguiti)) &&
            (cerca.isEmpty() || testoRicerca(e).contains(cerca))
    }
}

private fun testoRicerca(e: Evento): String = Testo.semplifica(
    buildString {
        append(e.titolo).append(' ')
        e.partecipanti.forEach { append(it).append(' ') }
        append(e.competizione.orEmpty()).append(' ')
        append(Sport.daChiave(e.sport).nome).append(' ')
        e.trasmissioni.forEach { append(it.canale).append(' ').append(it.titoloOriginale).append(' ') }
    }
)
