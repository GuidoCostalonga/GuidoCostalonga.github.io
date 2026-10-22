package org.costalonga.fattidelgiorno.generazione

import org.costalonga.fattidelgiorno.dominio.Fatto

/**
 * Il prompt da incollare in un servizio di intelligenza artificiale: chiede di
 * creare o rifinire il contenuto senza toccare i fatti verificati.
 */
object GeneratorePrompt {

    fun genera(fatto: Fatto, opzioniRichieste: OpzioniContenuto, bozza: String): String {
        val o = opzioniRichieste.coerente()
        val fonti = fatto.fonti.joinToString("\n") { f ->
            "- ${f.nome}: ${f.url}" + (f.consultataIl?.let { " (consultata il $it)" } ?: "")
        }
        val limite = o.piattaforma.limiteCaratteri?.let { "Rispetta il limite di $it caratteri della piattaforma." } ?: ""
        val lingua = if (fatto.lingua != "it") "Il testo del fatto è in inglese: traducilo in italiano in modo fedele, senza aggiungere nulla.\n" else ""
        return """
Agisci come social media manager esperto di comunicazione storica e culturale. Scrivi in italiano corretto, senza inglesismi inutili.

## Compito
Crea o rifinisci un contenuto di tipo "${o.formato.etichetta}" per ${o.piattaforma.etichetta}, partendo dal fatto verificato qui sotto e dalla bozza in fondo.

## Fatto verificato (non modificarlo)
- Titolo: ${fatto.titolo}
- Che cosa: ${fatto.testo}
- Legame con la data: ${fatto.spiegazioneData()}
- Area geografica: ${fatto.area.etichetta}
- Categoria: ${fatto.categoria.etichetta}
- Livello di verifica: ${fatto.verifica.etichetta}
- Fonti:
$fonti

## Regole non negoziabili
1. Non aggiungere fatti, date, numeri, nomi, luoghi, citazioni o aneddoti che non compaiono nel fatto verificato e nelle fonti elencate.
2. Non cambiare la data, l'anno o il legame con il giorno: il contenuto deve dire chiaramente che il fatto è ${fatto.spiegazioneData()}.
3. Mantieni il riferimento alla fonte (nome e collegamento) nel contenuto o nella didascalia.
4. Se un dettaglio ti sembra incerto o incompleto, segnalalo tra parentesi quadre invece di inventare.
5. Niente allarmismi, niente attacchi personali, niente promesse. Usa "ed" e "ad" solo davanti a parole che iniziano con la stessa vocale.
$lingua
## Stile
- Tono: ${o.tono.etichetta} (${o.tono.descrizione}).
- Pubblico: ${o.pubblico.trim().ifEmpty { "pubblico generale, curioso ma non specialista" }}.
- Lunghezza: ${o.lunghezza.etichetta.lowercase()}. $limite
- Emoji: ${if (o.emoji) "sì, con misura" else "no"}.
- Hashtag: ${if (o.hashtag) "sì, pochi e pertinenti" else "no"}.
- Invito all'azione: ${o.invitoAzione.trim().ifEmpty { "uno, breve, coerente con la piattaforma" }}.

## Struttura richiesta
${struttura(o)}

## Bozza di partenza (puoi migliorarla, non stravolgerne i fatti)
$bozza

## Risultato atteso
Restituisci solo il contenuto finale pronto da pubblicare, in italiano, senza spiegazioni. Alla fine aggiungi una riga "Controllo fonti:" con l'elenco dei collegamenti usati.
        """.trim()
    }

    private fun struttura(o: OpzioniContenuto): String = when (o.formato) {
        Formato.POST -> if (o.piattaforma == Piattaforma.X) {
            "Un solo messaggio entro 280 caratteri: apertura che incuriosisce, il fatto in una frase, il collegamento alla fonte."
        } else {
            "Apertura che incuriosisce, il fatto in due o tre frasi, perché è legato a oggi, invito all'azione, fonte, hashtag."
        }
        Formato.STORIA -> "Tre schermate: 1) data e titolo in grande; 2) il fatto in una frase; 3) perché oggi, invito all'azione e adesivo con il collegamento alla fonte."
        Formato.CAROSELLO -> "Da 5 a 7 schede: copertina con data e titolo; il legame con la data; una scheda per ogni idea (una o due frasi ciascuna); scheda finale con fonte e invito all'azione. Poi la didascalia con hashtag."
        Formato.FOTO_CAROSELLO -> "Da 4 a 6 foto con testo breve in sovrimpressione: copertina, legame con la data, il fatto diviso per idee, chiusura con fonte e invito a seguire."
        Formato.REEL, Formato.VIDEO_BREVE -> "Script con tempi: apertura a effetto nei primi 3 secondi, sviluppo in battute brevi, legame con oggi, chiusura con invito all'azione; testo in sovrimpressione e didascalia con fonte."
        Formato.SCRIPT_TIKTOK -> "Script parlato di 20-40 secondi: apertura fulminea (massimo 8 parole), battute di massimo 12 parole, una domanda finale, testo in sovrimpressione e didascalia con fonte."
        Formato.TESTO_PROFESSIONALE -> "Prima riga che fa fermare lo scorrimento, paragrafi di una o due frasi, un collegamento con il presente senza forzature, domanda finale, fonte, massimo 3 hashtag."
        Formato.THREAD -> "Da 3 a 6 messaggi numerati (1/n), ciascuno entro 280 caratteri: il primo con gancio e data, i centrali con il fatto, l'ultimo con fonte e invito."
    }
}
