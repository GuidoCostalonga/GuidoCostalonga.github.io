package org.costalonga.fattidelgiorno

import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.Fonte
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica

/** Oggetti comuni alle prove. */
object Prova {
    fun risorsa(nome: String): String =
        checkNotNull(Prova::class.java.classLoader?.getResourceAsStream(nome)) { "risorsa mancante: $nome" }
            .bufferedReader().use { it.readText() }

    fun fatto(
        id: String = "prova-1",
        titolo: String = "Inaugurazione del ponte di prova",
        testo: String = "Il ponte di prova viene inaugurato a Trieste. La cerimonia dura due ore.\n\nDalla voce collegata: Il ponte è lungo 100 metri.",
        anno: Int? = 1901,
        collegamento: Collegamento = Collegamento.AVVENUTO,
        categoria: Categoria = Categoria.EVENTO,
        area: Area = Area.FVG,
        lingua: String = "it",
        entita: String? = null,
        verifica: LivelloVerifica = LivelloVerifica.MEDIO,
        fonti: List<Fonte> = listOf(Fonte("Wikipedia (it): Ponte di prova", "https://it.wikipedia.org/wiki/Ponte_di_prova", "2026-10-22")),
    ) = Fatto(
        id = id, titolo = titolo, testo = testo, giorno = 22, mese = 10, anno = anno,
        collegamento = collegamento, categoria = categoria, area = area, fonti = fonti,
        verifica = verifica, lingua = lingua, origine = "Prova", entita = entita,
    )
}
