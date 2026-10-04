package org.costalonga.sportintv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.costalonga.sportintv.dati.inCorso
import org.costalonga.sportintv.dati.inOnda
import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.StatoEvento
import org.costalonga.sportintv.raccolta.TipoTrasmissione
import java.time.Instant

/**
 * Apre il link ufficiale: se l'app del servizio è installata e gestisce
 * quell'indirizzo, Android la propone; altrimenti si apre il browser.
 */
fun apriLink(context: Context, url: String) {
    val intento = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intento)
    } catch (_: ActivityNotFoundException) {
        // Nessuna app per aprire il link: improbabile, ma non deve chiudere l'app.
    }
}

@Composable
fun Etichetta(testo: String, sfondo: Color, colore: Color, icona: ImageVector? = null, modifier: Modifier = Modifier) {
    Surface(color = sfondo, contentColor = colore, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icona != null) {
                Icon(icona, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
            }
            Text(testo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EtichettaAccesso(a: Accesso) {
    val c = LocalColoriAccesso.current
    val (sfondo, testo) = when {
        a.gratuito -> c.gratis to c.suGratis
        a.aPagamento -> c.pagamento to c.suPagamento
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Etichetta(a.etichetta(), sfondo, testo)
}

@Composable
fun EtichettaInCorso(diretta: Boolean) {
    if (diretta) {
        Etichetta("In corso", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
    } else {
        Etichetta("In onda ora", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun CerchioSport(chiave: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(iconaSport(chiave), contentDescription = nomeSport(chiave), modifier = Modifier.size(24.dp))
        }
    }
}

/** Scheda di un evento nell'elenco: orario, titolo, sport, competizione, canali e accesso. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedaEvento(
    e: Evento,
    adesso: Instant,
    preferito: Boolean,
    salvato: Boolean,
    conPromemoria: Boolean,
    onClick: () -> Unit,
) {
    val inCorso = e.inCorso(adesso)
    val inOnda = e.inOnda(adesso)
    val descrizione = buildString {
        append(Formato.ora(e.inizio)).append(", ").append(e.titolo).append(". ")
        append(nomeSport(e.sport))
        e.competizione?.let { append(", ").append(it) }
        append(". ")
        if (inCorso) append("In corso. ")
        if (e.trasmissioni.isEmpty()) append("Trasmissione in Italia da confermare. ")
        else append("Su ").append(e.trasmissioni.map { it.canale }.distinct().joinToString(", ")).append(". ")
        if (e.stato == StatoEvento.RINVIATO || e.stato == StatoEvento.ANNULLATO) append(e.stato.etichetta()).append(". ")
        if (preferito) append("Tra i preferiti. ")
        if (conPromemoria) append("Promemoria attivo.")
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = descrizione },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Formato.ora(e.inizio),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                CerchioSport(e.sport)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(nomeSport(e.sport), e.competizione).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(e.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (inCorso || inOnda) EtichettaInCorso(inCorso)
                    when (e.stato) {
                        StatoEvento.RINVIATO, StatoEvento.ANNULLATO ->
                            Etichetta(e.stato.etichetta(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                        else -> Unit
                    }
                    if (e.trasmissioni.isEmpty()) {
                        Etichetta("Da confermare", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    if (e.trasmissioni.any { it.tipo == TipoTrasmissione.DIRETTA }) {
                        Etichetta("Diretta", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    } else if (e.trasmissioni.isNotEmpty() && e.trasmissioni.all { it.tipo == TipoTrasmissione.REPLICA }) {
                        Etichetta("Replica", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (t in e.trasmissioni.distinctBy { it.canale }.take(4)) {
                        val c = LocalColoriAccesso.current
                        val (sfondo, testo) = if (t.accesso.gratuito) c.gratis to c.suGratis else if (t.accesso.aPagamento) c.pagamento to c.suPagamento
                        else MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        Etichetta(t.canale, sfondo, testo)
                    }
                    if (e.trasmissioni.distinctBy { it.canale }.size > 4) {
                        Text("+${e.trasmissioni.distinctBy { it.canale }.size - 4}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (e.incertezze.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Informazioni discordanti fra le fonti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (preferito) Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                if (salvato) Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                if (conPromemoria) Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IntestazioneGiorno(testo: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Text(
            testo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
    }
}

/** Riquadro informativo: avvisi su dati superati, fonti in difficoltà, spiegazioni. */
@Composable
fun Riquadro(
    titolo: String,
    testo: String,
    modifier: Modifier = Modifier,
    icona: ImageVector = Icons.Filled.Info,
    avviso: Boolean = false,
    azione: Pair<String, () -> Unit>? = null,
) {
    val sfondo = if (avviso) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val colore = if (avviso) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = sfondo, contentColor = colore, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icona, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titolo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(testo, style = MaterialTheme.typography.bodyMedium)
                if (azione != null) {
                    OutlinedButton(onClick = azione.second) { Text(azione.first) }
                }
            }
        }
    }
}

/** Stato vuoto: distingue "nessun evento" da "dati non disponibili". */
@Composable
fun StatoVuoto(titolo: String, testo: String, nessunDato: Boolean, azione: Pair<String, () -> Unit>?) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (nessunDato) Icons.Filled.CloudOff else Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(titolo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(testo, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (azione != null) Button(onClick = azione.second) { Text(azione.first) }
    }
}
