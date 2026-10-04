package org.costalonga.sportintv.raccolta

/**
 * Le discipline riconosciute. L'ordine conta: le voci più specifiche vengono
 * prima (calcio a 5 prima di calcio, pallanuoto prima di nuoto).
 */
enum class Sport(val chiave: String, val nome: String, private val parole: List<String>) {
    CALCIO_A_5("calcio_a_5", "Calcio a 5", listOf("calcio a 5", "futsal", "calcio a cinque")),
    BEACH_VOLLEY("beach_volley", "Beach volley", listOf("beach volley", "beachvolley")),
    PALLANUOTO("pallanuoto", "Pallanuoto", listOf("pallanuoto", "waterpolo", "water polo")),
    FOOTBALL_AMERICANO("football_americano", "Football americano", listOf("football americano", "nfl", "ncaa football", "american football", "super bowl")),
    CALCIO("calcio", "Calcio", listOf("calcio", "serie a", "serie b", "serie c", "champions league", "europa league", "conference league", "coppa italia", "nations league", "premier league", "liga ", "laliga", "bundesliga", "ligue 1", "mondiali di calcio", "soccer", "football")),
    BASKET("basket", "Basket", listOf("basket", "pallacanestro", "nba", "eurolega", "euroleague", "eurocup", "liga endesa", "liga acb", "betclic elite", "wnba")),
    VOLLEY("volley", "Pallavolo", listOf("volley", "pallavolo", "superlega", "lvf ", "cev ")),
    PADEL("padel", "Padel", listOf("padel", "premier padel")),
    TENNIS_TAVOLO("tennis_tavolo", "Tennis tavolo", listOf("tennis tavolo", "tennistavolo", "table tennis", "ping pong", "mltt")),
    TENNIS("tennis", "Tennis", listOf("tennis", "atp", "wta", "davis cup", "coppa davis", "bjk cup", "billie jean king", "laver cup", "us open", "roland garros", "wimbledon", "australian open", "internazionali d'italia")),
    CICLISMO("ciclismo", "Ciclismo", listOf("ciclismo", "giro d'italia", "tour de france", "vuelta", "il lombardia", "milano-sanremo", "parigi-roubaix", "ciclocross", "mountain bike", "mtb", "eroica", "gran fondo", "granfondo", "tre valli", "coppa agostoni", "giro dell'emilia", "uci")),
    IPPICA("ippica", "Ippica", listOf("ippica", "galoppo", "trotto", "arc de triomphe", "gran premio lotteria")),
    MOTORI("motori", "Motori", listOf("formula 1", "formula1", "f1 ", "gran premio", "grand prix", "motogp", "moto2", "moto3", "superbike", "sbk", "rally", "wrc", "formula e", "indycar", "nascar", "motocross", "mxgp", "endurance", "wec", "dtm", "tcr", "truck racing", "motori", "automobilismo", "motociclismo", "powerboat", "karting", "le mans")),
    ATLETICA("atletica", "Atletica e corsa", listOf("atletica", "maratona", "diamond league", "mezza maratona", "corsa campestre", "corsa in montagna", "corsa su strada", "trail", "skyrunning", "cross ")),
    TRIATHLON("triathlon", "Triathlon", listOf("triathlon", "duathlon", "ironman")),
    NUOTO("nuoto", "Nuoto e tuffi", listOf("nuoto", "tuffi", "swimming", "nuoto artistico", "sincronizzato")),
    SPORT_INVERNALI("sport_invernali", "Sport invernali", listOf("sci ", "skiroll", "sci alpino", "sci nordico", "slalom", "discesa libera", "superg", "super g", "biathlon", "snowboard", "salto con gli sci", "combinata nordica", "fondo ", "bob ", "slittino", "skeleton", "curling", "coppa del mondo di sci")),
    PATTINAGGIO("pattinaggio", "Pattinaggio", listOf("pattinaggio", "short track", "figure skating")),
    HOCKEY("hockey", "Hockey", listOf("hockey", "nhl")),
    RUGBY("rugby", "Rugby", listOf("rugby", "sei nazioni", "six nations", "urc", "top 10")),
    BASEBALL("baseball", "Baseball e softball", listOf("baseball", "softball", "mlb")),
    GOLF("golf", "Golf", listOf("golf", "pga", "dp world tour", "ryder cup", "lpga")),
    PUGILATO("pugilato", "Pugilato", listOf("pugilato", "boxe", "boxing")),
    ARTI_MARZIALI("arti_marziali", "Arti marziali e lotta", listOf("judo", "karate", "taekwondo", "lotta ", "mma", "ufc", "kickboxing", "muay thai", "wrestling")),
    SCHERMA("scherma", "Scherma", listOf("scherma", "fioretto", "sciabola", "spada ")),
    GINNASTICA("ginnastica", "Ginnastica", listOf("ginnastica", "ritmica", "artistica")),
    EQUITAZIONE("equitazione", "Equitazione", listOf("equitazione", "salto ostacoli", "dressage", "piazza di siena", "coppa degli assi")),
    CANOTTAGGIO("canottaggio", "Canottaggio e canoa", listOf("canottaggio", "canoa", "kayak", "rowing")),
    VELA("vela", "Vela", listOf("vela", "america's cup", "sailgp", "regata")),
    PALLAMANO("pallamano", "Pallamano", listOf("pallamano", "handball")),
    TIRO("tiro", "Tiro", listOf("tiro a volo", "tiro con l'arco", "tiro a segno")),
    FRECCETTE("freccette", "Freccette", listOf("freccette", "darts", "pdc")),
    BILIARDO("biliardo", "Biliardo e snooker", listOf("biliardo", "snooker", "pool ")),
    CRICKET("cricket", "Cricket", listOf("cricket")),
    SPORT_ACQUATICI("sport_acquatici", "Sport acquatici", listOf("surf", "motonautica", "f1h2o")),
    ARRAMPICATA("arrampicata", "Arrampicata", listOf("arrampicata", "climbing", "paraclimbing", "boulder", "rock master")),
    ORIENTAMENTO("orientamento", "Orienteering", listOf("orientamento", "orienteering")),
    MULTISPORT("multisport", "Più discipline", listOf("olimpiadi", "olimpici", "paralimpiadi", "giochi del mediterraneo", "universiadi")),
    ALTRO("altro", "Altri sport", emptyList());

    companion object {
        private val perChiave = entries.associateBy { it.chiave }

        /** Vero se il testo è solo il nome di una disciplina ("Ciclismo", "Rally", "Judo"). */
        fun eGenerico(testo: String): Boolean {
            val t = Testo.semplifica(testo)
            return entries.any { s -> Testo.semplifica(s.nome) == t || s.parole.any { p -> !p.trim().contains(' ') && Testo.semplifica(p) == t } }
        }

        fun daChiave(chiave: String): Sport = perChiave[chiave] ?: ALTRO

        /**
         * Riconosce la disciplina. [indicazioni] sono i testi in ordine di
         * affidabilità: prima il campo sport della fonte, poi competizione e titolo.
         */
        fun riconosci(vararg indicazioni: String?): Sport {
            for (testo in indicazioni) {
                if (testo.isNullOrBlank()) continue
                val t = " " + Testo.semplifica(testo) + " "
                // Vince la disciplina nominata per prima: in "Pallavolo. Serie A1"
                // conta "pallavolo", non "serie a". A parità di posizione vale
                // l'ordine dell'elenco, dal più specifico al più generico.
                entries.filter { it != ALTRO }
                    .mapNotNull { s -> s.parole.mapNotNull { p -> posizione(t, p) }.minOrNull()?.let { s to it } }
                    .minByOrNull { it.second }
                    ?.let { return it.first }
            }
            return ALTRO
        }

        /**
         * Una parola chiave che finisce con uno spazio va trovata intera
         * ("sci " non deve riconoscere "scienza"); le altre bastano come
         * inizio di parola ("ciclismo" riconosce anche "ciclismo:").
         */
        private fun posizione(testo: String, parola: String): Int? {
            val intera = parola.endsWith(" ")
            val p = Testo.semplifica(parola)
            val i = if (intera) testo.indexOf(" $p ") else testo.indexOf(" $p")
            return i.takeIf { it >= 0 }
        }
    }
}
