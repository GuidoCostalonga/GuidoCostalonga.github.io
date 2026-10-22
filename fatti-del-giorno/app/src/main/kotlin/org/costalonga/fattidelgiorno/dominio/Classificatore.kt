package org.costalonga.fattidelgiorno.dominio

import java.text.Normalizer

/**
 * Assegna area geografica e categoria a un testo quando la fonte non le dichiara.
 * Lavora per parole chiave: è un aiuto, non una certezza, e l'app lo dice.
 */
object Classificatore {

    /** Toponimi del Friuli Venezia Giulia, cercati come parole intere. */
    val toponimiFvg: List<String> = listOf(
        "friuli", "friûl", "friul", "venezia giulia", "trieste", "udine", "pordenone", "gorizia",
        "monfalcone", "aquileia", "cividale", "grado", "lignano", "tolmezzo", "gemona", "sacile",
        "spilimbergo", "maniago", "codroipo", "palmanova", "tarvisio", "muggia", "latisana",
        "san daniele del friuli", "carnia", "tagliamento", "isonzo", "vajont", "roveredo in piano",
        "cormons", "casarsa", "san vito al tagliamento", "azzano decimo", "cordenons", "porcia",
        "fontanafredda", "caneva", "polcenigo", "aviano", "erto", "longarone", "redipuglia",
        "san sabba", "basovizza", "miramare", "duino", "sistiana", "cervignano", "venzone",
        "osoppo", "majano", "fagagna", "tricesimo", "tarcento", "nimis", "attimis", "artegna",
        "sauris", "ampezzo", "forni di sopra", "forni avoltri", "paluzza", "arta terme",
        "giuliano", "giuliani", "friulano", "friulani", "friulana", "friulane", "triestino", "triestina",
        "udinese", "pordenonese", "goriziano", "goriziana", "carnico", "carnica", "bisiaco", "patriarcato di aquileia",
        "regione friuli", "territorio libero di trieste",
    )

    /** Indizi che il testo riguarda l'Italia: parole intere, dopo la normalizzazione. */
    private val indiziItalia: List<String> = listOf(
        "italia", "italiano", "italiana", "italiani", "italiane", "roma", "milano", "napoli", "torino",
        "firenze", "venezia", "bologna", "genova", "palermo", "bari", "cagliari", "verona", "padova",
        "trento", "bolzano", "perugia", "ancona", "l'aquila", "campobasso", "potenza", "catanzaro",
        "reggio calabria", "messina", "catania", "siena", "pisa", "lucca", "parma", "modena", "ravenna",
        "ferrara", "brescia", "bergamo", "como", "varese", "mantova", "cremona", "pavia", "piacenza",
        "treviso", "vicenza", "rovigo", "belluno", "aosta", "cuneo", "asti", "alessandria", "novara",
        "la spezia", "livorno", "arezzo", "grosseto", "viterbo", "rieti", "latina", "frosinone",
        "caserta", "salerno", "avellino", "benevento", "foggia", "lecce", "taranto", "brindisi",
        "sassari", "nuoro", "oristano", "sicilia", "sardegna", "lombardia", "piemonte", "veneto",
        "toscana", "lazio", "campania", "puglia", "calabria", "liguria", "emilia", "romagna", "marche",
        "umbria", "abruzzo", "molise", "basilicata", "trentino", "alto adige", "valle d'aosta",
        "regno d'italia", "repubblica italiana", "vaticano", "papa", "serie a", "giro d'italia",
        "senato della repubblica", "camera dei deputati", "quirinale", "san marino", "garibaldi", "risorgimento",
        "italy", "italian",
    )

    private val indiziCultura: List<String> = listOf(
        "film", "cinema", "album", "disco", "canzone", "concerto", "festival", "premio", "nobel", "oscar",
        "romanzo", "libro", "pubblica", "pubblicato", "pubblicata", "opera", "teatro", "museo", "mostra",
        "scoperta", "scopre", "inventa", "brevetto", "satellite", "sonda", "lancio", "spazio", "astronauta",
        "campionato", "olimpiadi", "olimpici", "mondiale", "mondiali", "finale", "record", "coppa",
        "partita", "calcio", "ciclismo", "formula 1", "gran premio", "torneo", "scudetto", "medaglia",
        "università", "scienziato", "fisico", "chimico", "medicina", "vaccino", "telescopio", "computer",
        "internet", "televisione", "radio", "trasmissione", "esce", "debutta", "prima rappresentazione",
        "anteprima", "sinfonia", "manifesto", "fondazione", "fondata", "inaugurato", "inaugurata", "inaugurazione",
        "trasmesso", "trasmessa", "esposizione", "fumetto", "videogioco", "serie televisiva",
        "released", "premiere", "championship", "olympic", "discover", "discovers", "launches", "launched",
        "nobel prize", "world cup", "album", "film",
    )

    fun normalizza(testo: String): String {
        val senzaAccenti = Normalizer.normalize(testo.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return senzaAccenti.replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun contieneParola(testoNormalizzato: String, parola: String): Boolean {
        val p = normalizza(parola)
        if (p.isEmpty()) return false
        return Regex("(^| )${Regex.escape(p)}( |$)").containsMatchIn(testoNormalizzato)
    }

    fun riguardaFvg(testo: String): Boolean {
        val t = normalizza(testo)
        return toponimiFvg.any { contieneParola(t, it) }
    }

    fun riguardaItalia(testo: String): Boolean {
        val t = normalizza(testo)
        return indiziItalia.any { contieneParola(t, it) }
    }

    fun area(testo: String): Area = when {
        riguardaFvg(testo) -> Area.FVG
        riguardaItalia(testo) -> Area.ITALIA
        else -> Area.MONDO
    }

    /** Un evento di Wikipedia è "storico" salvo che il testo parli di cultura, scienza, sport o società. */
    fun categoriaEvento(testo: String): Categoria {
        val t = normalizza(testo)
        return if (indiziCultura.any { contieneParola(t, it) }) Categoria.CULTURA else Categoria.EVENTO
    }

    /** Chiave di confronto per riconoscere i doppioni: titolo normalizzato più anno. */
    fun chiaveDoppione(titolo: String, anno: Int?): String = normalizza(titolo).take(60) + "|" + (anno ?: "")
}
