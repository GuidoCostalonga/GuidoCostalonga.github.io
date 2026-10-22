package org.costalonga.fattidelgiorno.generazione

enum class Formato(val etichetta: String, val descrizione: String) {
    POST("Post", "Testo per il flusso principale"),
    STORIA("Storia", "Tre schermate brevi, da leggere in pochi secondi"),
    CAROSELLO("Carosello", "Una sequenza di schede, una per idea"),
    REEL("Script per Reel", "Voce e testo in sovrimpressione per un video breve"),
    VIDEO_BREVE("Script per video breve", "Voce e testo per un video di meno di un minuto"),
    SCRIPT_TIKTOK("Script per TikTok", "Apertura fulminea e battute corte"),
    FOTO_CAROSELLO("Carosello di foto", "Modalità foto: una scheda per immagine"),
    TESTO_PROFESSIONALE("Testo professionale", "Paragrafi brevi, chiusura con una domanda"),
    THREAD("Thread", "Una catena di messaggi numerati"),
}

enum class Piattaforma(val etichetta: String, val formati: List<Formato>, val limiteCaratteri: Int?) {
    INSTAGRAM("Instagram", listOf(Formato.POST, Formato.STORIA, Formato.CAROSELLO, Formato.REEL), 2200),
    FACEBOOK("Facebook", listOf(Formato.POST, Formato.STORIA, Formato.VIDEO_BREVE), null),
    TIKTOK("TikTok", listOf(Formato.SCRIPT_TIKTOK, Formato.FOTO_CAROSELLO), 4000),
    LINKEDIN("LinkedIn", listOf(Formato.TESTO_PROFESSIONALE, Formato.CAROSELLO), 3000),
    X("X", listOf(Formato.POST, Formato.THREAD), 280),
}

enum class Tono(val etichetta: String, val descrizione: String) {
    INFORMALE("Informale", "chiaro, diretto e coinvolgente, come si parla a un amico curioso"),
    ISTITUZIONALE("Istituzionale", "sobrio e preciso, adatto a un ente o a un rappresentante pubblico"),
    DIVULGATIVO("Divulgativo", "spiega con calma, come una guida che accompagna"),
    IRONICO("Ironico", "leggero e con un sorriso, senza mai prendere in giro nessuno"),
    ENTUSIASTA("Entusiasta", "caldo e partecipe, senza esagerare"),
}

enum class Lunghezza(val etichetta: String) {
    BREVE("Breve"),
    MEDIA("Media"),
    LUNGA("Lunga"),
}

data class OpzioniContenuto(
    val piattaforma: Piattaforma = Piattaforma.INSTAGRAM,
    val formato: Formato = Formato.POST,
    val tono: Tono = Tono.INFORMALE,
    val pubblico: String = "",
    val lunghezza: Lunghezza = Lunghezza.MEDIA,
    val invitoAzione: String = "",
    val emoji: Boolean = true,
    val hashtag: Boolean = true,
) {
    /** Se il formato non appartiene alla piattaforma, si passa al primo disponibile. */
    fun coerente(): OpzioniContenuto =
        if (formato in piattaforma.formati) this else copy(formato = piattaforma.formati.first())
}
