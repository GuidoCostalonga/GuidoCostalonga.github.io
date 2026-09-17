package org.costalonga.meteofvg.data

/**
 * I 215 Comuni del Friuli Venezia Giulia.
 *
 * Elenco identico a quello della pagina https://costalonga.org/meteo/ :
 * denominazione, sigla di provincia, coordinate del centroide del municipio
 * (EPSG:4326) e codice ISTAT. Nessun dato aggiunto o modificato a mano.
 *
 * Il file e' generato dall'elenco della pagina: non si modifica a mano.
 */

/** Riga compatta dell'elenco: nome, sigla provincia, latitudine, longitudine, codice ISTAT. */
private class C(
    val nome: String,
    val prov: String,
    val lat: Double,
    val lon: Double,
    val istat: String,
)

data class Comune(
    val nome: String,
    val sigla: String,
    val provincia: String,
    val lat: Double,
    val lon: Double,
    val istat: String,
) {
    val slug: String = slugDi(nome)

    /** Etichetta usata in testata: il nome, con la provincia quando non coincide. */
    val etichetta: String get() = if (provincia == nome) nome else "$nome \u00b7 $provincia"
}

val PROVINCE: Map<String, String> = linkedMapOf(
    "GO" to "Gorizia",
    "PN" to "Pordenone",
    "TS" to "Trieste",
    "UD" to "Udine",
)

fun slugDi(nome: String): String = java.text.Normalizer
    .normalize(nome, java.text.Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("[^a-z0-9]+".toRegex(), "-")
    .trim('-')

private val ELENCO = listOf(
    C("Aiello del Friuli", "UD", 45.8729, 13.3636, "30001"),
    C("Amaro", "UD", 46.374, 13.0954, "30002"),
    C("Ampezzo", "UD", 46.4171, 12.7903, "30003"),
    C("Andreis", "PN", 46.2005, 12.6137, "93001"),
    C("Aquileia", "UD", 45.7696, 13.3698, "30004"),
    C("Arba", "PN", 46.145, 12.7914, "93002"),
    C("Arta Terme", "UD", 46.4724, 13.0267, "30005"),
    C("Artegna", "UD", 46.2391, 13.1557, "30006"),
    C("Attimis", "UD", 46.1893, 13.3071, "30007"),
    C("Aviano", "PN", 46.0685, 12.5891, "93004"),
    C("Azzano Decimo", "PN", 45.8814, 12.7143, "93005"),
    C("Bagnaria Arsa", "UD", 45.8836, 13.285, "30008"),
    C("Barcis", "PN", 46.1903, 12.5582, "93006"),
    C("Basiliano", "UD", 46.0163, 13.1071, "30009"),
    C("Bertiolo", "UD", 45.9427, 13.0492, "30010"),
    C("Bicinicco", "UD", 45.9357, 13.2512, "30011"),
    C("Bordano", "UD", 46.3153, 13.1046, "30012"),
    C("Brugnera", "PN", 45.9035, 12.526, "93007"),
    C("Budoia", "PN", 46.043, 12.5326, "93008"),
    C("Buja", "UD", 46.2067, 13.1186, "30013"),
    C("Buttrio", "UD", 46.0104, 13.3338, "30014"),
    C("Camino al Tagliamento", "UD", 45.927, 12.9451, "30015"),
    C("Campoformido", "UD", 46.0193, 13.1624, "30016"),
    C("Campolongo Tapogliano", "UD", 45.8685, 13.4038, "30138"),
    C("Caneva", "PN", 45.9687, 12.4481, "93009"),
    C("Capriva del Friuli", "GO", 45.9414, 13.5136, "31001"),
    C("Carlino", "UD", 45.8023, 13.1882, "30018"),
    C("Casarsa della Delizia", "PN", 45.9559, 12.8427, "93010"),
    C("Cassacco", "UD", 46.1736, 13.1879, "30019"),
    C("Castelnovo del Friuli", "PN", 46.2138, 12.8831, "93011"),
    C("Castions di Strada", "UD", 45.9083, 13.1853, "30020"),
    C("Cavasso Nuovo", "PN", 46.1966, 12.7703, "93012"),
    C("Cavazzo Carnico", "UD", 46.3675, 13.04, "30021"),
    C("Cercivento", "UD", 46.5273, 12.9892, "30022"),
    C("Cervignano del Friuli", "UD", 45.8228, 13.3364, "30023"),
    C("Chions", "PN", 45.8472, 12.7111, "93013"),
    C("Chiopris-Viscone", "UD", 45.9314, 13.3811, "30024"),
    C("Chiusaforte", "UD", 46.4083, 13.309, "30025"),
    C("Cimolais", "PN", 46.2876, 12.4381, "93014"),
    C("Cividale del Friuli", "UD", 46.0936, 13.4303, "30026"),
    C("Claut", "PN", 46.2667, 12.5157, "93015"),
    C("Clauzetto", "PN", 46.2299, 12.9156, "93016"),
    C("Codroipo", "UD", 45.9611, 12.979, "30027"),
    C("Colloredo di Monte Albano", "UD", 46.1664, 13.139, "30028"),
    C("Comeglians", "UD", 46.5152, 12.8678, "30029"),
    C("Cordenons", "PN", 46.0037, 12.7187, "93017"),
    C("Cordovado", "PN", 45.8458, 12.8807, "93018"),
    C("Cormons", "GO", 45.9578, 13.471, "31002"),
    C("Corno di Rosazzo", "UD", 45.9971, 13.4416, "30030"),
    C("Coseano", "UD", 46.097, 13.0194, "30031"),
    C("Dignano", "UD", 46.085, 12.9393, "30032"),
    C("Doberdò del Lago", "GO", 45.8446, 13.5401, "31003"),
    C("Dogna", "UD", 46.4479, 13.315, "30033"),
    C("Dolegna del Collio", "GO", 46.0313, 13.4793, "31004"),
    C("Drenchia", "UD", 46.1742, 13.6365, "30034"),
    C("Duino Aurisina", "TS", 45.7704, 13.6753, "32001"),
    C("Enemonzo", "UD", 46.4109, 12.8787, "30035"),
    C("Erto e Casso", "PN", 46.2771, 12.3596, "93019"),
    C("Faedis", "UD", 46.1503, 13.3464, "30036"),
    C("Fagagna", "UD", 46.1134, 13.0846, "30037"),
    C("Fanna", "PN", 46.1871, 12.7531, "93020"),
    C("Farra d'Isonzo", "GO", 45.908, 13.5165, "31005"),
    C("Fiume Veneto", "PN", 45.9279, 12.7322, "93021"),
    C("Fiumicello Villa Vicentina", "UD", 45.7836, 13.413, "30190"),
    C("Flaibano", "UD", 46.0583, 12.9836, "30039"),
    C("Fogliano Redipuglia", "GO", 45.8565, 13.4929, "31006"),
    C("Fontanafredda", "PN", 45.9734, 12.5691, "93022"),
    C("Forgaria nel Friuli", "UD", 46.2228, 12.9735, "30137"),
    C("Forni Avoltri", "UD", 46.5868, 12.7769, "30040"),
    C("Forni di Sopra", "UD", 46.4202, 12.5856, "30041"),
    C("Forni di Sotto", "UD", 46.395, 12.6702, "30042"),
    C("Frisanco", "PN", 46.2128, 12.7271, "93024"),
    C("Gemona del Friuli", "UD", 46.277, 13.1401, "30043"),
    C("Gonars", "UD", 45.8959, 13.2371, "30044"),
    C("Gorizia", "GO", 45.9441, 13.6252, "31007"),
    C("Gradisca d'Isonzo", "GO", 45.8896, 13.5003, "31008"),
    C("Grado", "GO", 45.6777, 13.3864, "31009"),
    C("Grimacco", "UD", 46.1674, 13.5812, "30045"),
    C("Latisana", "UD", 45.7785, 12.9962, "30046"),
    C("Lauco", "UD", 46.424, 12.933, "30047"),
    C("Lestizza", "UD", 45.9549, 13.1417, "30048"),
    C("Lignano Sabbiadoro", "UD", 45.6894, 13.1386, "30049"),
    C("Lusevera", "UD", 46.2758, 13.2693, "30051"),
    C("Magnano in Riviera", "UD", 46.2313, 13.1768, "30052"),
    C("Majano", "UD", 46.1852, 13.0682, "30053"),
    C("Malborghetto Valbruna", "UD", 46.4978, 13.4331, "30054"),
    C("Maniago", "PN", 46.171, 12.7074, "93025"),
    C("Manzano", "UD", 45.9905, 13.3815, "30055"),
    C("Marano Lagunare", "UD", 45.765, 13.1675, "30056"),
    C("Mariano del Friuli", "GO", 45.9161, 13.4587, "31010"),
    C("Martignacco", "UD", 46.0979, 13.1368, "30057"),
    C("Medea", "GO", 45.9177, 13.4225, "31011"),
    C("Meduno", "PN", 46.2168, 12.7859, "93026"),
    C("Mereto di Tomba", "UD", 46.0498, 13.0428, "30058"),
    C("Moggio Udinese", "UD", 46.4097, 13.1951, "30059"),
    C("Moimacco", "UD", 46.0913, 13.377, "30060"),
    C("Monfalcone", "GO", 45.8049, 13.5331, "31012"),
    C("Monrupino", "TS", 45.7178, 13.7974, "32002"),
    C("Montenars", "UD", 46.255, 13.1788, "30061"),
    C("Montereale Valcellina", "PN", 46.1605, 12.6621, "93027"),
    C("Moraro", "GO", 45.9306, 13.4965, "31013"),
    C("Morsano al Tagliamento", "PN", 45.8581, 12.9293, "93028"),
    C("Mortegliano", "UD", 45.9458, 13.1728, "30062"),
    C("Moruzzo", "UD", 46.1202, 13.1232, "30063"),
    C("Mossa", "GO", 45.9386, 13.5482, "31014"),
    C("Muggia", "TS", 45.5958, 13.7829, "32003"),
    C("Muzzana del Turgnano", "UD", 45.8181, 13.1293, "30064"),
    C("Nimis", "UD", 46.2007, 13.2663, "30065"),
    C("Osoppo", "UD", 46.256, 13.0809, "30066"),
    C("Ovaro", "UD", 46.4829, 12.8654, "30067"),
    C("Pagnacco", "UD", 46.1243, 13.187, "30068"),
    C("Palazzolo dello Stella", "UD", 45.8048, 13.0796, "30069"),
    C("Palmanova", "UD", 45.9055, 13.3099, "30070"),
    C("Paluzza", "UD", 46.5308, 13.0182, "30071"),
    C("Pasian di Prato", "UD", 46.0485, 13.1876, "30072"),
    C("Pasiano di Pordenone", "PN", 45.8498, 12.6265, "93029"),
    C("Paularo", "UD", 46.5303, 13.1166, "30073"),
    C("Pavia di Udine", "UD", 45.9963, 13.3041, "30074"),
    C("Pinzano al Tagliamento", "PN", 46.1831, 12.9454, "93030"),
    C("Pocenia", "UD", 45.8355, 13.1008, "30075"),
    C("Polcenigo", "PN", 46.0307, 12.5016, "93031"),
    C("Pontebba", "UD", 46.5065, 13.3064, "30076"),
    C("Porcia", "PN", 45.9466, 12.6018, "93032"),
    C("Pordenone", "PN", 45.9562, 12.6597, "93033"),
    C("Porpetto", "UD", 45.8582, 13.2179, "30077"),
    C("Povoletto", "UD", 46.1182, 13.2988, "30078"),
    C("Pozzuolo del Friuli", "UD", 45.986, 13.195, "30079"),
    C("Pradamano", "UD", 46.0338, 13.3026, "30080"),
    C("Prata di Pordenone", "PN", 45.8933, 12.5963, "93034"),
    C("Prato Carnico", "UD", 46.5207, 12.8094, "30081"),
    C("Pravisdomini", "PN", 45.8189, 12.6944, "93035"),
    C("Precenicco", "UD", 45.7889, 13.0774, "30082"),
    C("Premariacco", "UD", 46.0607, 13.3948, "30083"),
    C("Preone", "UD", 46.3937, 12.8662, "30084"),
    C("Prepotto", "UD", 46.0457, 13.4794, "30085"),
    C("Pulfero", "UD", 46.1793, 13.478, "30086"),
    C("Ragogna", "UD", 46.1735, 12.9824, "30087"),
    C("Ravascletto", "UD", 46.5229, 12.9221, "30088"),
    C("Raveo", "UD", 46.4344, 12.8711, "30089"),
    C("Reana del Rojale", "UD", 46.1457, 13.2351, "30090"),
    C("Remanzacco", "UD", 46.0856, 13.3243, "30091"),
    C("Resia", "UD", 46.3474, 13.3361, "30092"),
    C("Resiutta", "UD", 46.3931, 13.2186, "30093"),
    C("Rigolato", "UD", 46.5528, 12.8521, "30094"),
    C("Rive d'Arcano", "UD", 46.1261, 13.0316, "30095"),
    C("Rivignano Teor", "UD", 45.8613, 13.0476, "30188"),
    C("Romans d'Isonzo", "GO", 45.8906, 13.4382, "31015"),
    C("Ronchi dei Legionari", "GO", 45.8278, 13.5018, "31016"),
    C("Ronchis", "UD", 45.8066, 12.9978, "30097"),
    C("Roveredo in Piano", "PN", 46.0111, 12.6203, "93036"),
    C("Ruda", "UD", 45.8382, 13.4019, "30098"),
    C("Sacile", "PN", 45.9539, 12.5034, "93037"),
    C("Sagrado", "GO", 45.8758, 13.4855, "31017"),
    C("San Canzian d'Isonzo", "GO", 45.7986, 13.4661, "31018"),
    C("San Daniele del Friuli", "UD", 46.1532, 13.009, "30099"),
    C("San Dorligo della Valle", "TS", 45.6126, 13.8535, "32004"),
    C("San Floriano del Collio", "GO", 45.9831, 13.5871, "31019"),
    C("San Giorgio della Richinvelda", "PN", 46.0395, 12.8823, "93038"),
    C("San Giorgio di Nogaro", "UD", 45.832, 13.2111, "30100"),
    C("San Giovanni al Natisone", "UD", 45.9779, 13.4011, "30101"),
    C("San Leonardo", "UD", 46.1275, 13.5384, "30102"),
    C("San Lorenzo Isontino", "GO", 45.9312, 13.5285, "31020"),
    C("San Martino al Tagliamento", "PN", 46.0208, 12.8638, "93039"),
    C("San Pier d'Isonzo", "GO", 45.8469, 13.4601, "31021"),
    C("San Pietro al Natisone", "UD", 46.1267, 13.4852, "30103"),
    C("San Quirino", "PN", 46.0362, 12.68, "93040"),
    C("San Vito al Tagliamento", "PN", 45.9145, 12.8566, "93041"),
    C("San Vito al Torre", "UD", 45.8958, 13.3706, "30105"),
    C("San Vito di Fagagna", "UD", 46.0906, 13.067, "30106"),
    C("Santa Maria la Longa", "UD", 45.9331, 13.288, "30104"),
    C("Sappada", "UD", 46.5678, 12.6864, "30189"),
    C("Sauris", "UD", 46.4654, 12.7087, "30107"),
    C("Savogna", "UD", 46.1802, 13.5522, "30108"),
    C("Savogna d'Isonzo", "GO", 45.9059, 13.5749, "31022"),
    C("Sedegliano", "UD", 46.0144, 12.9774, "30109"),
    C("Sequals", "PN", 46.1655, 12.8288, "93042"),
    C("Sesto al Reghena", "PN", 45.8476, 12.8155, "93043"),
    C("Sgonico", "TS", 45.736, 13.7482, "32005"),
    C("Socchieve", "UD", 46.3971, 12.8481, "30110"),
    C("Spilimbergo", "PN", 46.1113, 12.9016, "93044"),
    C("Staranzano", "GO", 45.8058, 13.5003, "31023"),
    C("Stregna", "UD", 46.1315, 13.6036, "30111"),
    C("Sutrio", "UD", 46.512, 12.9908, "30112"),
    C("Taipana", "UD", 46.2587, 13.3459, "30113"),
    C("Talmassons", "UD", 45.9296, 13.1171, "30114"),
    C("Tarcento", "UD", 46.2153, 13.2217, "30116"),
    C("Tarvisio", "UD", 46.5053, 13.5784, "30117"),
    C("Tavagnacco", "UD", 46.1274, 13.2141, "30118"),
    C("Terzo d'Aquileia", "UD", 45.8009, 13.3458, "30120"),
    C("Tolmezzo", "UD", 46.4295, 13.0562, "30121"),
    C("Torreano", "UD", 46.1285, 13.4311, "30122"),
    C("Torviscosa", "UD", 45.8222, 13.2769, "30123"),
    C("Tramonti di Sopra", "PN", 46.3089, 12.7885, "93045"),
    C("Tramonti di Sotto", "PN", 46.2849, 12.7956, "93046"),
    C("Trasaghis", "UD", 46.2822, 13.0754, "30124"),
    C("Travesio", "PN", 46.1965, 12.871, "93047"),
    C("Treppo Grande", "UD", 46.1906, 13.1581, "30126"),
    C("Treppo Ligosullo", "UD", 46.5443, 13.0589, "30191"),
    C("Tricesimo", "UD", 46.1606, 13.2125, "30127"),
    C("Trieste", "TS", 45.6505, 13.7931, "32006"),
    C("Trivignano Udinese", "UD", 45.9434, 13.3406, "30128"),
    C("Turriaco", "GO", 45.82, 13.4467, "31024"),
    C("Udine", "UD", 46.0635, 13.2358, "30129"),
    C("Vajont", "PN", 46.1461, 12.6974, "93052"),
    C("Valvasone Arzene", "PN", 45.9952, 12.8663, "93053"),
    C("Varmo", "UD", 45.887, 12.9877, "30130"),
    C("Venzone", "UD", 46.3337, 13.1394, "30131"),
    C("Verzegnis", "UD", 46.3851, 12.9872, "30132"),
    C("Villa Santina", "UD", 46.4141, 12.9207, "30133"),
    C("Villesse", "GO", 45.8639, 13.4402, "31025"),
    C("Visco", "UD", 45.8922, 13.3469, "30135"),
    C("Vito d'Asio", "PN", 46.2278, 12.9383, "93049"),
    C("Vivaro", "PN", 46.078, 12.7758, "93050"),
    C("Zoppola", "PN", 45.9663, 12.7716, "93051"),
    C("Zuglio", "UD", 46.4618, 13.026, "30136")
)

/** Tutti i Comuni, in ordine alfabetico come nella pagina. */
val COMUNI: List<Comune> = ELENCO.map {
    Comune(it.nome, it.prov, PROVINCE.getValue(it.prov), it.lat, it.lon, it.istat)
}

private val PER_NOME: Map<String, Comune> = COMUNI.associateBy { it.nome }
private val PER_SLUG: Map<String, Comune> = COMUNI.associateBy { it.slug }

fun comunePerNome(nome: String?): Comune? = nome?.let { PER_NOME[it] }
fun comunePerSlug(slug: String?): Comune? = slug?.let { PER_SLUG[it.lowercase()] }

/** Comune predefinito all'avvio, come nella pagina. */
val COMUNE_PREDEFINITO: Comune = PER_NOME.getValue("Roveredo in Piano")

/** I tre Comuni raggiungibili con un tocco, come nella pagina. */
val COMUNI_RAPIDI: List<Comune> = listOf("Roveredo in Piano", "Pordenone", "Trieste")
    .map { PER_NOME.getValue(it) }

/** Comuni raggruppati per provincia, province in ordine alfabetico. */
val COMUNI_PER_PROVINCIA: List<Pair<String, List<Comune>>> = PROVINCE.entries
    .sortedBy { it.value }
    .map { (sigla, nome) -> nome to COMUNI.filter { it.sigla == sigla } }
