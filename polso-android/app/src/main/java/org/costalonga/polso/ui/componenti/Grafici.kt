package org.costalonga.polso.ui.componenti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.analisi.Grafico
import java.time.LocalDate
import kotlin.math.abs

/**
 * Grafici disegnati con Canvas di Compose. Ogni grafico:
 * - indica unità e titolo;
 * - lascia vuoti i giorni senza dati (linea interrotta, barra assente) e lo dice;
 * - si tocca per leggere il valore esatto, scritto in chiaro sotto il titolo;
 * - ha una descrizione per i lettori di schermo.
 */
@Composable
fun GraficoAnalisi(g: Grafico, modifier: Modifier = Modifier) {
    when (g) {
        is Grafico.Linea -> GraficoLinea(g, modifier)
        is Grafico.Barre -> GraficoBarre(g, modifier)
        is Grafico.Dispersione -> GraficoDispersione(g, modifier)
        is Grafico.Calendario -> Calendario(g, modifier)
    }
}

private val coloriParti = listOf(Color(0xFF2B6CB0), Color(0xFF7BAFD4), Color(0xFF8E6BBF), Color(0xFFE0A030), Color(0xFF999999))

@Composable
private fun Intestazione(titolo: String, unita: String, selezione: String?, mancanti: Int) {
    Text("$titolo${if (unita.isNotBlank()) " ($unita)" else ""}", style = MaterialTheme.typography.labelLarge)
    Text(
        selezione ?: if (mancanti > 0) "Tocca il grafico per leggere i valori · $mancanti giorni senza dati (vuoti)" else "Tocca il grafico per leggere i valori",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun GraficoLinea(g: Grafico.Linea, modifier: Modifier = Modifier) {
    var sel by remember(g) { mutableStateOf<Int?>(null) }
    val col = MaterialTheme.colorScheme.primary
    val col2 = MaterialTheme.colorScheme.secondary
    val assi = MaterialTheme.colorScheme.outline
    val valori = g.punti.map { it.second }
    val presenti = valori.filterNotNull() + g.mediaMobile.filterNotNull() + listOfNotNull(g.riferimento)
    val mancanti = valori.count { it == null }
    Column(modifier) {
        Intestazione(g.titolo, g.unita, sel?.let { i -> "${Tempo.etichetta(g.punti[i].first)}: ${valori[i]?.let { Formato.numero(it, if (it < 100) 1 else 0) + " " + g.unita } ?: "nessun dato"}" + (g.mediaMobile.getOrNull(i)?.let { " · linea secondaria ${Formato.numero(it, 1)}" } ?: "") }, mancanti)
        if (presenti.isEmpty()) { Text("Nessun dato nel periodo.", style = MaterialTheme.typography.bodyMedium); return@Column }
        val max = presenti.max()
        val min = presenti.min().let { if (it > 0 && g.unita in setOf("passi", "m", "kcal", "min")) 0.0 else it }
        val ampiezza = (max - min).takeIf { it > 0 } ?: 1.0
        Row {
            Column(Modifier.width(44.dp).height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Formato.numero(max), style = MaterialTheme.typography.labelSmall)
                Text(Formato.numero(min), style = MaterialTheme.typography.labelSmall)
            }
            Canvas(
                Modifier.fillMaxWidth().height(160.dp)
                    .semantics { contentDescription = "${g.titolo}: ${presenti.size} valori, da ${Formato.numero(valori.filterNotNull().minOrNull())} a ${Formato.numero(valori.filterNotNull().maxOrNull())} ${g.unita}; $mancanti giorni senza dati." }
                    .pointerInput(g) { detectTapGestures { o -> sel = ((o.x / size.width) * valori.size).toInt().coerceIn(0, valori.size - 1) } },
            ) {
                val n = valori.size.coerceAtLeast(1)
                val passo = size.width / n
                fun x(i: Int) = passo * (i + 0.5f)
                fun y(v: Double) = (size.height - ((v - min) / ampiezza) * size.height).toFloat()
                drawLine(assi, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                g.riferimento?.let { r -> drawLine(col2, Offset(0f, y(r)), Offset(size.width, y(r)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
                var path: Path? = null
                valori.forEachIndexed { i, v ->
                    if (v == null) { path?.let { drawPath(it, col, style = Stroke(3f)) }; path = null }
                    else {
                        if (path == null) path = Path().apply { moveTo(x(i), y(v)) } else path!!.lineTo(x(i), y(v))
                        drawCircle(col, 4f, Offset(x(i), y(v)))
                    }
                }
                path?.let { drawPath(it, col, style = Stroke(3f)) }
                var mm: Path? = null
                g.mediaMobile.forEachIndexed { i, v ->
                    if (v == null) { mm?.let { drawPath(it, col2, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))) }; mm = null }
                    else if (mm == null) mm = Path().apply { moveTo(x(i), y(v)) } else mm!!.lineTo(x(i), y(v))
                }
                mm?.let { drawPath(it, col2, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))) }
                sel?.let { drawLine(assi, Offset(x(it), 0f), Offset(x(it), size.height), 1.5f) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.punti.firstOrNull()?.first?.let { Tempo.etichettaBreve(it) } ?: "", style = MaterialTheme.typography.labelSmall)
            Text(g.punti.lastOrNull()?.first?.let { Tempo.etichettaBreve(it) } ?: "", style = MaterialTheme.typography.labelSmall)
        }
        Legenda(listOfNotNull("— valori" to col, if (g.mediaMobile.isNotEmpty()) "- - linea secondaria (media mobile o tendenza)" to col2 else null,
            g.riferimento?.let { "- - ${g.etichettaRiferimento ?: "riferimento"} ${Formato.numero(it)}" to col2 }))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legenda(voci: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        voci.forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun GraficoBarre(g: Grafico.Barre, modifier: Modifier = Modifier) {
    var sel by remember(g) { mutableStateOf<Int?>(null) }
    val col = MaterialTheme.colorScheme.primary
    val col2 = MaterialTheme.colorScheme.secondary
    val assi = MaterialTheme.colorScheme.outline
    val valori = g.barre.map { it.second }
    val mancanti = valori.count { it == null }
    Column(modifier) {
        Intestazione(g.titolo, g.unita, sel?.let { i ->
            "${g.barre[i].first}: ${valori[i]?.let { Formato.numero(it, if (it < 10) 2 else 0) + " " + g.unita } ?: "nessun dato"}" +
                g.parti.joinToString("") { (nome, v) -> v.getOrNull(i)?.let { " · $nome ${Formato.numero(it, 2)}" } ?: "" }
        }, mancanti)
        val presenti = valori.filterNotNull() + listOfNotNull(g.riferimento)
        if (presenti.isEmpty()) { Text("Nessun dato nel periodo.", style = MaterialTheme.typography.bodyMedium); return@Column }
        val max = presenti.max().takeIf { it > 0 } ?: 1.0
        Row {
            Column(Modifier.width(44.dp).height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Formato.numero(max, if (max < 10) 1 else 0), style = MaterialTheme.typography.labelSmall)
                Text("0", style = MaterialTheme.typography.labelSmall)
            }
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .semantics { contentDescription = "${g.titolo}: " + g.barre.joinToString("; ") { "${it.first} ${it.second?.let { v -> Formato.numero(v, 1) } ?: "nessun dato"}" } }
                    .pointerInput(g) { detectTapGestures { o -> sel = ((o.x / size.width) * valori.size).toInt().coerceIn(0, valori.size - 1) } },
            ) {
                val n = valori.size.coerceAtLeast(1)
                val passo = size.width / n
                val larg = passo * 0.7f
                drawLine(assi, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                valori.forEachIndexed { i, v ->
                    val x = passo * i + (passo - larg) / 2
                    if (v == null) {
                        // giorno senza dati: segno tratteggiato alla base, non una barra a zero
                        drawLine(assi, Offset(x, size.height - 2f), Offset(x + larg, size.height - 2f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                    } else if (g.parti.isNotEmpty() && g.parti.any { it.second.getOrNull(i) != null }) {
                        var base = size.height
                        g.parti.forEachIndexed { k, (_, pv) ->
                            val h = ((pv.getOrNull(i) ?: 0.0) / max * size.height).toFloat()
                            drawRect(coloriParti[k % coloriParti.size], Offset(x, base - h), Size(larg, h))
                            base -= h
                        }
                    } else {
                        val h = (v / max * size.height).toFloat()
                        drawRect(if (sel == i) col2 else col, Offset(x, size.height - h), Size(larg, h))
                    }
                }
                g.riferimento?.let { r -> val y = (size.height - r / max * size.height).toFloat(); drawLine(col2, Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
            }
        }
        if (g.barre.size <= 12) Row(Modifier.fillMaxWidth().padding(start = 44.dp)) {
            g.barre.forEach { Text(it.first, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
        } else Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.barre.first().first, style = MaterialTheme.typography.labelSmall); Text(g.barre.last().first, style = MaterialTheme.typography.labelSmall)
        }
        val leg = g.parti.mapIndexed { k, p -> p.first to coloriParti[k % coloriParti.size] } + listOfNotNull(g.riferimento?.let { "- - riferimento ${Formato.numero(it, 1)}" to col2 })
        if (leg.isNotEmpty()) Legenda(leg)
    }
}

@Composable
fun GraficoDispersione(g: Grafico.Dispersione, modifier: Modifier = Modifier) {
    var sel by remember(g) { mutableStateOf<Int?>(null) }
    val col = MaterialTheme.colorScheme.primary
    val assi = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Intestazione(g.titolo, g.unita, sel?.let { "${g.etichettaX}: ${Formato.numero(g.punti[it].first, 1)} · ${g.etichettaY}: ${Formato.numero(g.punti[it].second, 1)}" }, 0)
        if (g.punti.isEmpty()) { Text("Nessun dato."); return@Column }
        val minX = g.punti.minOf { it.first }; val maxX = g.punti.maxOf { it.first }
        val minY = g.punti.minOf { it.second }; val maxY = g.punti.maxOf { it.second }
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = "Dispersione di ${g.punti.size} punti: ${g.etichettaX} contro ${g.etichettaY}" }
                .pointerInput(g) {
                    detectTapGestures { o ->
                        sel = g.punti.indices.minByOrNull { i ->
                            val px = ((g.punti[i].first - minX) / (maxX - minX).coerceAtLeast(1e-9) * size.width).toFloat()
                            val py = (size.height - (g.punti[i].second - minY) / (maxY - minY).coerceAtLeast(1e-9) * size.height).toFloat()
                            abs(px - o.x) + abs(py - o.y)
                        }
                    }
                },
        ) {
            drawLine(assi, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            drawLine(assi, Offset(0f, 0f), Offset(0f, size.height), 1f)
            g.punti.forEachIndexed { i, (a, b) ->
                val x = ((a - minX) / (maxX - minX).coerceAtLeast(1e-9) * size.width).toFloat()
                val y = (size.height - (b - minY) / (maxY - minY).coerceAtLeast(1e-9) * size.height).toFloat()
                drawCircle(col.copy(alpha = if (sel == i) 1f else 0.55f), if (sel == i) 8f else 5f, Offset(x, y))
            }
        }
        Text("Orizzontale: ${g.etichettaX} (${Formato.numero(minX, 0)}–${Formato.numero(maxX, 0)}) · Verticale: ${g.etichettaY} (${Formato.numero(minY, 0)}–${Formato.numero(maxY, 0)})", style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Calendario con intensità per giorno: oltre al colore, ogni giorno mostra
 * un livello da 0 a 4 a puntini e i giorni senza dati hanno il bordo
 * tratteggiato, così l'informazione non dipende solo dal colore.
 */
@Composable
fun Calendario(g: Grafico.Calendario, modifier: Modifier = Modifier) {
    var sel by remember(g) { mutableStateOf<LocalDate?>(null) }
    val base = MaterialTheme.colorScheme.primary
    val vuoto = MaterialTheme.colorScheme.outline
    val valori = g.valori.values.filterNotNull()
    val max = valori.maxOrNull() ?: 1.0
    Column(modifier) {
        Intestazione(g.titolo, g.unita, sel?.let { d -> "${Tempo.etichetta(d)}: ${g.valori[d]?.let { Formato.numero(it) + " " + g.unita } ?: "nessun dato"}" }, g.valori.values.count { it == null })
        Row { listOf("L", "M", "M", "G", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        val primo = g.da.minusDays((g.da.dayOfWeek.value - 1).toLong())
        val giorni = Tempo.giorni(primo, g.a)
        giorni.chunked(7).forEach { settimana ->
            Row {
                settimana.forEach { d ->
                    val v = g.valori[d]
                    val livello = if (v == null) -1 else ((v / max) * 4).toInt().coerceIn(0, 4)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                            .background(if (d < g.da || v == null) Color.Transparent else base.copy(alpha = 0.15f + 0.2f * livello), RoundedCornerShape(4.dp))
                            .then(if (d >= g.da && v == null) Modifier.border(1.dp, vuoto.copy(alpha = 0.6f), RoundedCornerShape(4.dp)) else Modifier)
                            .pointerInput(d) { detectTapGestures { sel = d } }
                            .semantics { contentDescription = "${Tempo.etichetta(d)}: ${v?.let { Formato.numero(it) } ?: "nessun dato"}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d >= g.da) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                            Text(if (v == null) "–" else "•".repeat(livello + 1), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(7 - settimana.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("Puntini = intensità rispetto al giorno migliore del periodo (1-5); «–» e bordo = nessun dato.", style = MaterialTheme.typography.labelSmall)
    }
}
