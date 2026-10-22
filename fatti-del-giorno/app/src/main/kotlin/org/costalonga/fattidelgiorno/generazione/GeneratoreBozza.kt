package org.costalonga.fattidelgiorno.generazione

import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.Fatto

/**
 * Costruisce la bozza senza nessun servizio esterno: usa soltanto il titolo,
 * il testo, la data e le fonti del fatto. Non aggiunge dettagli storici.
 */
object GeneratoreBozza {

    fun genera(fatto: Fatto, opzioniRichieste: OpzioniContenuto): String {
        val opzioni = opzioniRichieste.coerente()
        return when (opzioni.formato) {
            Formato.POST -> if (opzioni.piattaforma == Piattaforma.X) postX(fatto, opzioni) else post(fatto, opzioni)
            Formato.STORIA -> storia(fatto, opzioni)
            Formato.CAROSELLO, Formato.FOTO_CAROSELLO -> carosello(fatto, opzioni)
            Formato.REEL, Formato.VIDEO_BREVE, Formato.SCRIPT_TIKTOK -> scriptVideo(fatto, opzioni)
            Formato.TESTO_PROFESSIONALE -> testoProfessionale(fatto, opzioni)
            Formato.THREAD -> thread(fatto, opzioni)
        }
    }

    // ---- mattoni comuni -------------------------------------------------

    internal fun frasi(testo: String): List<String> =
        testo.replace("\n", " ").split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** La parte del testo che precede l'estratto della voce collegata. */
    private fun nucleo(fatto: Fatto): String = fatto.testo.substringBefore("\n\nDalla voce collegata:").trim()

    private fun contesto(fatto: Fatto): String? =
        fatto.testo.substringAfter("\n\nDalla voce collegata:", "").trim().ifEmpty { null }

    private fun corpo(fatto: Fatto, lunghezza: Lunghezza): String {
        val principale = nucleo(fatto)
        val extra = contesto(fatto)
        return when (lunghezza) {
            Lunghezza.BREVE -> frasi(principale).first()
            Lunghezza.MEDIA -> principale
            Lunghezza.LUNGA -> if (extra != null) "$principale\n\n$extra" else principale
        }
    }

    private fun emoji(fatto: Fatto, opzioni: OpzioniContenuto): String {
        if (!opzioni.emoji) return ""
        return when (fatto.categoria) {
            Categoria.RICORRENZA -> "📅 "
            Categoria.EVENTO -> "🕰️ "
            Categoria.NASCITA -> "🎂 "
            Categoria.SCOMPARSA -> "🕯️ "
            Categoria.CULTURA -> "🎭 "
            Categoria.CURIOSITA -> "💡 "
        }
    }

    private fun quando(fatto: Fatto): String = Date.giornoMese(fatto.giorno, fatto.mese)

    private fun gancio(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val data = quando(fatto)
        val anni = fatto.anno?.let { " del $it" } ?: ""
        return when (opzioni.tono) {
            Tono.INFORMALE -> when (fatto.collegamento) {
                Collegamento.NATO -> "Lo sapevi? Il $data$anni nasceva ${fatto.titolo}."
                Collegamento.MORTO -> "Il $data$anni ci lasciava ${fatto.titolo}: oggi lo ricordiamo."
                Collegamento.RICORRENZA -> "Oggi, $data, si celebra: ${fatto.titolo}."
                Collegamento.AVVENUTO -> "Sapevi che il $data$anni è successo questo? ${fatto.titolo}"
            }
            Tono.ISTITUZIONALE -> when (fatto.collegamento) {
                Collegamento.NATO -> "Il $data$anni nasceva ${fatto.titolo}."
                Collegamento.MORTO -> "Il $data$anni scompariva ${fatto.titolo}."
                Collegamento.RICORRENZA -> "Oggi, $data, ricorre: ${fatto.titolo}."
                Collegamento.AVVENUTO -> "Il $data$anni: ${fatto.titolo}."
            }
            Tono.DIVULGATIVO -> "Accadde il $data$anni. ${fatto.titolo}"
            Tono.IRONICO -> "Segnatevi la data, $data$anni: ${fatto.titolo}"
            Tono.ENTUSIASTA -> "Che giorno, il $data$anni! ${fatto.titolo}"
        }.replace("..", ".")
    }

    private fun legame(fatto: Fatto): String = "Perché oggi? Perché è ${fatto.spiegazioneData()}."

    private fun invito(fatto: Fatto, opzioni: OpzioniContenuto): String {
        if (opzioni.invitoAzione.isNotBlank()) return opzioni.invitoAzione.trim()
        return when (opzioni.piattaforma) {
            Piattaforma.LINKEDIN -> "Conoscevate questa storia? Vi aspetto nei commenti."
            Piattaforma.X -> "Lo sapevi?"
            Piattaforma.TIKTOK -> "Seguimi per un fatto al giorno."
            else -> when (fatto.area) {
                Area.FVG -> "Lo sapevi? Raccontalo nei commenti e condividi con chi ama la nostra terra."
                else -> "Lo sapevi? Scrivilo nei commenti."
            }
        }
    }

    private fun fonte(fatto: Fatto): String {
        val prima = fatto.fonti.first()
        return "Fonte: ${prima.nome} – ${prima.url}"
    }

    internal fun hashtag(fatto: Fatto, opzioni: OpzioniContenuto, massimo: Int = 6): String {
        if (!opzioni.hashtag) return ""
        val tag = mutableListOf("#AccaddeOggi")
        when (fatto.area) {
            Area.FVG -> { tag += "#FriuliVeneziaGiulia"; tag += "#FVG" }
            Area.ITALIA -> tag += "#Italia"
            Area.MONDO -> tag += "#Storia"
        }
        tag += when (fatto.categoria) {
            Categoria.RICORRENZA -> "#Ricorrenza"
            Categoria.EVENTO -> "#Storia"
            Categoria.NASCITA -> "#NatiOggi"
            Categoria.SCOMPARSA -> "#Memoria"
            Categoria.CULTURA -> "#Cultura"
            Categoria.CURIOSITA -> "#Curiosità"
        }
        tag += "#${Date.nomeMese(fatto.mese).replaceFirstChar { it.uppercase() }}"
        return tag.distinct().take(massimo).joinToString(" ")
    }

    private fun pubblico(opzioni: OpzioniContenuto): String? =
        opzioni.pubblico.trim().ifEmpty { null }?.let { "Per chi: $it" }

    // ---- formati ----------------------------------------------------------

    private fun post(fatto: Fatto, opzioni: OpzioniContenuto): String = listOfNotNull(
        emoji(fatto, opzioni) + gancio(fatto, opzioni),
        corpo(fatto, opzioni.lunghezza),
        legame(fatto),
        invito(fatto, opzioni),
        fonte(fatto),
        hashtag(fatto, opzioni).ifEmpty { null },
    ).joinToString("\n\n")

    private fun storia(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val prima = frasi(nucleo(fatto)).first()
        return listOfNotNull(
            "Schermata 1 (testo grande): ${emoji(fatto, opzioni)}${quando(fatto)}${fatto.anno?.let { " $it" } ?: ""}\n${fatto.titolo}",
            "Schermata 2: $prima",
            "Schermata 3: ${legame(fatto)}\n${invito(fatto, opzioni)}\nAdesivo collegamento: ${fatto.fonti.first().url}",
            hashtag(fatto, opzioni, massimo = 3).ifEmpty { null }?.let { "Adesivi hashtag: $it" },
        ).joinToString("\n\n")
    }

    private fun carosello(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val nome = if (opzioni.formato == Formato.FOTO_CAROSELLO) "Foto" else "Scheda"
        val frasi = frasi(corpo(fatto, opzioni.lunghezza))
        val blocchi = mutableListOf<String>()
        blocchi += "$nome 1 (copertina): ${emoji(fatto, opzioni)}${quando(fatto)}${fatto.anno?.let { " $it" } ?: ""}\n${fatto.titolo}"
        blocchi += "$nome 2: ${legame(fatto)}"
        frasi.chunked(2).forEachIndexed { i, gruppo -> blocchi += "$nome ${i + 3}: ${gruppo.joinToString(" ")}" }
        blocchi += "$nome ${blocchi.size + 1} (chiusura): ${invito(fatto, opzioni)}\n${fonte(fatto)}"
        val didascalia = listOfNotNull(
            "Didascalia: ${gancio(fatto, opzioni)}",
            pubblico(opzioni),
            hashtag(fatto, opzioni).ifEmpty { null },
        ).joinToString("\n")
        return (blocchi + didascalia).joinToString("\n\n")
    }

    private fun scriptVideo(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val battute = frasi(corpo(fatto, opzioni.lunghezza)).flatMap { spezza(it, if (opzioni.piattaforma == Piattaforma.TIKTOK) 12 else 18) }
        val sviluppo = battute.joinToString("\n") { "- $it" }
        return listOfNotNull(
            "[0-3 s] Apertura a effetto: ${gancio(fatto, opzioni)}",
            "[3-20 s] Il fatto, una battuta per volta:\n$sviluppo",
            "[20-25 s] Il legame con oggi: ${legame(fatto)}",
            "[Chiusura] ${invito(fatto, opzioni)}",
            "Testo in sovrimpressione: ${quando(fatto)}${fatto.anno?.let { " $it" } ?: ""} · ${fatto.titolo}",
            "Didascalia: ${fonte(fatto)}" + hashtag(fatto, opzioni, massimo = 4).let { if (it.isEmpty()) "" else "\n$it" },
        ).joinToString("\n\n")
    }

    private fun testoProfessionale(fatto: Fatto, opzioni: OpzioniContenuto): String = listOfNotNull(
        gancio(fatto, opzioni.copy(tono = if (opzioni.tono == Tono.INFORMALE) Tono.ISTITUZIONALE else opzioni.tono)),
        corpo(fatto, opzioni.lunghezza),
        legame(fatto),
        pubblico(opzioni),
        invito(fatto, opzioni),
        fonte(fatto),
        hashtag(fatto, opzioni, massimo = 3).ifEmpty { null },
    ).joinToString("\n\n")

    /** Lunghezza come la conta X: ogni collegamento vale 23 caratteri. */
    internal fun lunghezzaX(testo: String): Int {
        val url = Regex("https?://\\S+")
        val senzaUrl = url.replace(testo, "")
        return senzaUrl.codePointCount(0, senzaUrl.length) + url.findAll(testo).count() * 23
    }

    private fun postX(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val url = fatto.fonti.first().url
        val coda = listOfNotNull(hashtag(fatto, opzioni, massimo = 2).ifEmpty { null }, url).joinToString("\n")
        val base = emoji(fatto, opzioni) + gancio(fatto, opzioni)
        var testo = "$base\n\n${corpo(fatto, Lunghezza.BREVE)}\n\n$coda"
        if (lunghezzaX(testo) <= 280) return testo
        testo = "$base\n\n$coda"
        if (lunghezzaX(testo) <= 280) return testo
        val spazio = 280 - lunghezzaX("\n\n$coda") - 1
        return base.take(maxOf(spazio, 20)).trimEnd() + "…\n\n$coda"
    }

    private fun thread(fatto: Fatto, opzioni: OpzioniContenuto): String {
        val messaggi = mutableListOf<String>()
        messaggi += emoji(fatto, opzioni) + gancio(fatto, opzioni) + "\n" + legame(fatto)
        var corrente = ""
        for (frase in frasi(corpo(fatto, opzioni.lunghezza))) {
            val candidato = if (corrente.isEmpty()) frase else "$corrente $frase"
            if (lunghezzaX(candidato) + 6 <= 280) corrente = candidato
            else { if (corrente.isNotEmpty()) messaggi += corrente; corrente = frase.take(270) }
        }
        if (corrente.isNotEmpty()) messaggi += corrente
        messaggi += listOfNotNull(invito(fatto, opzioni), fonte(fatto), hashtag(fatto, opzioni, massimo = 2).ifEmpty { null }).joinToString("\n")
        val totale = messaggi.size
        return messaggi.mapIndexed { i, m -> "${i + 1}/$totale ${taglia(m, 280 - "${i + 1}/$totale ".length)}" }.joinToString("\n\n")
    }

    private fun taglia(testo: String, massimo: Int): String =
        if (lunghezzaX(testo) <= massimo) testo else testo.take(massimo - 1).trimEnd() + "…"

    /** Spezza una frase lunga in battute di poche parole, per gli script parlati. */
    internal fun spezza(frase: String, paroleMassime: Int): List<String> {
        val parole = frase.split(" ")
        if (parole.size <= paroleMassime) return listOf(frase)
        return parole.chunked(paroleMassime).map { it.joinToString(" ") }
    }
}
