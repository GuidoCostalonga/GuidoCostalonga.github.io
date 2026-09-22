package org.costalonga.meteofvg.allerte

/**
 * Quando avvisare e che cosa scrivere.
 *
 * Qui non c'e' niente di Android: sono le due regole che decidono se far
 * squillare il telefono, tenute da parte perche' si possano provare.
 */
object Decisione {

    /**
     * Si avvisa quando compare un'allerta che prima non c'era, e quando
     * quella in corso cambia testo.
     *
     * Non si avvisa mai se il riquadro e' vuoto o non raggiungibile: il
     * silenzio della Regione non e' una notizia, e un riquadro che non si
     * carica non e' un'assenza di allerta.
     */
    fun daAvvisare(
        precedente: StatoAllerte?,
        improntaPrecedente: String?,
        adesso: StatoAllerte,
        improntaAdesso: String,
    ): Boolean {
        if (adesso != StatoAllerte.PIENO) return false
        if (improntaAdesso.isEmpty()) return false
        if (precedente != StatoAllerte.PIENO) return true
        return improntaPrecedente != improntaAdesso
    }

    /**
     * L'impronta del testo dell'avviso: serve solo a capire se e' lo stesso
     * di prima. Spazi e maiuscole non contano.
     */
    fun impronta(testo: String): String {
        val pulito = testo.replace(REGEX_SPAZI, " ").trim().lowercase()
        if (pulito.isEmpty()) return ""
        return Integer.toHexString(pulito.hashCode())
    }

    /**
     * La riga da mettere nella notifica: il testo del riquadro ridotto a una
     * frase, senza aggiungere nulla che la Regione non abbia scritto.
     */
    fun riassunto(testo: String, massimo: Int = 140): String {
        val pulito = testo.replace(REGEX_SPAZI, " ").trim()
        if (pulito.isEmpty()) return ""

        val fine = pulito.indexOfFirst { it == '.' || it == '!' || it == '?' }
        val frase = if (fine in 40 until massimo) pulito.substring(0, fine + 1) else pulito
        if (frase.length <= massimo) return frase

        val tagliato = frase.substring(0, massimo)
        val spazio = tagliato.lastIndexOf(' ')
        val corpo = if (spazio > massimo / 2) tagliato.substring(0, spazio) else tagliato
        return corpo.trimEnd(' ', ',', ';', ':', '-') + "…"
    }

    private val REGEX_SPAZI = Regex("\\s+")
}
