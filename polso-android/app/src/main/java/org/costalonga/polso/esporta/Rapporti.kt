package org.costalonga.polso.esporta

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import org.costalonga.polso.dati.Archivio
import org.costalonga.polso.dati.ArchivioImpostazioni
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Contesto
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Evidenze
import org.costalonga.polso.motore.analisi.Grafico
import org.costalonga.polso.motore.analisi.Provenienza
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.motore.backup.Backup
import org.costalonga.polso.motore.backup.ContenutoBackup
import org.costalonga.polso.motore.backup.RiepilogoSalvato
import org.costalonga.polso.dati.DiarioEntita
import org.costalonga.polso.dati.PolsoDb
import org.costalonga.polso.dati.RiepilogoEntita
import org.costalonga.polso.motore.importa.RisultatoImportazione
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Rapporto PDF in formato A4 con riepilogo, grafici, fonti, copertura e
 * limiti. Disegnato con PdfDocument di Android: nessuna libreria esterna.
 */
/** Dove si disegnano le pagine: PdfDocument sul telefono, una bitmap nelle prove. */
interface Fogli {
    fun nuova(numero: Int, larghezza: Int, altezza: Int): Canvas
    fun chiudi()
    fun scrivi(out: OutputStream)
}

class FogliPdf : Fogli {
    private val doc = PdfDocument()
    private var pagina: PdfDocument.Page? = null
    override fun nuova(numero: Int, larghezza: Int, altezza: Int): Canvas =
        doc.startPage(PdfDocument.PageInfo.Builder(larghezza, altezza, numero).create()).also { pagina = it }.canvas
    override fun chiudi() { pagina?.let { doc.finishPage(it) }; pagina = null }
    override fun scrivi(out: OutputStream) { doc.writeTo(out); doc.close() }
}

class RapportoPdf(private val d: Dati, private val p: Periodo, private val oggi: LocalDate, private val sezioni: Set<Sezione>, private val demo: Boolean, private val fogli: Fogli = FogliPdf()) {
    private val L = 595f; private val H = 842f; private val M = 40f
    private var aperta = false
    var pagine = 0
        private set
    private var canvas: Canvas? = null
    private var y = 0f
    private var numero = 0
    private val testo = Paint().apply { color = Color.rgb(25, 30, 30); textSize = 9.5f; isAntiAlias = true }
    private val titolo = Paint(testo).apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val sottotitolo = Paint(testo).apply { textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(14, 92, 99) }
    private val grigio = Paint(testo).apply { color = Color.rgb(95, 105, 105); textSize = 8.5f }
    private val linea = Paint().apply { color = Color.rgb(14, 92, 99); strokeWidth = 1.6f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val riempi = Paint().apply { color = Color.rgb(14, 92, 99); style = Paint.Style.FILL; isAntiAlias = true }
    private val assi = Paint().apply { color = Color.rgb(190, 198, 198); strokeWidth = 0.6f }

    private fun nuovaPagina() {
        if (aperta) chiudiPagina()
        numero++
        canvas = fogli.nuova(numero, L.toInt(), H.toInt())
        aperta = true
        pagine = numero
        y = M
    }

    private fun chiudiPagina() {
        canvas!!.drawText("Polso · ${p.descrizione()} · pagina $numero" + if (demo) " · DATI DIMOSTRATIVI SINTETICI" else "", M, H - 20f, grigio)
        fogli.chiudi()
        aperta = false
    }

    private fun spazio(h: Float) { if (y + h > H - 45) nuovaPagina() }

    private fun paragrafo(s: String, paint: Paint = testo, rientro: Float = 0f) {
        val larg = L - 2 * M - rientro
        val parole = s.split(" ")
        var riga = ""
        fun scrivi(r: String) { spazio(paint.textSize + 3); canvas!!.drawText(r, M + rientro, y + paint.textSize, paint); y += paint.textSize + 3 }
        for (w in parole) {
            val prova = if (riga.isEmpty()) w else "$riga $w"
            if (paint.measureText(prova) > larg && riga.isNotEmpty()) { scrivi(riga); riga = w } else riga = prova
        }
        if (riga.isNotEmpty()) scrivi(riga)
    }

    private fun grafico(g: Grafico) {
        val h = 120f
        spazio(h + 30)
        paragrafo("${g.titolo} (${g.unita})", grigio)
        val c = canvas!!
        val x0 = M + 30; val x1 = L - M; val y0 = y + 4; val y1 = y + h
        val valori: List<Double?> = when (g) {
            is Grafico.Linea -> g.punti.map { it.second }
            is Grafico.Barre -> g.barre.map { it.second }
            else -> emptyList()
        }
        val presenti = valori.filterNotNull() + listOfNotNull((g as? Grafico.Linea)?.riferimento, (g as? Grafico.Barre)?.riferimento)
        if (presenti.isEmpty()) { y += h; return }
        val max = presenti.max().let { if (it <= 0) 1.0 else it * 1.08 }
        val min = minOf(0.0, presenti.min())
        fun py(v: Double) = (y1 - (v - min) / (max - min) * (y1 - y0)).toFloat()
        c.drawLine(x0, y1, x1, y1, assi); c.drawLine(x0, y0, x0, y1, assi)
        c.drawText(Formato.numero(max), M - 4, y0 + 8, grigio); c.drawText(Formato.numero(min), M - 4, y1, grigio)
        val n = valori.size.coerceAtLeast(1)
        val passo = (x1 - x0) / n
        when (g) {
            is Grafico.Linea -> {
                var path: Path? = null
                valori.forEachIndexed { i, v ->
                    val x = x0 + passo * (i + 0.5f)
                    if (v == null) { path?.let { c.drawPath(it, linea) }; path = null } else {
                        if (path == null) path = Path().apply { moveTo(x, py(v)) } else path!!.lineTo(x, py(v))
                        c.drawCircle(x, py(v), 1.6f, riempi)
                    }
                }
                path?.let { c.drawPath(it, linea) }
                g.riferimento?.let { r -> c.drawLine(x0, py(r), x1, py(r), Paint(assi).apply { color = Color.rgb(200, 120, 40); pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) }) }
            }
            is Grafico.Barre -> valori.forEachIndexed { i, v -> if (v != null) c.drawRect(x0 + passo * i + passo * 0.15f, py(v), x0 + passo * (i + 0.85f), y1, riempi) }
            else -> {}
        }
        y = y1 + 6
        paragrafo("Punti vuoti = giorni senza dati (non zero).", grigio)
        y += 4
    }

    fun scrivi(out: OutputStream) {
        nuovaPagina()
        canvas!!.drawText("Polso — rapporto dei dati dello smartwatch", M, y + 18, titolo); y += 28
        paragrafo("Periodo: ${p.descrizione()}${if (p.parziale(oggi)) " (in corso: l'ultimo giorno è parziale)" else ""}. Fuso orario: ${d.zona.id}. Generato il ${LocalDateTime.now(d.zona).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}.")
        if (demo) paragrafo("ATTENZIONE: questo rapporto usa dati dimostrativi sintetici, non misure reali.", sottotitolo)
        paragrafo("Legenda: «fonte» = misura originale (per esempio HONOR Health tramite Health Connect); «calcolato» = valore elaborato dall'app con il metodo indicato. Nessuna interpretazione dell'IA è inclusa.", grigio)
        y += 6
        val c = Contesto(d, p, oggi)
        val ev = Evidenze.calcola(c)
        if (ev.isNotEmpty()) {
            spazio(30f); paragrafo("Cosa emerge (regole fisse)", sottotitolo)
            ev.forEach { paragrafo("• ${it.titolo}: ${it.testo}") }
            paragrafo(Evidenze.CRITERI, grigio)
            y += 6
        }
        val nonDisp = mutableListOf<String>()
        for (s in Sezione.entries.filter { it in sezioni }) {
            val analisi = Catalogo.perSezione(s)
            spazio(40f); paragrafo(s.nome, sottotitolo)
            for (a in analisi) when (val e = a.esegui(c)) {
                is Esito.Disponibile -> {
                    spazio(30f)
                    paragrafo(a.def.titolo + " — dati in ${e.copertura.giorniConDati} giorni su ${e.copertura.giorniNelPeriodo}", Paint(testo).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
                    e.voci.forEach { v -> paragrafo("${v.etichetta}: ${v.testo}${if (v.provenienza == Provenienza.FONTE) " [fonte]" else " [calcolato]"}", rientro = 8f) }
                    e.grafici.firstOrNull { it is Grafico.Linea || it is Grafico.Barre }?.let { grafico(it) }
                    paragrafo("Metodo: ${a.def.metodo}", grigio, 8f)
                    paragrafo("Limiti: ${a.def.limiti}", grigio, 8f)
                    y += 4
                }
                is Esito.NonDisponibile -> nonDisp += "${a.def.titolo}: ${e.motivo}"
            }
        }
        if (nonDisp.isNotEmpty()) {
            spazio(40f); paragrafo("Analisi non disponibili e requisiti mancanti", sottotitolo)
            nonDisp.forEach { paragrafo("• $it", grigio) }
        }
        spazio(60f); paragrafo("Limiti generali", sottotitolo)
        paragrafo("I dati di uno smartwatch sono stime per il benessere, non misure diagnostiche. Le relazioni fra grandezze sono associazioni e non dimostrano cause. Per qualsiasi dubbio sulla salute è necessario rivolgersi al medico.")
        if (aperta) chiudiPagina()
        fogli.scrivi(out)
    }
}

data class EsitoRipristino(val conteggi: Map<String, Int>, val verificato: Boolean, val dettaglio: String)

/** Backup cifrato e ripristino verificato dell'archivio reale. */
class GestoreBackup(private val context: Context, private val archivio: Archivio, private val impostazioni: ArchivioImpostazioni, private val versioneApp: String) {
    suspend fun contenuto(): ContenutoBackup {
        val dao = archivio.dao
        return ContenutoBackup(
            versioneSchema = PolsoDb.VERSIONE, versioneApp = versioneApp, creatoIl = System.currentTimeMillis(),
            misure = archivio.misure(Long.MIN_VALUE, Long.MAX_VALUE), sonni = archivio.sonni(Long.MIN_VALUE, Long.MAX_VALUE),
            allenamenti = archivio.allenamenti(Long.MIN_VALUE, Long.MAX_VALUE), diario = archivio.diario(),
            impostazioni = impostazioni.perBackup(),
            riepiloghi = dao.tuttiIRiepiloghi().map { RiepilogoSalvato(it.tipo, it.da, it.a, it.testo, it.motore, it.creatoIl) },
        )
    }

    suspend fun salva(uri: Uri, passphrase: CharArray): Map<String, Int> {
        val c = contenuto()
        val dati = Backup.cifra(c, passphrase)
        (context.contentResolver.openOutputStream(uri, "wt") ?: error("destinazione non scrivibile")).use { it.write(dati) }
        // Verifica immediata: il file scritto si rilegge e si decifra.
        val riletto = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val r = Backup.decifra(riletto, passphrase)
        check(Backup.impronta(r) == Backup.impronta(c)) { "Il file scritto non corrisponde all'archivio." }
        return c.conteggi()
    }

    /**
     * Ripristina sostituendo l'archivio attuale. Prima si decifra e controlla
     * il file; solo dopo si cancellano i dati presenti. Alla fine si rilegge
     * l'archivio e si confrontano conteggi e impronta.
     */
    suspend fun ripristina(uri: Uri, passphrase: CharArray): EsitoRipristino {
        val byte = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val c = Backup.decifra(byte, passphrase)
        if (c.versioneSchema > PolsoDb.VERSIONE) error("Backup creato con una versione più recente dell'archivio (${c.versioneSchema}).")
        val dao = archivio.dao
        dao.svuotaTutto()
        archivio.scrivi(RisultatoImportazione(c.misure, c.sonni, c.allenamenti), System.currentTimeMillis())
        dao.inserisciVoci(c.diario.map { DiarioEntita(0, it.tipo, it.istante, it.valore, it.valore2, it.testo, it.creataIl, it.modificataIl) })
        c.riepiloghi.forEach { dao.salvaRiepilogo(RiepilogoEntita(tipo = it.tipo, da = it.da, a = it.a, testo = it.testo, motore = it.motore, verificato = true, fatti = "", creatoIl = it.creatoIl)) }
        impostazioni.ripristina(c.impostazioni)
        val dopo = contenuto()
        val ok = Backup.impronta(dopo) == Backup.impronta(c) && dopo.conteggi()["misure"] == c.misure.distinctBy { it.chiave() }.size
        return EsitoRipristino(c.conteggi(), ok, if (ok) "Ripristino verificato: conteggi e impronta coincidono." else "Attenzione: l'archivio ripristinato non coincide del tutto con il backup.")
    }
}
