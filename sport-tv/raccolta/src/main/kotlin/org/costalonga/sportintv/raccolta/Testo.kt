package org.costalonga.sportintv.raccolta

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Strumenti sui testi delle fonti: semplificazione, partecipanti, tipo di trasmissione. */
object Testo {

    private val accenti = Regex("\\p{M}+")
    private val nonAlfanumerico = Regex("[^a-z0-9']+")
    private val apostrofi = Regex("[’`´]")

    /** Minuscole, senza accenti né punteggiatura: "Élite: Uomini" diventa "elite uomini". */
    fun semplifica(testo: String): String {
        val senzaAccenti = accenti.replace(Normalizer.normalize(testo, Normalizer.Form.NFD), "")
        return nonAlfanumerico.replace(apostrofi.replace(senzaAccenti.lowercase(Locale.ROOT), "'"), " ").trim()
    }

    // ---------------------------------------------------------------- rubriche

    /**
     * Notiziari, rubriche e programmi di approfondimento: non sono eventi
     * sportivi e restano fuori dalla guida.
     */
    private val rubriche = Regex(
        "\\b(tg|tgr|telegiornale|rubrica|speciale|memory|tender|magazine|notiziario|edizione|talk|studio|" +
            "approfondimento|highlights|sintesi|il meglio|anteprima|pre ?gara|post ?gara|pre ?partita|post ?partita|" +
            "prepartita|dopopartita|domenica sportiva|dribbling|90 ?minuto|novantesimo|diretta azzurra|" +
            "sport ?mediaset|monday night|pressing|tiki taka|circolando|break time|tie break|colpo da campione|" +
            "tennis talk|sportabilia|linea bianca|radiocorsa|processo alla tappa|buongiorno serie a|focus serie a|" +
            "la domenica della serie a|stories|storie|documentario|replay story|giro all'arrivo|giro mattina|" +
            "tg sport|sabato sport|domenica sport|calcio totale|il grande match|zona gol|euro ?news|meteo|bordocampo|bordo campo|" +
            "rosa del calcio|imperdibili|reparto corse|al 90|serie a live|college gameday|big noon kickoff|kickoff show|" +
            "conferenza stampa|sorteggio|presentazione)\\b"
    )

    fun eRubrica(vararg testi: String?): Boolean =
        testi.any { t -> t != null && rubriche.containsMatchIn(semplifica(t)) }

    // ---------------------------------------------------------- tipo di messa in onda

    private val reReplica = Regex("\\b(replica|repliche)\\b")
    private val reDifferita = Regex("\\b(differita|registrat[ao])\\b")
    private val reDiretta = Regex("\\b(diretta|live|in diretta)\\b")

    /**
     * Il tipo di messa in onda si deduce solo da parole esplicite nel titolo o
     * nella descrizione. Senza parole esplicite resta "non indicato".
     */
    fun tipoDaTesto(vararg testi: String?): TipoTrasmissione {
        val t = testi.filterNotNull().joinToString(" ") { it.lowercase(Locale.ROOT) }
        val s = semplifica(t)
        return when {
            reReplica.containsMatchIn(s) || t.contains("(r)") -> TipoTrasmissione.REPLICA
            reDifferita.containsMatchIn(s) -> TipoTrasmissione.DIFFERITA
            reDiretta.containsMatchIn(s) -> TipoTrasmissione.DIRETTA
            else -> TipoTrasmissione.NON_INDICATO
        }
    }

    // ------------------------------------------------------------ partecipanti

    private val dataInCoda = Regex("\\s*[-–]?\\s*\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}\\s*$")
    private val separatoreSfida = Regex("\\s+(?:-|–|vs\\.?|v\\.?|contro)\\s+", RegexOption.IGNORE_CASE)
    private val separatoreTrattinoStretto = Regex("(?<=[A-Za-zÀ-ÿ.])-(?=[A-Z])")
    private val separatoreTrasferta = Regex("\\s+@\\s+")
    private val siglaNazione = Regex("\\s*\\((?:[A-Z]{3})\\)")
    private val paroleNonSquadra = setOf(
        "uomini", "donne", "maschile", "femminile", "elite", "prima parte", "seconda parte", "terza parte",
        "qualifiche", "gara", "finale", "semifinale", "uomini elite", "donne elite",
    )

    /**
     * Estrae i due partecipanti da testi come "Roma - Lazio", "Sinner vs Alcaraz",
     * "Colts @ Commanders" (in trasferta: l'ordine diventa casa, ospite).
     * Restituisce una lista vuota se il testo non ha la forma di una sfida.
     */
    fun partecipanti(testo: String?, trattinoStretto: Boolean = false, atleti: Boolean = false): List<String> {
        if (testo.isNullOrBlank()) return emptyList()
        var t = dataInCoda.replace(testo.trim(), "").trim()
        t = siglaNazione.replace(t, "")
        val trasferta = separatoreTrasferta.split(t)
        val sfida = separatoreSfida.split(t)
        val parti = when {
            trasferta.size == 2 -> listOf(trasferta[1], trasferta[0])
            sfida.size == 2 -> sfida
            // "Qualificazioni Mondiali femminili 2027 - Bielorussia - Italia": gli ultimi due sono i partecipanti.
            sfida.size >= 3 && sfida.takeLast(2).none { p -> p.any { it.isDigit() } } &&
                sfida.dropLast(2).any { p -> p.any { it.isDigit() } } -> sfida.takeLast(2)
            else -> separatoreTrattinoStretto.split(t).takeIf { trattinoStretto && it.size == 2 } ?: return emptyList()
        }
        val pulite = parti.map { it.trim().trim('.', ',', ';').trim() }
        if (pulite.any { it.length < 2 || it.length > 60 }) return emptyList()
        if (pulite.any { semplifica(it) in paroleNonSquadra }) return emptyList()
        if (pulite.any { semplifica(it) in nomiSport }) return emptyList()
        return pulite.map { nomeLeggibile(it, atleti) }
    }

    private val nomiSport: Set<String> by lazy { Sport.entries.map { semplifica(it.nome) }.toSet() + setOf("calcio", "basket", "volley", "tennis", "ciclismo") }

    /**
     * "Jannik SINNER" diventa "Jannik Sinner"; le sigle brevi (USA, PSG, AC)
     * restano. Per gli [atleti] anche i cognomi brevi in maiuscolo ("Ann LI")
     * vengono riscritti, se il nome contiene già lettere minuscole.
     */
    fun nomeLeggibile(nome: String, atleti: Boolean = false): String {
        val misto = nome.any { it.isLowerCase() }
        return nome.split(' ').filter { it.isNotBlank() }.joinToString(" ") { parola ->
            val lettere = parola.filter { it.isLetter() }
            val maiuscolo = lettere.isNotEmpty() && lettere == lettere.uppercase(Locale.ITALIAN)
            if (maiuscolo && (lettere.length > 3 || (atleti && misto && lettere.length >= 2))) {
                parola.lowercase(Locale.ITALIAN).split('-').joinToString("-") { p -> p.replaceFirstChar { it.titlecase(Locale.ITALIAN) } }
            } else parola
        }
    }

    private val sigleSocietarie = Regex("^(?:(?:fc|ac|as|ss|us|ssc|acf|cfc|afc|bc|sc|uc|rc)\\s+)+|(?:\\s+(?:fc|ac|cfc|bc|calcio|\\d{4}))+$", RegexOption.IGNORE_CASE)

    /** "US Sassuolo Calcio" diventa "Sassuolo", "Bologna FC 1909" diventa "Bologna". */
    fun nomeBreveSquadra(nome: String): String = sigleSocietarie.replace(nome.trim(), "").trim().ifEmpty { nome }

    // ------------------------------------------------- confronto fra partecipanti

    private val paroleSocietarie = setOf(
        "fc", "ac", "as", "ss", "us", "ssc", "acf", "cfc", "bc", "sc", "afc", "cf", "ud", "rc", "sd", "ca", "cd",
        "calcio", "club", "football", "futbol", "sport", "sporting", "1907", "1909", "1913", "1919", "1899", "1900",
        "women", "woman", "femminile", "donne", "ladies", "w", "fem", "the", "de", "di", "del", "della", "u19", "u21",
    )

    /** Nomi diversi della stessa squadra nelle varie fonti. */
    private val alias = mapOf(
        "internazionale" to "inter", "barcellona" to "barcelona", "siviglia" to "sevilla",
        "monaco di baviera" to "bayern", "stella rossa" to "crvena zvezda",
    )

    /** Parole significative di un nome di squadra o atleta. */
    fun gettoni(nome: String): Set<String> {
        var s = semplifica(nome)
        alias.forEach { (da, a) -> s = s.replace(Regex("\\b$da\\b"), a) }
        return s.split(' ').filter { it.isNotBlank() && it !in paroleSocietarie }.toSet()
    }

    /** Vero se due nomi indicano verosimilmente la stessa squadra o lo stesso atleta. */
    fun stessoPartecipante(a: String, b: String): Boolean {
        val ga = gettoni(a)
        val gb = gettoni(b)
        if (ga.isEmpty() || gb.isEmpty()) return semplifica(a) == semplifica(b)
        if (ga.containsAll(gb) || gb.containsAll(ga)) return true
        val comuni = ga.intersect(gb).size
        return comuni > 0 && comuni.toDouble() / ga.union(gb).size >= 0.5
    }

    /** Stesso incontro: i due partecipanti coincidono, in qualunque ordine. */
    fun stessaSfida(a: List<String>, b: List<String>): Boolean {
        if (a.size != 2 || b.size != 2) return false
        return (stessoPartecipante(a[0], b[0]) && stessoPartecipante(a[1], b[1])) ||
            (stessoPartecipante(a[0], b[1]) && stessoPartecipante(a[1], b[0]))
    }

    /** Somiglianza fra due titoli senza partecipanti (indice di Jaccard sulle parole). */
    fun somiglianza(a: String, b: String): Double {
        val ga = semplifica(a).split(' ').filter { it.length > 2 }.toSet()
        val gb = semplifica(b).split(' ').filter { it.length > 2 }.toSet()
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        return ga.intersect(gb).size.toDouble() / ga.union(gb).size
    }

    private val reFemminile = Regex("\\b(women|woman|femminil[ei]|donne|ladies|wta|female|girls|ragazze)\\b")

    /** "f" per le competizioni femminili, "m" altrimenti: tiene separate le partite omonime. */
    fun genere(vararg testi: String?): String =
        if (testi.any { it != null && reFemminile.containsMatchIn(semplifica(it)) }) "f" else "m"

    private val anno = Regex("\\b(19[5-9]\\d|20\\d\\d)\\b")

    /**
     * Vero se il testo cita solo annate passate ("ATP 1000 Shanghai 2025"
     * trasmesso nel 2026): non può trattarsi di una diretta.
     */
    fun soloAnniPassati(testo: String, annoCorrente: Int): Boolean {
        val anni = anno.findAll(testo).map { it.value.toInt() }.toList()
        // "2026-2027" e "2026/27" indicano la stagione in corso.
        return anni.isNotEmpty() && anni.all { it < annoCorrente } && !testo.contains("${annoCorrente - 1}-${annoCorrente}") &&
            !testo.contains("${annoCorrente - 1}/${annoCorrente % 100}") && !testo.contains("${annoCorrente - 1}-${annoCorrente % 100}")
    }

    fun impronta(testo: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(testo.toByteArray(Charsets.UTF_8))
        return md.take(8).joinToString("") { "%02x".format(it) }
    }
}
