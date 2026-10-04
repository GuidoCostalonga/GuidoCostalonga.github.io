package org.costalonga.sportintv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.IceSkating
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsRugby
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.StatoEvento
import org.costalonga.sportintv.raccolta.TipoTrasmissione

@Suppress("DEPRECATION") // le icone "AutoMirrored" non servono: l'app è solo in italiano
fun iconaSport(chiave: String): ImageVector = when (Sport.daChiave(chiave)) {
    Sport.CALCIO, Sport.CALCIO_A_5 -> Icons.Filled.SportsSoccer
    Sport.FOOTBALL_AMERICANO -> Icons.Filled.SportsFootball
    Sport.BASKET -> Icons.Filled.SportsBasketball
    Sport.VOLLEY, Sport.BEACH_VOLLEY -> Icons.Filled.SportsVolleyball
    Sport.TENNIS, Sport.PADEL, Sport.TENNIS_TAVOLO -> Icons.Filled.SportsTennis
    Sport.CICLISMO -> Icons.Filled.DirectionsBike
    Sport.MOTORI -> Icons.Filled.SportsMotorsports
    Sport.ATLETICA, Sport.TRIATHLON -> Icons.Filled.DirectionsRun
    Sport.NUOTO, Sport.PALLANUOTO -> Icons.Filled.Pool
    Sport.SPORT_INVERNALI -> Icons.Filled.DownhillSkiing
    Sport.PATTINAGGIO -> Icons.Filled.IceSkating
    Sport.HOCKEY -> Icons.Filled.SportsHockey
    Sport.RUGBY -> Icons.Filled.SportsRugby
    Sport.BASEBALL -> Icons.Filled.SportsBaseball
    Sport.GOLF -> Icons.Filled.GolfCourse
    Sport.PUGILATO -> Icons.Filled.SportsMma
    Sport.ARTI_MARZIALI, Sport.SCHERMA -> Icons.Filled.SportsMartialArts
    Sport.GINNASTICA -> Icons.Filled.SportsGymnastics
    Sport.PALLAMANO -> Icons.Filled.SportsHandball
    Sport.CRICKET -> Icons.Filled.SportsCricket
    Sport.VELA -> Icons.Filled.Sailing
    Sport.CANOTTAGGIO -> Icons.Filled.Kayaking
    Sport.SPORT_ACQUATICI -> Icons.Filled.Surfing
    Sport.ARRAMPICATA -> Icons.Filled.Landscape
    Sport.ORIENTAMENTO -> Icons.Filled.Hiking
    Sport.TIRO, Sport.FRECCETTE -> Icons.Filled.TrackChanges
    Sport.IPPICA, Sport.EQUITAZIONE, Sport.MULTISPORT -> Icons.Filled.EmojiEvents
    Sport.BILIARDO -> Icons.Filled.Sports
    Sport.ALTRO -> Icons.Filled.Sports
}

fun nomeSport(chiave: String): String = Sport.daChiave(chiave).nome

fun Accesso.etichetta(): String = when (this) {
    Accesso.IN_CHIARO -> "In chiaro"
    Accesso.GRATUITO_CON_REGISTRAZIONE -> "Gratis con registrazione"
    Accesso.ABBONAMENTO -> "Abbonamento"
    Accesso.ACQUISTO_SINGOLO -> "Acquisto singolo"
    Accesso.NON_INDICATO -> "Accesso non indicato"
}

fun TipoTrasmissione.etichetta(): String = when (this) {
    TipoTrasmissione.DIRETTA -> "Diretta"
    TipoTrasmissione.DIFFERITA -> "Differita"
    TipoTrasmissione.REPLICA -> "Replica"
    TipoTrasmissione.NON_INDICATO -> "Diretta o replica non indicato dalla fonte"
}

fun StatoEvento.etichetta(): String = when (this) {
    StatoEvento.CONFERMATO -> "Trasmissione confermata"
    StatoEvento.DA_CONFERMARE -> "Trasmissione in Italia da confermare"
    StatoEvento.RINVIATO -> "Rinviato"
    StatoEvento.ANNULLATO -> "Annullato"
}
