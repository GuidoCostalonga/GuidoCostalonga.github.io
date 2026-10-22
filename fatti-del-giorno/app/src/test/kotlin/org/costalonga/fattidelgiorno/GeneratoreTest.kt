package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.generazione.Formato
import org.costalonga.fattidelgiorno.generazione.GeneratoreBozza
import org.costalonga.fattidelgiorno.generazione.GeneratorePrompt
import org.costalonga.fattidelgiorno.generazione.Lunghezza
import org.costalonga.fattidelgiorno.generazione.OpzioniContenuto
import org.costalonga.fattidelgiorno.generazione.Piattaforma
import org.costalonga.fattidelgiorno.generazione.Tono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratoreTest {

    private val fatto = Prova.fatto()
    private val url = fatto.fonti.first().url

    @Test
    fun `ogni piattaforma e formato producono una bozza con data, fatto e fonte`() {
        Piattaforma.entries.forEach { p ->
            p.formati.forEach { f ->
                val bozza = GeneratoreBozza.genera(fatto, OpzioniContenuto(piattaforma = p, formato = f))
                val dove = "${p.etichetta}/${f.etichetta}"
                assertTrue(dove, bozza.isNotBlank())
                assertTrue(dove, bozza.contains("22 ottobre"))
                assertTrue(dove, bozza.contains(url))
                assertTrue(dove, bozza.contains("ponte di prova", ignoreCase = true))
                p.limiteCaratteri?.let { if (f != Formato.THREAD) assertTrue("$dove: ${bozza.length}", GeneratoreBozza.lunghezzaX(bozza) <= it || p != Piattaforma.X) }
            }
        }
    }

    @Test
    fun `un formato non adatto alla piattaforma viene corretto`() {
        val o = OpzioniContenuto(piattaforma = Piattaforma.TIKTOK, formato = Formato.THREAD).coerente()
        assertEquals(Formato.SCRIPT_TIKTOK, o.formato)
    }

    @Test
    fun `su X il post sta in 280 caratteri e il thread e numerato`() {
        val lungo = Prova.fatto(testo = ("Una frase molto lunga che continua a ripetersi per occupare spazio. ").repeat(12))
        val post = GeneratoreBozza.genera(lungo, OpzioniContenuto(piattaforma = Piattaforma.X, formato = Formato.POST))
        assertTrue(post.length.toString(), GeneratoreBozza.lunghezzaX(post) <= 280)
        assertTrue(post.contains(url))

        val thread = GeneratoreBozza.genera(lungo, OpzioniContenuto(piattaforma = Piattaforma.X, formato = Formato.THREAD, lunghezza = Lunghezza.LUNGA))
        val messaggi = thread.split("\n\n")
        assertTrue(messaggi.size >= 3)
        messaggi.forEachIndexed { i, m ->
            assertTrue(m, m.startsWith("${i + 1}/${messaggi.size} "))
            assertTrue(m, GeneratoreBozza.lunghezzaX(m) <= 280)
        }
        assertTrue(messaggi.last().contains(url))
    }

    @Test
    fun `lo script per TikTok ha apertura, battute brevi e chiusura`() {
        val script = GeneratoreBozza.genera(fatto, OpzioniContenuto(piattaforma = Piattaforma.TIKTOK, formato = Formato.SCRIPT_TIKTOK))
        assertTrue(script.contains("[0-3 s] Apertura"))
        assertTrue(script.contains("[Chiusura]"))
        assertTrue(script.contains("Testo in sovrimpressione"))
        script.lines().filter { it.startsWith("- ") }.forEach { assertTrue(it, it.split(" ").size <= 14) }
    }

    @Test
    fun `il carosello propone una sequenza di schede e la storia tre schermate`() {
        val carosello = GeneratoreBozza.genera(fatto, OpzioniContenuto(piattaforma = Piattaforma.INSTAGRAM, formato = Formato.CAROSELLO))
        assertTrue(carosello.contains("Scheda 1 (copertina)"))
        assertTrue(carosello.contains("Scheda 2:"))
        assertTrue(carosello.contains("(chiusura)"))
        assertTrue(carosello.contains("Didascalia:"))
        val storia = GeneratoreBozza.genera(fatto, OpzioniContenuto(piattaforma = Piattaforma.INSTAGRAM, formato = Formato.STORIA))
        assertTrue(storia.contains("Schermata 1") && storia.contains("Schermata 2") && storia.contains("Schermata 3"))
        val foto = GeneratoreBozza.genera(fatto, OpzioniContenuto(piattaforma = Piattaforma.TIKTOK, formato = Formato.FOTO_CAROSELLO))
        assertTrue(foto.contains("Foto 1 (copertina)"))
    }

    @Test
    fun `tono, emoji, hashtag, lunghezza e invito seguono le opzioni`() {
        val base = OpzioniContenuto(piattaforma = Piattaforma.FACEBOOK, formato = Formato.POST)
        val informale = GeneratoreBozza.genera(fatto, base)
        assertTrue(informale.contains("Sapevi che"))
        assertTrue(informale.contains("🕰️"))
        assertTrue(informale.contains("#FriuliVeneziaGiulia"))
        assertTrue(informale.contains("Lo sapevi? Raccontalo nei commenti"))

        val sobrio = GeneratoreBozza.genera(fatto, base.copy(tono = Tono.ISTITUZIONALE, emoji = false, hashtag = false, invitoAzione = "Vieni in biblioteca sabato"))
        assertFalse(sobrio.contains("Sapevi che"))
        assertFalse(sobrio.contains("🕰️"))
        assertFalse(sobrio.contains("#"))
        assertTrue(sobrio.contains("Vieni in biblioteca sabato"))

        val breve = GeneratoreBozza.genera(fatto, base.copy(lunghezza = Lunghezza.BREVE))
        assertFalse(breve.contains("due ore"))
        val lunga = GeneratoreBozza.genera(fatto, base.copy(lunghezza = Lunghezza.LUNGA))
        assertTrue(lunga.contains("due ore") && lunga.contains("100 metri"))
        assertFalse(informale.contains("100 metri")) // la lunghezza media lascia fuori l'estratto
    }

    @Test
    fun `la bozza spiega il legame con la data per ogni tipo di collegamento`() {
        val nascita = Prova.fatto(titolo = "Tina Modotti", testo = "Fotografa.", collegamento = Collegamento.NATO, categoria = Categoria.NASCITA, anno = 1896)
        assertTrue(GeneratoreBozza.genera(nascita, OpzioniContenuto()).contains("nato/a il 22 ottobre 1896"))
        val ricorrenza = Prova.fatto(titolo = "Giorno di prova", testo = "Si celebra.", collegamento = Collegamento.RICORRENZA, categoria = Categoria.RICORRENZA, anno = null)
        assertTrue(GeneratoreBozza.genera(ricorrenza, OpzioniContenuto()).contains("ricorrenza celebrata il 22 ottobre"))
    }

    @Test
    fun `il prompt riporta il fatto, le fonti, le regole e la bozza`() {
        val o = OpzioniContenuto(piattaforma = Piattaforma.LINKEDIN, formato = Formato.CAROSELLO, tono = Tono.IRONICO, pubblico = "amministratori locali")
        val bozza = GeneratoreBozza.genera(fatto, o)
        val prompt = GeneratorePrompt.genera(fatto, o, bozza)
        listOf(
            "LinkedIn", "Carosello", "Ironico", "amministratori locali", fatto.titolo, url,
            "Non aggiungere fatti", "avvenuto il 22 ottobre 1901", "consultata il 2026-10-22",
            "Da Wikipedia: controllare le note", bozza, "Controllo fonti", "Rispetta il limite di 3000 caratteri",
        ).forEach { assertTrue(it, prompt.contains(it)) }
        assertFalse(prompt.contains("traducilo"))
        val inglese = GeneratorePrompt.genera(Prova.fatto(lingua = "en"), o, bozza)
        assertTrue(inglese.contains("traducilo in italiano"))
    }
}
