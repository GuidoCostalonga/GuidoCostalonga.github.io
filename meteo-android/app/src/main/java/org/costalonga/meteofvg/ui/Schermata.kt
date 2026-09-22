package org.costalonga.meteofvg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.costalonga.meteofvg.R
import org.costalonga.meteofvg.data.COMUNI_RAPIDI
import org.costalonga.meteofvg.data.Comune
import org.costalonga.meteofvg.data.Consiglio
import org.costalonga.meteofvg.data.Giorno
import org.costalonga.meteofvg.data.Ora
import org.costalonga.meteofvg.data.Previsione
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ORA = DateTimeFormatter.ofPattern("HH:mm")
private val SETTIMANA_CORTA = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)
private val GIORNO_LUNGO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

/** La schermata con tutto quello che la pagina mostra, nello stesso ordine. */
@Composable
fun SchermataMeteo(
    comune: Comune,
    previsione: Previsione?,
    caricamento: Boolean,
    errore: String?,
    giornoScelto: Int,
    onGiornoScelto: (Int) -> Unit,
    onComune: (Comune) -> Unit,
    onApriElenco: () -> Unit,
    onRicarica: () -> Unit,
) {
    Surface(color = Fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Testata(
                comune = comune,
                caricamento = caricamento,
                onComune = onComune,
                onApriElenco = onApriElenco,
                onRicarica = onRicarica,
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (errore != null) {
                    item { Errore(errore, onRicarica) }
                }

                if (previsione == null) {
                    if (errore == null) {
                        item { InAttesa() }
                    }
                } else {
                    item { RiquadroAdesso(previsione) }
                    item { RiquadroAttendibilita(previsione) }
                    item { RiquadroOre(previsione) }
                    item {
                        RiquadroGiorni(
                            previsione = previsione,
                            giornoScelto = giornoScelto,
                            onGiornoScelto = onGiornoScelto,
                        )
                    }
                    item { RiquadroConsigli(previsione.consigli) }
                }

                item {
                    Scheda(
                        titolo = "Allerta della Protezione Civile",
                        sottotitolo = "Riquadro ufficiale della Regione Friuli Venezia Giulia " +
                            "per ${comune.nome}",
                    ) {
                        RiquadroAllerte(comune)
                    }
                }

                item { Piede() }
            }
        }
    }
}

@Composable
private fun Testata(
    comune: Comune,
    caricamento: Boolean,
    onComune: (Comune) -> Unit,
    onApriElenco: () -> Unit,
    onRicarica: () -> Unit,
) {
    Surface(color = Blu) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "METEO FRIULI VENEZIA GIULIA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Giallo,
                    )
                    Text(
                        text = comune.nome,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontSize = 24.sp,
                    )
                    Text(
                        text = "Provincia di ${comune.provincia}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFADC0D0),
                    )
                }
                if (caricamento) {
                    CircularProgressIndicator(
                        color = Giallo,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onRicarica)
                            .padding(8.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_ricarica),
                            contentDescription = "Aggiorna",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COMUNI_RAPIDI.forEach { rapido ->
                    PastigliaComune(
                        testo = rapido.nome,
                        attiva = rapido.nome == comune.nome,
                        onClick = { onComune(rapido) },
                    )
                }
                PastigliaComune(testo = "Tutti", attiva = false, onClick = onApriElenco)
            }
        }
    }
}

@Composable
private fun PastigliaComune(testo: String, attiva: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (attiva) Giallo else Color(0x22FFFFFF))
            .border(
                1.dp,
                if (attiva) Giallo else Color(0x44FFFFFF),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = testo,
            style = MaterialTheme.typography.labelMedium,
            color = if (attiva) Blu else Color.White,
        )
    }
}

@Composable
private fun RiquadroAdesso(previsione: Previsione) {
    val condizione = previsione.condizione
    Scheda(titolo = "Adesso", sottotitolo = aggiornatoAlle(previsione.aggiornato)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconaAnimata(condizione.famiglia, 92.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${previsione.temperatura.roundToInt()}",
                        style = MaterialTheme.typography.displayMedium,
                        color = Testo,
                    )
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.headlineMedium,
                        color = GialloScuro,
                    )
                }
                Text(
                    text = condizione.descrizione,
                    style = MaterialTheme.typography.titleMedium,
                    color = Blu,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato("percepita", "${previsione.percepita.roundToInt()}°", Modifier.weight(1f))
            Dato("vento", "${previsione.vento.roundToInt()} km/h", Modifier.weight(1f))
            Dato("umidità", "${previsione.umidita}%", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RiquadroAttendibilita(previsione: Previsione) {
    Scheda(
        titolo = "Attendibilità a 24 ore",
        sottotitolo = "Quanto concordano fra loro i modelli europei",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${previsione.attendibilita}",
                style = MaterialTheme.typography.displaySmall,
                color = Blu,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = previsione.giudizio,
                    style = MaterialTheme.typography.titleSmall,
                    color = Testo,
                )
                Spacer(Modifier.height(6.dp))
                BarraValore(previsione.attendibilita / 100f)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = previsione.accordo,
            style = MaterialTheme.typography.bodyMedium,
            color = Testo2,
        )
        Spacer(Modifier.height(12.dp))
        previsione.confronti.forEachIndexed { indice, confronto ->
            if (indice > 0) HorizontalDivider(color = Bordo)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = confronto.modello,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Testo2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = confronto.esito,
                    style = MaterialTheme.typography.labelMedium,
                    color = Blu,
                )
            }
        }
    }
}

@Composable
private fun RiquadroOre(previsione: Previsione) {
    val ore = previsione.oreDaAdesso()
    Scheda(titolo = "Prossime ore", sottotitolo = "Diciotto ore, ora per ora") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(ore) { indice, ora ->
                ColonnaOra(ora, adesso = indice == 0)
            }
        }
    }
}

@Composable
private fun ColonnaOra(ora: Ora, adesso: Boolean) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (adesso) BluTenue else Superficie2)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (adesso) "adesso" else ora.quando.format(ORA),
            style = MaterialTheme.typography.labelSmall,
            color = if (adesso) Blu else Testo3,
        )
        Spacer(Modifier.height(4.dp))
        IconaMeteo(ora.condizione.famiglia, 34.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${ora.temperatura.roundToInt()}°",
            style = MaterialTheme.typography.titleMedium,
            color = Testo,
        )
        Text(
            text = "perc. ${ora.percepita.roundToInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = Testo3,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${ora.pioggia}% pioggia",
            style = MaterialTheme.typography.bodySmall,
            color = if (ora.pioggia > 55) BluChiaro else Testo3,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RiquadroGiorni(
    previsione: Previsione,
    giornoScelto: Int,
    onGiornoScelto: (Int) -> Unit,
) {
    val giorni = previsione.giorni
    val indice = giornoScelto.coerceIn(0, (giorni.size - 1).coerceAtLeast(0))
    Scheda(
        titolo = "Prossimi 7 giorni",
        sottotitolo = "Tocca un giorno per vedere il dettaglio ora per ora",
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(giorni) { posizione, giorno ->
                ColonnaGiorno(
                    giorno = giorno,
                    oggi = posizione == 0,
                    attivo = posizione == indice,
                    onClick = { onGiornoScelto(posizione) },
                )
            }
        }

        val giorno = giorni.getOrNull(indice)
        if (giorno != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Dettaglio orario · ${etichettaLunga(giorno.data)}",
                style = MaterialTheme.typography.labelSmall,
                color = Blu,
            )
            Spacer(Modifier.height(8.dp))
            val ore = previsione.oreDel(giorno.data)
            if (ore.isEmpty()) {
                Text(
                    text = "Il dettaglio ora per ora di questo giorno non è disponibile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Testo2,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ore) { ora -> ColonnaOra(ora, adesso = false) }
                }
            }
        }
    }
}

@Composable
private fun ColonnaGiorno(
    giorno: Giorno,
    oggi: Boolean,
    attivo: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (attivo) BluTenue else Superficie2)
            .border(
                1.dp,
                if (attivo) Blu else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (oggi) "Oggi" else giorno.data.format(SETTIMANA_CORTA).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (attivo) Blu else Testo2,
        )
        Spacer(Modifier.height(4.dp))
        IconaMeteo(giorno.condizione.famiglia, 34.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${giorno.pioggia}% pioggia",
            style = MaterialTheme.typography.bodySmall,
            color = if (giorno.pioggia > 55) BluChiaro else Testo3,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(2.dp))
        Row {
            Text(
                text = "${giorno.minima.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = Testo3,
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium,
                color = Bordo,
            )
            Text(
                text = "${giorno.massima.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = Testo,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun RiquadroConsigli(consigli: List<Consiglio>) {
    Scheda(titolo = "Da sapere", sottotitolo = "Tre indicazioni pratiche") {
        consigli.forEachIndexed { indice, consiglio ->
            if (indice > 0) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (consiglio.rilievo) Attenzione else Ok),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = consiglio.titolo,
                        style = MaterialTheme.typography.titleSmall,
                        color = Testo,
                    )
                    Text(
                        text = consiglio.testo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Testo2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Errore(messaggio: String, onRicarica: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFBEEEC))
            .border(1.dp, Color(0xFFE7C6C1), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = "Dati non disponibili",
                style = MaterialTheme.typography.titleSmall,
                color = Pericolo,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = messaggio,
                style = MaterialTheme.typography.bodyMedium,
                color = Testo2,
            )
            TextButton(onClick = onRicarica, contentPadding = PaddingValues(0.dp)) {
                Text("Riprova", color = Blu, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InAttesa() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Blu, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Lettura delle previsioni…",
                style = MaterialTheme.typography.bodyMedium,
                color = Testo3,
            )
        }
    }
}

@Composable
private fun Piede() {
    Column(Modifier.padding(top = 6.dp, bottom = 24.dp)) {
        HorizontalDivider(color = Bordo)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "FONTI",
            style = MaterialTheme.typography.labelSmall,
            color = Blu,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Previsioni: Open-Meteo, che distribuisce le corse dei modelli ECMWF IFS, " +
                "ICON-D2 del Deutscher Wetterdienst e Météo-France. Fuso Europe/Rome. " +
                "Allerte: riquadro ufficiale della Protezione Civile della Regione Friuli " +
                "Venezia Giulia. Coordinate: centroide del municipio di ciascun Comune, " +
                "standard EPSG:4326. Elenco dei Comuni: codici ISTAT.",
            style = MaterialTheme.typography.bodySmall,
            color = Testo3,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Le previsioni indicano probabilità, non certezze. Per le allerte ufficiali " +
                "fare sempre riferimento alla Protezione Civile della Regione Friuli Venezia Giulia.",
            style = MaterialTheme.typography.bodySmall,
            color = Testo2,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Guido Costalonga · costalonga.org/meteo",
            style = MaterialTheme.typography.labelSmall,
            color = Testo3,
        )
    }
}

private fun aggiornatoAlle(quando: Long): String =
    "Aggiornato alle " + Instant.ofEpochMilli(quando)
        .atZone(ZoneId.systemDefault())
        .format(ORA)

private fun etichettaLunga(data: LocalDate): String =
    if (data == LocalDate.now()) "oggi" else data.format(GIORNO_LUNGO)
