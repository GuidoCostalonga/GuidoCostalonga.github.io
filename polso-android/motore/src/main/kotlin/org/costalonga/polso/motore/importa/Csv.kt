package org.costalonga.polso.motore.importa

/** Lettore e scrittore CSV (RFC 4180) con riconoscimento del separatore. */
object Csv {
    fun separatore(intestazione: String): Char {
        val candidati = listOf(';', ',', '\t')
        return candidati.maxBy { c -> intestazione.count { it == c } }
    }

    /** Divide il testo in righe di campi, gestendo virgolette e a capo nei campi. */
    fun leggi(testo: String, sep: Char = separatore(testo.lineSequence().firstOrNull().orEmpty())): List<List<String>> {
        val righe = mutableListOf<List<String>>()
        var campo = StringBuilder()
        var riga = mutableListOf<String>()
        var virgolette = false
        var i = 0
        val t = testo.removePrefix("﻿")
        while (i < t.length) {
            val ch = t[i]
            if (virgolette) {
                if (ch == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { campo.append('"'); i++ } else virgolette = false
                } else campo.append(ch)
            } else when (ch) {
                '"' -> virgolette = true
                sep -> { riga += campo.toString(); campo = StringBuilder() }
                '\r' -> {}
                '\n' -> { riga += campo.toString(); campo = StringBuilder(); righe += riga; riga = mutableListOf() }
                else -> campo.append(ch)
            }
            i++
        }
        if (campo.isNotEmpty() || riga.isNotEmpty()) { riga += campo.toString(); righe += riga }
        return righe.filter { r -> r.any { it.isNotBlank() } }
    }

    fun campo(v: String, sep: Char = ';'): String =
        if (v.any { it == sep || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun riga(valori: List<String>, sep: Char = ';'): String = valori.joinToString(sep.toString()) { campo(it, sep) }
}
