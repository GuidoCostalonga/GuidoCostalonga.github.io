package org.costalonga.polso.ui

import android.app.Application
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.costalonga.polso.BuildConfig
import org.costalonga.polso.contenitore
import org.costalonga.polso.dati.Archivio
import org.costalonga.polso.dati.ImportazioneEntita
import org.costalonga.polso.dati.Impostazioni
import org.costalonga.polso.dati.ProfiloIa
import org.costalonga.polso.dati.RiepilogoEntita
import org.costalonga.polso.esporta.EsitoRipristino
import org.costalonga.polso.esporta.GestoreBackup
import org.costalonga.polso.esporta.RapportoPdf
import org.costalonga.polso.fonti.MappaHc
import org.costalonga.polso.fonti.SincronizzatoreHc
import org.costalonga.polso.fonti.StatoHc
import org.costalonga.polso.ia.IaOnline
import org.costalonga.polso.lavori.valutaNotifiche
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Demo
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Regole
import org.costalonga.polso.motore.Segnalazione
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.motore.VoceDiario
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Contesto
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Evidenza
import org.costalonga.polso.motore.analisi.Evidenze
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.motore.esporta.Contenuto
import org.costalonga.polso.motore.esporta.Esportazione
import org.costalonga.polso.motore.ia.Assistente
import org.costalonga.polso.motore.ia.CostruttoreFatti
import org.costalonga.polso.motore.ia.Fatti
import org.costalonga.polso.motore.ia.Interprete
import org.costalonga.polso.motore.ia.MotoreTesto
import org.costalonga.polso.motore.ia.Prompt
import org.costalonga.polso.motore.ia.Risposta
import org.costalonga.polso.motore.ia.TestiDeterministici
import org.costalonga.polso.motore.importa.ImportaCsvGenerico
import org.costalonga.polso.motore.importa.ImportaPolsoCsv
import org.costalonga.polso.motore.importa.Mappatura
import org.costalonga.polso.motore.importa.RisultatoImportazione
import org.costalonga.polso.motore.importa.Tracce
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SceltaMotore(val nome: String) { CALCOLO("Solo calcolo (senza IA)"), LOCALE("IA locale"), ONLINE("Servizio online") }

data class MessaggioChat(val domanda: String, val risposta: Risposta?)

/** Richiesta in attesa del consenso prima di un invio online. */
data class ConsensoOnline(val testoInviato: String, val destinatario: String, val esegui: () -> Unit)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val c = app.contenitore

    val impostazioni: StateFlow<Impostazioni> = c.impostazioni.flusso.stateIn(viewModelScope, SharingStarted.Eagerly, Impostazioni())

    // Periodo iniziale: gli ultimi 30 giorni, così la dashboard non parte da una settimana appena iniziata.
    private val _tipoPeriodo = MutableStateFlow(TipoPeriodo.PERSONALIZZATO)
    private val _riferimento = MutableStateFlow(LocalDate.now())
    private val _personalizzato = MutableStateFlow<Pair<LocalDate, LocalDate>?>(LocalDate.now().minusDays(29) to LocalDate.now())
    val periodo = MutableStateFlow(Periodo.ultimiGiorni(30, LocalDate.now()))

    private val _dati = MutableStateFlow<Dati?>(null)
    val dati: StateFlow<Dati?> = _dati.asStateFlow()
    private val _esiti = MutableStateFlow<Map<String, Esito>>(emptyMap())
    val esiti: StateFlow<Map<String, Esito>> = _esiti.asStateFlow()
    private val _evidenze = MutableStateFlow<List<Evidenza>>(emptyList())
    val evidenze: StateFlow<List<Evidenza>> = _evidenze.asStateFlow()
    val caricamento = MutableStateFlow(true)
    val messaggio = MutableStateFlow<String?>(null)
    val statoHc = MutableStateFlow<StatoHc?>(null)
    val sincronizzando = MutableStateFlow(false)
    val consenso = MutableStateFlow<ConsensoOnline?>(null)
    val chat = MutableStateFlow<List<MessaggioChat>>(emptyList())
    val lavorandoIa = MutableStateFlow(false)
    val ultimaProva = MutableStateFlow<List<Segnalazione>?>(null)

    val oggi: LocalDate get() = LocalDate.now(impostazioni.value.preferenze().zona)
    val demo: Boolean get() = impostazioni.value.modalitaDemo
    fun archivio(): Archivio = c.archivio(demo)

    val registro = impostazioni.map { it.modalitaDemo }.flatMapLatest { c.archivio(it).dao.registro() }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList<ImportazioneEntita>())
    val diario = impostazioni.map { it.modalitaDemo }.flatMapLatest { c.archivio(it).dao.diario() }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val riepiloghi = impostazioni.map { it.modalitaDemo }.flatMapLatest { c.archivio(it).dao.riepiloghi() }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList<RiepilogoEntita>())
    val notifiche = c.archivioReale.dao.notifiche().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var lavoroCarico: Job? = null

    init {
        viewModelScope.launch {
            var precedente: Impostazioni? = null
            impostazioni.collect { i ->
                val p = precedente
                precedente = i
                if (p == null || p.modalitaDemo != i.modalitaDemo || p.preferenze() != i.preferenze()) ricarica()
            }
        }
        aggiornaStatoHc()
    }

    fun impostaTipo(t: TipoPeriodo) {
        _tipoPeriodo.value = t
        if (t == TipoPeriodo.PERSONALIZZATO && _personalizzato.value == null) _personalizzato.value = oggi.minusDays(29) to oggi
        aggiornaPeriodo()
    }

    fun sposta(passi: Int) {
        val p = periodo.value
        if (p.tipo == TipoPeriodo.PERSONALIZZATO) {
            val n = p.lunghezza.toLong() * passi
            _personalizzato.value = p.da.plusDays(n) to p.a.plusDays(n)
        } else _riferimento.value = when (p.tipo) {
            TipoPeriodo.GIORNO -> _riferimento.value.plusDays(passi.toLong())
            TipoPeriodo.SETTIMANA -> _riferimento.value.plusWeeks(passi.toLong())
            TipoPeriodo.MESE -> _riferimento.value.plusMonths(passi.toLong())
            else -> _riferimento.value.plusYears(passi.toLong())
        }
        aggiornaPeriodo()
    }

    fun personalizza(da: LocalDate, a: LocalDate) {
        _personalizzato.value = minOf(da, a) to maxOf(da, a)
        _tipoPeriodo.value = TipoPeriodo.PERSONALIZZATO
        aggiornaPeriodo()
    }

    private fun aggiornaPeriodo() {
        val t = _tipoPeriodo.value
        periodo.value = if (t == TipoPeriodo.PERSONALIZZATO) _personalizzato.value!!.let { Periodo(t, it.first, it.second) } else Periodo.di(t, _riferimento.value)
        ricarica()
    }

    fun ricarica() {
        lavoroCarico?.cancel()
        lavoroCarico = viewModelScope.launch(Dispatchers.Default) {
            caricamento.value = true
            val i = impostazioni.value
            val p = periodo.value
            val d = c.carica(c.archivio(i.modalitaDemo), i.preferenze(), minOf(p.da, oggi.minusDays(30)), maxOf(p.a, oggi))
            val ctx = Contesto(d, p, oggi)
            _dati.value = d
            _esiti.value = Catalogo.tutte.associate { it.def.id to it.esegui(ctx) }
            _evidenze.value = Evidenze.calcola(ctx)
            caricamento.value = false
        }
    }

    fun esitiSezione(s: Sezione) = Catalogo.perSezione(s).map { it to (_esiti.value[it.def.id] ?: Esito.NonDisponibile("Calcolo in corso…")) }

    // ------------------------------------------------------------- fonti

    fun aggiornaStatoHc() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val sdk = HealthConnectClient.getSdkStatus(ctx)
            val client = c.fabbricaClientHc()
            statoHc.value = if (client == null) StatoHc(sdk, emptySet(), false, false) else runCatching {
                StatoHc(sdk, client.permessiConcessi(), client.funzioneDisponibile(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY), client.funzioneDisponibile(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND))
            }.getOrElse { StatoHc(sdk, emptySet(), false, false) }
        }
    }

    fun sincronizza() {
        if (demo) { messaggio.value = "In modalità dimostrativa la sincronizzazione è disattivata."; return }
        val client = c.fabbricaClientHc() ?: run { messaggio.value = "Health Connect non è disponibile su questo telefono."; return }
        viewModelScope.launch {
            sincronizzando.value = true
            val e = withContext(Dispatchers.IO) { SincronizzatoreHc(client, c.archivioReale, c.impostazioni).sincronizza() }
            sincronizzando.value = false
            messaggio.value = if (e.riuscita) "Sincronizzazione riuscita: ${e.nuovi} nuovi, ${e.aggiornati} aggiornati, ${e.eliminati} eliminati."
            else "Sincronizzazione con avvisi: ${e.errori.first()}"
            aggiornaStatoHc()
            ricarica()
        }
    }

    fun importa(uri: Uri, nome: String, tipo: String, mappatura: Mappatura? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val avvio = System.currentTimeMillis()
            val r: RisultatoImportazione = try {
                ctx.contentResolver.openInputStream(uri)!!.use { s ->
                    when (tipo) {
                        "gpx" -> Tracce.gpx(s, nome)
                        "tcx" -> Tracce.tcx(s, nome)
                        "generico" -> ImportaCsvGenerico.importa(s.readBytes().toString(Charsets.UTF_8), nome, mappatura!!, impostazioni.value.preferenze().zona)
                        else -> ImportaPolsoCsv.importa(s.readBytes().toString(Charsets.UTF_8), nome, impostazioni.value.preferenze().zona)
                    }
                }
            } catch (e: Exception) {
                RisultatoImportazione(errori = listOf("File non leggibile: ${e.javaClass.simpleName}"))
            }
            val a = archivio()
            val esito = a.scrivi(r, avvio)
            a.dao.nuovaImportazione(ImportazioneEntita(fonte = "file", descrizione = "Importazione $nome ($tipo)", avviataIl = avvio, conclusaIl = System.currentTimeMillis(),
                da = (r.misure.map { it.inizio } + r.sonni.map { it.inizio } + r.allenamenti.map { it.inizio }).minOrNull(),
                a = (r.misure.map { it.fine } + r.sonni.map { it.fine } + r.allenamenti.map { it.fine }).maxOrNull(),
                nuovi = esito.nuovi, aggiornati = esito.aggiornati, eliminati = 0, errori = r.errori.take(50).joinToString("\n"),
                esito = if (r.errori.isEmpty()) "riuscita" else if (r.totale > 0) "parziale" else "non riuscita"))
            messaggio.value = "Importati ${esito.nuovi} nuovi e ${esito.aggiornati} già presenti (aggiornati)" + if (r.errori.isNotEmpty()) "; ${r.errori.size} righe scartate (dettagli nel registro)." else "."
            ricarica()
        }
    }

    suspend fun anteprimaCsv(uri: Uri): List<List<String>> = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use { ImportaCsvGenerico.anteprima(it.readBytes().toString(Charsets.UTF_8)) }
    }

    // ------------------------------------------------------------ diario

    fun salvaVoce(v: VoceDiario) = viewModelScope.launch(Dispatchers.IO) { archivio().salvaVoce(v); ricarica() }
    fun eliminaRiepilogo(id: Long) = viewModelScope.launch(Dispatchers.IO) { archivio().dao.eliminaRiepilogo(id) }
    fun eliminaVoce(id: Long) = viewModelScope.launch(Dispatchers.IO) { archivio().dao.eliminaVoce(id); ricarica() }

    // ------------------------------------------------------- impostazioni

    fun modifica(f: (Impostazioni) -> Impostazioni) = viewModelScope.launch {
        c.impostazioni.modifica(f)
        val i = c.impostazioni.attuali()
        c.pianificaSincronizzazione(i.oreSincronizzazione, i.sincronizzazioneAutomatica && !i.modalitaDemo)
    }

    fun modalitaDemo(attiva: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (attiva && c.archivioDemo.dao.conteggi().isEmpty()) {
            val a = Demo.genera(LocalDate.now(), 180)
            c.archivioDemo.scrivi(RisultatoImportazione(a.misure, a.sonni, a.allenamenti), 0)
            a.diario.forEach { c.archivioDemo.salvaVoce(it) }
        }
        c.impostazioni.modifica { it.copy(modalitaDemo = attiva) }
    }

    fun cancellaTutto() = viewModelScope.launch(Dispatchers.IO) {
        c.archivioReale.dao.svuotaTutto()
        c.archivioDemo.dao.svuotaTutto()
        c.segreti.cancella()
        ProfiloIa.entries.forEach { c.modelli.elimina(it) }
        c.impostazioni.modifica { org.costalonga.polso.dati.Impostazioni(primoAvvioFatto = true) }
        messaggio.value = "Tutti i dati dell'app sono stati cancellati. I dati in Health Connect e in HONOR Health non sono stati toccati."
        ricarica()
    }

    fun provaRegole() = viewModelScope.launch(Dispatchers.Default) {
        val d = _dati.value ?: return@launch
        ultimaProva.value = Regole.valuta(d, oggi, impostazioni.value.notifiche)
    }

    fun valutaOra() = viewModelScope.launch(Dispatchers.IO) {
        val s = valutaNotifiche(getApplication())
        messaggio.value = s?.let { "Notifica inviata: ${it.titolo}" } ?: "Nessuna notifica da inviare ora (nessuna variazione, ore silenziose, intervallo minimo o permesso mancante)."
    }

    // ------------------------------------------------- esportazione e backup

    fun esporta(uri: Uri, formato: String, contenuti: Set<Contenuto>, sezioni: Set<Sezione>) = viewModelScope.launch(Dispatchers.IO) {
        val d = _dati.value ?: return@launch
        val p = periodo.value
        try {
            (getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("destinazione non scrivibile")).use { out ->
                when (formato) {
                    "pdf" -> RapportoPdf(d, p, oggi, sezioni, demo).scrivi(out)
                    "xlsx" -> Esportazione(d, p, oggi, contenuti).excel(out)
                    else -> {
                        val file = Esportazione(d, p, oggi, contenuti).csv()
                        if (file.size == 1) out.write(file.values.first().toByteArray(Charsets.UTF_8))
                        else ZipOutputStream(out).use { z -> file.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray(Charsets.UTF_8)); z.closeEntry() } }
                    }
                }
            }
            messaggio.value = "Esportazione completata (${p.descrizione()})."
        } catch (e: Exception) {
            messaggio.value = "Esportazione non riuscita: ${e.javaClass.simpleName}"
        }
    }

    private fun gestoreBackup() = GestoreBackup(getApplication(), c.archivioReale, c.impostazioni, BuildConfig.VERSION_NAME)

    fun backup(uri: Uri, passphrase: CharArray) = viewModelScope.launch(Dispatchers.IO) {
        messaggio.value = try {
            val n = gestoreBackup().salva(uri, passphrase)
            "Backup cifrato creato e verificato: ${n["misure"]} misure, ${n["sonni"]} notti, ${n["allenamenti"]} allenamenti, ${n["diario"]} voci di diario."
        } catch (e: Exception) { "Backup non riuscito: ${e.message ?: e.javaClass.simpleName}" } finally { passphrase.fill(' ') }
    }

    val esitoRipristino = MutableStateFlow<EsitoRipristino?>(null)
    fun ripristina(uri: Uri, passphrase: CharArray) = viewModelScope.launch(Dispatchers.IO) {
        try {
            esitoRipristino.value = gestoreBackup().ripristina(uri, passphrase)
            messaggio.value = esitoRipristino.value!!.dettaglio
        } catch (e: org.costalonga.polso.motore.backup.PassphraseErrata) {
            messaggio.value = "Passphrase errata o file alterato: nessun dato è stato modificato."
        } catch (e: Exception) {
            messaggio.value = "Ripristino non riuscito: ${e.message ?: e.javaClass.simpleName}"
        } finally { passphrase.fill(' ') }
        ricarica()
    }

    // ------------------------------------------------------------------ IA

    private fun motore(s: SceltaMotore): MotoreTesto? = when (s) {
        SceltaMotore.CALCOLO -> null
        SceltaMotore.LOCALE -> impostazioni.value.profiloIa?.let { ProfiloIa.valueOf(it) }?.takeIf { c.modelli.installato(it) }?.let { p -> c.iaLocale.also { it.profiloAttivo = p } }
        SceltaMotore.ONLINE -> c.segreti.chiaveOnline()?.takeIf { impostazioni.value.onlineAttivo }?.let { IaOnline(impostazioni.value.onlineIndirizzo, impostazioni.value.onlineModello, it) }
    }

    fun motoriDisponibili(): List<SceltaMotore> = SceltaMotore.entries.filter { it == SceltaMotore.CALCOLO || motore(it) != null }

    private fun conConsenso(scelta: SceltaMotore, testo: String, azione: () -> Unit) {
        if (scelta != SceltaMotore.ONLINE) { azione(); return }
        val i = impostazioni.value
        consenso.value = ConsensoOnline("SISTEMA:\n${Prompt.SISTEMA}\n\nRICHIESTA:\n$testo", "${i.onlineIndirizzo} (modello ${i.onlineModello})", azione)
    }

    fun generaRiepilogo(tipo: TipoPeriodo, scelta: SceltaMotore) {
        val d = _dati.value ?: return
        val rif = if (tipo == TipoPeriodo.GIORNO) oggi.minusDays(1) else oggi.minusWeeks(1)
        val f = CostruttoreFatti.perRiepilogo(d, tipo, rif, oggi)
        val etichetta = if (tipo == TipoPeriodo.GIORNO) "giornaliero" else "settimanale"
        val richiesta = Prompt.riepilogo(f, etichetta)
        conConsenso(scelta, richiesta) {
            viewModelScope.launch {
                lavorandoIa.value = true
                val r = Assistente.rispondi(f, richiesta, motore(scelta)) { TestiDeterministici.riepilogo(f) }
                salva(r, etichetta)
                lavorandoIa.value = false
            }
        }
    }

    private suspend fun salva(r: Risposta, tipo: String) = withContext(Dispatchers.IO) {
        archivio().dao.salvaRiepilogo(RiepilogoEntita(tipo = tipo, da = r.fatti.periodo.da.toString(), a = r.fatti.periodo.a.toString(),
            testo = r.testo + (r.nota?.let { "\n\nNota: $it" } ?: ""), motore = r.motore, verificato = r.daIa, fatti = r.fatti.testo, creatoIl = System.currentTimeMillis()))
    }

    fun chiedi(domanda: String, scelta: SceltaMotore) {
        val d = _dati.value ?: return
        val q = Interprete.interpreta(domanda, oggi)
        val f = CostruttoreFatti.costruisci(d, q.periodo, oggi, Interprete.analisi(q))
        val richiesta = Prompt.domanda(f, domanda)
        conConsenso(scelta, richiesta) {
            viewModelScope.launch {
                lavorandoIa.value = true
                chat.value = chat.value + MessaggioChat(domanda, null)
                val r = Assistente.rispondi(f, richiesta, motore(scelta)) { TestiDeterministici.risposta(q, f) }
                chat.value = chat.value.dropLast(1) + MessaggioChat(domanda, r)
                lavorandoIa.value = false
            }
        }
    }

    val spiegazione = MutableStateFlow<Risposta?>(null)
    fun spiega(idAnalisi: String, scelta: SceltaMotore) {
        val d = _dati.value ?: return
        val a = Catalogo.perId(idAnalisi) ?: return
        val f: Fatti = CostruttoreFatti.costruisci(d, periodo.value, oggi, listOf(idAnalisi))
        val richiesta = Prompt.spiegazione(f, a.def.titolo)
        conConsenso(scelta, richiesta) {
            viewModelScope.launch {
                lavorandoIa.value = true
                spiegazione.value = Assistente.rispondi(f, richiesta, motore(scelta)) { TestiDeterministici.riepilogo(f) + "\n\nMetodo: ${a.def.metodo}\nLimiti: ${a.def.limiti}" }
                lavorandoIa.value = false
            }
        }
    }

    private var scaricamento: Job? = null
    fun scarica(p: ProfiloIa) { scaricamento?.cancel(); scaricamento = viewModelScope.launch { c.modelli.scarica(p); modifica { it.copy(profiloIa = it.profiloIa ?: p.name) } } }
    fun annullaScaricamento() { scaricamento?.cancel() }
    fun eliminaModello(p: ProfiloIa) { c.iaLocale.chiudi(); c.modelli.elimina(p); if (impostazioni.value.profiloIa == p.name) modifica { it.copy(profiloIa = null) } }

    fun salvaChiaveOnline(k: String?) { c.segreti.salvaChiaveOnline(k); messaggio.value = if (k.isNullOrBlank()) "Chiave eliminata." else "Chiave salvata in modo cifrato (Android Keystore)." }

    val tipiHc get() = MappaHc.TIPI
    val permessiHc get() = SincronizzatoreHc.TUTTI_I_PERMESSI
}
