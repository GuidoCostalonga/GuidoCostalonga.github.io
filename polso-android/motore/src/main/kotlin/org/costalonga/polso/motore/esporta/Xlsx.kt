package org.costalonga.polso.motore.esporta

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Scrittore minimo di file Excel (.xlsx, Office Open XML SpreadsheetML).
 * Celle di testo (stringhe in linea) e numeri; prima riga in grassetto e
 * bloccata; larghezza colonne indicativa. Nessuna libreria esterna.
 */
class Xlsx {
    sealed interface Cella { data class Testo(val v: String) : Cella; data class Numero(val v: Double) : Cella; data object Vuota : Cella }

    class Foglio(val nome: String) {
        val righe = mutableListOf<List<Cella>>()
        fun intestazione(vararg c: String) { righe += c.map { Cella.Testo(it) } }
        fun riga(vararg valori: Any?) {
            righe += valori.map {
                when (it) {
                    null -> Cella.Vuota
                    is Number -> it.toDouble().let { d -> if (d.isNaN() || d.isInfinite()) Cella.Vuota else Cella.Numero(d) }
                    else -> Cella.Testo(it.toString())
                }
            }
        }
    }

    val fogli = mutableListOf<Foglio>()

    fun foglio(nome: String): Foglio {
        // Excel: nome massimo 31 caratteri, niente []:*?/\
        val pulito = nome.replace(Regex("[\\[\\]:*?/\\\\]"), " ").take(31)
        return Foglio(pulito).also { fogli += it }
    }

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch.code < 0x20 && ch != '\n' && ch != '\t' -> {}
            else -> append(ch)
        }
    }

    fun colonna(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    fun scrivi(out: OutputStream) {
        require(fogli.isNotEmpty())
        ZipOutputStream(out).use { z ->
            fun file(nome: String, testo: String) { z.putNextEntry(ZipEntry(nome)); z.write(testo.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            file("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                fogli.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            file("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            file("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                fogli.mapIndexed { i, f -> """<sheet name="${esc(f.nome)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets></workbook>")
            file("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                fogli.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${fogli.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            file("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            fogli.forEachIndexed { i, f ->
                val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
                val nCol = f.righe.maxOfOrNull { it.size } ?: 0
                if (nCol > 0) {
                    sb.append("<cols>")
                    for (c in 0 until nCol) {
                        val larg = f.righe.take(200).maxOf { r -> when (val x = r.getOrNull(c)) { is Cella.Testo -> x.v.length; is Cella.Numero -> 12; else -> 4 } }.coerceIn(8, 60)
                        sb.append("""<col min="${c + 1}" max="${c + 1}" width="${larg + 2}" customWidth="1"/>""")
                    }
                    sb.append("</cols>")
                }
                sb.append("<sheetData>")
                f.righe.forEachIndexed { r, riga ->
                    sb.append("""<row r="${r + 1}">""")
                    riga.forEachIndexed { c, cella ->
                        val ref = "${colonna(c)}${r + 1}"
                        val s = if (r == 0) " s=\"1\"" else ""
                        when (cella) {
                            is Cella.Testo -> sb.append("""<c r="$ref" t="inlineStr"$s><is><t xml:space="preserve">${esc(cella.v)}</t></is></c>""")
                            is Cella.Numero -> sb.append("""<c r="$ref"$s><v>${cella.v}</v></c>""")
                            Cella.Vuota -> {}
                        }
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                file("xl/worksheets/sheet${i + 1}.xml", sb.toString())
            }
        }
    }
}
