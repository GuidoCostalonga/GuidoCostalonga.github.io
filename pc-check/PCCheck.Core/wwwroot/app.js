/* PC Check - interfaccia (JavaScript puro, nessuna dipendenza esterna, nessuna chiamata di rete fuori da 127.0.0.1). */
(function () {
  "use strict";

  const TOKEN_HEADER = "X-PC-Check-Token";
  const TOKEN_STORAGE = "pccheck_session_token";

  // ------------------------------------------------------------------
  // Token di sessione: arriva una sola volta nell'URL, poi vive in sessionStorage.
  // ------------------------------------------------------------------
  function acquireToken() {
    const params = new URLSearchParams(window.location.search);
    let token = params.get("token");
    if (token) {
      try { sessionStorage.setItem(TOKEN_STORAGE, token); } catch (e) { /* storage non disponibile */ }
      const clean = window.location.pathname + window.location.hash;
      window.history.replaceState({}, document.title, clean);
    } else {
      try { token = sessionStorage.getItem(TOKEN_STORAGE); } catch (e) { token = null; }
    }
    return token;
  }

  const token = acquireToken();
  const state = {
    info: null,
    overview: null,
    defender: null,
    threats: [],
    scan: null,
    scanTimer: null,
    performance: null,
    startup: [],
    apps: [],
    temp: null,
    report: null,
    loading: {}
  };

  // ------------------------------------------------------------------
  // Utilità
  // ------------------------------------------------------------------
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function fmtBytes(bytes) {
    if (bytes == null || isNaN(bytes)) return "n.d.";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let v = Number(bytes), u = 0;
    while (v >= 1024 && u < units.length - 1) { v /= 1024; u++; }
    return (u === 0 ? v.toFixed(0) : v.toFixed(v < 10 ? 2 : 1)).replace(".", ",") + " " + units[u];
  }

  function fmtPct(value) {
    if (value == null || isNaN(value)) return "n.d.";
    return Number(value).toLocaleString("it-IT", { maximumFractionDigits: 1 }) + "%";
  }

  function fmtDate(value) {
    if (!value) return "n.d.";
    const d = new Date(value);
    if (isNaN(d.getTime())) return "n.d.";
    return d.toLocaleString("it-IT", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  function fmtDateOnly(value) {
    if (!value) return "";
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    return d.toLocaleDateString("it-IT");
  }

  function fmtUptime(seconds) {
    if (seconds == null) return "n.d.";
    const s = Math.floor(seconds);
    const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
    const parts = [];
    if (d) parts.push(d + (d === 1 ? " giorno" : " giorni"));
    if (h) parts.push(h + " h");
    parts.push(m + " min");
    return parts.join(" ");
  }

  function num(value) {
    return Number(value || 0).toLocaleString("it-IT");
  }

  function toast(message, kind) {
    const box = document.createElement("div");
    box.className = "toast " + (kind || "");
    box.textContent = message;
    $("#toasts").appendChild(box);
    setTimeout(() => box.remove(), 6000);
  }

  function setGlobalError(message) {
    const el = $("#global-error");
    if (!message) { el.hidden = true; el.textContent = ""; return; }
    el.hidden = false;
    el.textContent = message;
  }

  // ------------------------------------------------------------------
  // Chiamate API con token
  // ------------------------------------------------------------------
  async function api(path, options) {
    const opts = Object.assign({ method: "GET" }, options || {});
    const headers = { "Accept": "application/json" };
    headers[TOKEN_HEADER] = token || "";
    if (opts.body !== undefined) {
      headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(opts.body);
    }
    opts.headers = headers;
    opts.credentials = "omit";
    opts.cache = "no-store";
    let response;
    try {
      response = await fetch(path, opts);
    } catch (e) {
      throw new Error("PC Check non risponde: probabilmente è stato chiuso. Riavvialo e usa il nuovo collegamento.");
    }
    if (response.status === 401) {
      showAuthError();
      throw new Error("Sessione non valida.");
    }
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = { raw: text }; }
    }
    if (!response.ok) {
      const message = (data && (data.error || data.message)) || ("Errore " + response.status);
      const err = new Error(message);
      err.data = data;
      throw err;
    }
    return data;
  }

  async function apiText(path) {
    const headers = {};
    headers[TOKEN_HEADER] = token || "";
    const response = await fetch(path, { headers, cache: "no-store", credentials: "omit" });
    if (response.status === 401) { showAuthError(); throw new Error("Sessione non valida."); }
    if (!response.ok) throw new Error("Errore " + response.status);
    return response.text();
  }

  function showAuthError() {
    $("#app").hidden = true;
    $("#auth-error").hidden = false;
  }

  // ------------------------------------------------------------------
  // Finestra di conferma per gli interventi
  // ------------------------------------------------------------------
  function confirmAction(spec) {
    return new Promise((resolve) => {
      const modal = $("#modal");
      $("#modal-title").textContent = spec.title || "Conferma intervento";
      $("#modal-body").innerHTML = spec.html || "";
      $("#modal-impact").textContent = spec.impact || "";
      $("#modal-reversible").textContent = spec.reversible || "";
      const confirmBtn = $("#modal-confirm");
      confirmBtn.textContent = spec.confirmLabel || "Conferma";
      confirmBtn.className = "btn " + (spec.danger === false ? "btn-primary" : "btn-danger");
      modal.hidden = false;
      const done = (value) => {
        modal.hidden = true;
        confirmBtn.onclick = null;
        $("#modal-cancel").onclick = null;
        document.removeEventListener("keydown", onKey);
        resolve(value);
      };
      const onKey = (e) => { if (e.key === "Escape") done(false); };
      confirmBtn.onclick = () => done(true);
      $("#modal-cancel").onclick = () => done(false);
      modal.onclick = (e) => { if (e.target === modal) done(false); };
      document.addEventListener("keydown", onKey);
      confirmBtn.focus();
    });
  }

  function showOutcome(outcome, container) {
    const ok = outcome && outcome.success;
    const cls = ok ? "alert-ok" : (outcome && outcome.userCancelled ? "alert-info" : "alert-error");
    const html = '<div class="alert ' + cls + '">' + esc(outcome ? outcome.message : "Esito sconosciuto") +
      (outcome && outcome.details ? '<div class="small muted">' + esc(outcome.details) + '</div>' : "") +
      (outcome && outcome.requiresAdmin ? '<div class="small">Richiede i permessi di amministratore.</div>' : "") + '</div>';
    if (container) container.innerHTML = html;
    toast(outcome ? outcome.message : "Esito sconosciuto", ok ? "ok" : (outcome && outcome.userCancelled ? "warn" : "error"));
  }

  // ------------------------------------------------------------------
  // Navigazione tra sezioni
  // ------------------------------------------------------------------
  const sections = ["panoramica", "scansione", "prestazioni", "avvio", "interventi", "report"];
  const loaders = {
    panoramica: loadOverview,
    scansione: loadScanSection,
    prestazioni: loadPerformance,
    avvio: loadStartupAndApps,
    interventi: loadActions,
    report: () => Promise.resolve()
  };

  function currentSection() {
    const hash = (window.location.hash || "#panoramica").replace("#", "");
    return sections.includes(hash) ? hash : "panoramica";
  }

  function showSection(name, force) {
    sections.forEach((s) => {
      const el = $("#section-" + s);
      const active = s === name;
      el.hidden = !active;
      if (active) $("#section-title").textContent = el.dataset.title;
    });
    $$(".nav-link").forEach((a) => a.classList.toggle("active", a.dataset.section === name));
    const loader = loaders[name];
    if (loader) {
      loader(force).catch((e) => setGlobalError(e.message));
    }
  }

  window.addEventListener("hashchange", () => showSection(currentSection(), false));

  // ------------------------------------------------------------------
  // Sistema
  // ------------------------------------------------------------------
  async function loadSystemInfo() {
    const info = await api("/api/system/info");
    state.info = info;
    $("#sys-machine").textContent = info.machineName;
    $("#sys-os").textContent = info.osDescription;
    $("#sys-user").textContent = "Utente: " + info.userName + " · versione " + info.version;
    $("#demo-badge").hidden = !info.demoMode;
    $("#admin-badge").hidden = !info.isAdministrator;
    $("#brand-sub").textContent = info.demoMode ? "Modalità dimostrativa" : "Diagnostica locale";
    if (!info.isWindows && !info.demoMode) {
      setGlobalError("Sistema operativo non Windows: vengono mostrati dati dimostrativi.");
    }
  }

  // ------------------------------------------------------------------
  // Panoramica
  // ------------------------------------------------------------------
  function severityPill(severity) {
    const map = {
      Critical: ["pill-critical", "Grave"],
      High: ["pill-high", "Alta"],
      Medium: ["pill-warn", "Media"],
      Low: ["pill-low", "Bassa"],
      Info: ["pill-info", "Info"]
    };
    const m = map[severity] || ["pill-neutral", severity];
    return '<span class="pill ' + m[0] + '">' + esc(m[1]) + '</span>';
  }

  const sectionLinks = { scan: "scansione", performance: "prestazioni", startup: "avvio", apps: "avvio", temp: "interventi", overview: "panoramica" };

  function renderOverview(data) {
    const d = data.defender;
    const protection = $("#card-protection");
    let cls = "ok", value = "Attiva", detail = "Defender con protezione in tempo reale";
    if (!d.available) { cls = "warn"; value = "Sconosciuta"; detail = d.error || "Stato non leggibile"; }
    else if (!d.antivirusEnabled) { cls = "bad"; value = "Disattivata"; detail = "Antivirus Defender non attivo"; }
    else if (!d.realTimeProtectionEnabled) { cls = "bad"; value = "Parziale"; detail = "Protezione in tempo reale disattivata"; }
    else if (d.signatureAgeDays != null && d.signatureAgeDays > 7) { cls = "warn"; value = "Da aggiornare"; detail = "Definizioni vecchie di " + d.signatureAgeDays + " giorni"; }
    setStat(protection, cls, value, detail);

    const active = data.threats.filter((t) => t.isActive || t.status === "Rilevata").length;
    setStat($("#card-threats"), active ? "bad" : "ok", String(active), active ? "Minacce attive secondo Defender" : (data.threats.length ? data.threats.length + " nella cronologia, tutte gestite" : "Nessuna rilevazione in cronologia"));

    const p = data.performance;
    setStat($("#card-cpu"), p.cpuPercent >= 80 ? "bad" : (p.cpuPercent >= 50 ? "warn" : "ok"), fmtPct(p.cpuPercent), p.cpuCores + " core · campione " + p.sampleMilliseconds + " ms");
    setStat($("#card-memory"), p.memoryPercent >= 90 ? "bad" : (p.memoryPercent >= 75 ? "warn" : "ok"), fmtPct(p.memoryPercent), fmtBytes(p.memoryUsedBytes) + " di " + fmtBytes(p.memoryTotalBytes));

    const sys = p.disks.find((x) => /^c:/i.test(x.name)) || p.disks[0];
    if (sys) {
      setStat($("#card-disk"), sys.usedPercent >= 90 ? "bad" : (sys.usedPercent >= 80 ? "warn" : "ok"), fmtBytes(sys.freeBytes) + " liberi", sys.name + " · " + fmtPct(sys.usedPercent) + " usato di " + fmtBytes(sys.totalBytes));
    } else {
      setStat($("#card-disk"), "", "n.d.", "Nessun disco leggibile");
    }

    const temp = data.temp;
    setStat($("#card-temp"), temp.totalReclaimableBytes > 1024 * 1024 * 1024 ? "warn" : "ok", fmtBytes(temp.totalReclaimableBytes), "recuperabili da %TEMP% e Windows\\Temp");

    $("#overview-time").textContent = "Aggiornato alle " + new Date(data.generatedAt).toLocaleTimeString("it-IT");
    const list = $("#findings-list");
    list.innerHTML = data.findings.map((f) => {
      const target = sectionLinks[f.section] || "panoramica";
      return '<li class="finding">' + severityPill(f.severity) +
        '<div><div class="finding-title">' + esc(f.title) + '</div><div class="finding-desc">' + esc(f.description) + '</div></div>' +
        '<a class="btn btn-secondary btn-sm" href="#' + target + '">Apri</a></li>';
    }).join("") || '<li class="empty">Nessuna segnalazione.</li>';

    const unknownCount = data.performance.topProcesses.filter((x) => x.unknown).length +
      data.startup.filter((s) => s.enabled && s.unknown).length +
      data.apps.filter((a) => a.hints && a.hints.length).length;
    $("#count-threats").textContent = String(active);
    $("#count-unknown").textContent = String(unknownCount);
    $("#count-high").textContent = String(data.performance.highImpactProcesses.length);
  }

  function setStat(card, cls, value, detail) {
    card.classList.remove("ok", "warn", "bad");
    if (cls) card.classList.add(cls);
    $(".stat-value", card).textContent = value;
    $(".stat-detail", card).textContent = detail;
  }

  async function loadOverview(force) {
    if (state.overview && !force) { renderOverview(state.overview); return; }
    setGlobalError("");
    const data = await api("/api/overview");
    state.overview = data;
    state.defender = data.defender;
    state.threats = data.threats;
    state.performance = data.performance;
    state.startup = data.startup;
    state.apps = data.apps;
    state.temp = data.temp;
    state.scan = data.scan;
    renderOverview(data);
  }

  // ------------------------------------------------------------------
  // Scansione
  // ------------------------------------------------------------------
  function yesNo(v) {
    return v ? '<span class="text-ok">Sì</span>' : '<span class="text-bad">No</span>';
  }

  function renderDefender(d) {
    const rows = [];
    const add = (k, v) => rows.push("<dt>" + esc(k) + "</dt><dd>" + v + "</dd>");
    if (!d.available) {
      $("#defender-status").innerHTML = '<div class="alert alert-error">' + esc(d.error || "Stato non disponibile.") + '</div>';
    } else {
      add("Antivirus attivo", yesNo(d.antivirusEnabled));
      add("Protezione in tempo reale", yesNo(d.realTimeProtectionEnabled));
      add("Monitoraggio comportamento", yesNo(d.behaviorMonitorEnabled));
      add("Protezione di rete", yesNo(d.networkInspectionEnabled));
      add("Protezione manomissioni", yesNo(d.tamperProtected));
      add("Versione definizioni", esc(d.signatureVersion || "n.d.") + (d.signatureAgeDays != null ? ' <span class="muted">(' + d.signatureAgeDays + " giorni)</span>" : ""));
      add("Aggiornate il", esc(fmtDate(d.signatureLastUpdated)));
      add("Motore / piattaforma", esc((d.engineVersion || "n.d.") + " / " + (d.platformVersion || "n.d.")));
      add("Ultima scansione rapida", esc(fmtDate(d.lastQuickScanEnd)));
      add("Ultima scansione completa", esc(fmtDate(d.lastFullScanEnd)));
      if (d.scannerPath) add("Eseguibile scansioni", '<span class="path">' + esc(d.scannerPath) + "</span>");
      $("#defender-status").innerHTML = "<dl class=\"kv\">" + rows.join("") + "</dl>";
    }
    $("#defender-warnings").innerHTML = (d.warnings || []).map((w) => '<div class="alert alert-info" style="margin-top:8px">' + esc(w) + "</div>").join("");
  }

  function renderThreats(threats) {
    const el = $("#threats-table");
    if (!threats.length) {
      el.innerHTML = '<div class="empty">Nessuna minaccia nella cronologia di Microsoft Defender.</div>';
      return;
    }
    const sevPill = (t) => {
      const map = { 5: "pill-critical", 4: "pill-high", 2: "pill-warn", 1: "pill-low" };
      return '<span class="pill ' + (map[t.severityLevel] || "pill-neutral") + '">' + esc(t.severity) + "</span>";
    };
    el.innerHTML = '<div class="table-wrap"><table class="table"><thead><tr><th>Minaccia</th><th>Gravità</th><th>Categoria</th><th>Stato</th><th>Rilevata</th><th>Risorse coinvolte</th></tr></thead><tbody>' +
      threats.map((t) => '<tr><td><strong>' + esc(t.name) + '</strong>' + (t.isActive ? ' <span class="pill pill-critical">attiva</span>' : "") + '</td><td>' + sevPill(t) + '</td><td>' + esc(t.category) +
        '</td><td>' + esc(t.status) + '</td><td>' + esc(fmtDate(t.detectedAt)) + '</td><td class="path">' + esc((t.resources || []).join("\n")) + "</td></tr>").join("") +
      "</tbody></table></div>";
  }

  function renderScan(s) {
    state.scan = s;
    const el = $("#scan-status");
    const out = $("#scan-output");
    const running = s && s.state === "Running";
    $("#btn-scan-quick").disabled = running;
    $("#btn-scan-full").disabled = running;
    $("#btn-scan-cancel").hidden = !running;
    if (!s || s.state === "Idle") {
      el.innerHTML = '<span class="muted">Nessuna scansione avviata da PC Check in questa sessione.</span>';
      out.hidden = true;
      return;
    }
    const labels = { Running: ["text-warn", "In corso"], Completed: ["text-ok", "Completata"], ThreatsFound: ["text-bad", "Minacce trovate"], Failed: ["text-bad", "Non riuscita"], Cancelled: ["muted", "Interrotta"] };
    const l = labels[s.state] || ["", s.state];
    el.innerHTML = '<span class="' + l[0] + '">' + esc(l[1]) + "</span> · " + esc(s.message) +
      (s.startedAt ? '<div class="small muted">Avviata alle ' + esc(fmtDate(s.startedAt)) + (s.finishedAt ? ", terminata alle " + esc(fmtDate(s.finishedAt)) : "") + (s.exitCode != null ? " · codice " + s.exitCode : "") + "</div>" : "") +
      (s.command ? '<div class="small path">' + esc(s.command) + "</div>" : "");
    if (s.output && s.output.length) {
      out.hidden = false;
      out.textContent = s.output.join("\n");
      out.scrollTop = out.scrollHeight;
    } else {
      out.hidden = true;
    }
    if (running) {
      if (!state.scanTimer) state.scanTimer = setInterval(pollScan, 2000);
    } else if (state.scanTimer) {
      clearInterval(state.scanTimer);
      state.scanTimer = null;
      if (s.state === "Completed" || s.state === "ThreatsFound") {
        api("/api/defender/threats").then((t) => { state.threats = t; renderThreats(t); }).catch(() => {});
        state.overview = null;
      }
    }
  }

  async function pollScan() {
    try {
      renderScan(await api("/api/defender/scan"));
    } catch (e) {
      clearInterval(state.scanTimer);
      state.scanTimer = null;
    }
  }

  async function loadScanSection(force) {
    const [status, threats, scan] = await Promise.all([
      (state.defender && !force) ? Promise.resolve(state.defender) : api("/api/defender/status"),
      (state.threats && state.threats.length && !force) ? Promise.resolve(state.threats) : api("/api/defender/threats"),
      api("/api/defender/scan")
    ]);
    state.defender = status;
    state.threats = threats;
    renderDefender(status);
    renderThreats(threats);
    renderScan(scan);
  }

  async function startScan(type) {
    const full = type === "full";
    const ok = await confirmAction({
      title: full ? "Avviare la scansione completa?" : "Avviare la scansione rapida?",
      html: "<p>Microsoft Defender analizzerà " + (full ? "<strong>tutti i file del computer</strong>: può richiedere da 30 minuti a diverse ore" : "le <strong>aree più a rischio</strong> (memoria, avvio, cartelle di sistema): di solito richiede pochi minuti") +
        ".</p><p>Comando eseguito: <code>MpCmdRun.exe -Scan -ScanType " + (full ? 2 : 1) + "</code>. Nessun file viene modificato da PC Check.</p>",
      impact: full ? "Il computer potrebbe risultare più lento durante la scansione." : "Leggero rallentamento per pochi minuti.",
      reversible: "Sì: la scansione può essere interrotta in qualsiasi momento.",
      confirmLabel: "Avvia scansione",
      danger: false
    });
    if (!ok) return;
    try {
      renderScan(await api("/api/defender/scan", { method: "POST", body: { type } }));
    } catch (e) {
      toast(e.message, "error");
    }
  }

  // ------------------------------------------------------------------
  // Prestazioni
  // ------------------------------------------------------------------
  function meter(percent) {
    const cls = percent >= 90 ? "bad" : (percent >= 75 ? "warn" : "");
    return '<div class="meter ' + cls + '"><span style="width:' + Math.min(100, Math.max(0, percent)) + '%"></span></div>';
  }

  function renderPerformance(p) {
    $("#perf-cards").innerHTML =
      '<article class="card stat ' + (p.cpuPercent >= 80 ? "bad" : p.cpuPercent >= 50 ? "warn" : "ok") + '"><div class="stat-label">CPU</div><div class="stat-value">' + fmtPct(p.cpuPercent) + '</div><div class="stat-detail">' + p.cpuCores + " core · media su " + p.sampleMilliseconds + " ms</div>" + meter(p.cpuPercent) + "</article>" +
      '<article class="card stat ' + (p.memoryPercent >= 90 ? "bad" : p.memoryPercent >= 75 ? "warn" : "ok") + '"><div class="stat-label">Memoria</div><div class="stat-value">' + fmtPct(p.memoryPercent) + '</div><div class="stat-detail">' + fmtBytes(p.memoryUsedBytes) + " di " + fmtBytes(p.memoryTotalBytes) + "</div>" + meter(p.memoryPercent) + "</article>" +
      '<article class="card stat"><div class="stat-label">Processi</div><div class="stat-value">' + num(p.processCount) + '</div><div class="stat-detail">' + p.highImpactProcesses.length + " ad alto impatto</div></article>" +
      '<article class="card stat"><div class="stat-label">Acceso da</div><div class="stat-value" style="font-size:1.2rem">' + esc(fmtUptime(p.uptimeSeconds)) + '</div><div class="stat-detail">Un riavvio ogni tanto libera memoria e completa gli aggiornamenti</div></article>';

    $("#disks-table").innerHTML = p.disks.length ? '<div class="table-wrap"><table class="table"><thead><tr><th>Unità</th><th>Tipo</th><th class="num">Totale</th><th class="num">Liberi</th><th style="width:30%">Uso</th></tr></thead><tbody>' +
      p.disks.map((d) => '<tr><td><strong>' + esc(d.name) + "</strong> " + esc(d.label) + '</td><td>' + esc(d.driveType) + " · " + esc(d.format) + '</td><td class="num">' + fmtBytes(d.totalBytes) + '</td><td class="num">' + fmtBytes(d.freeBytes) + '</td><td>' + meter(d.usedPercent) + '<span class="small muted">' + fmtPct(d.usedPercent) + "</span></td></tr>").join("") +
      "</tbody></table></div>" : '<div class="empty">Nessun disco leggibile.</div>';

    renderProcessTable($("#processes-table"), p.topProcesses, true);
  }

  function processFlags(x) {
    const flags = [];
    if (x.protected) flags.push('<span class="pill pill-neutral">Windows</span>');
    if (x.highImpact) flags.push('<span class="pill pill-info">alto impatto</span>');
    if (x.unknown) flags.push('<span class="pill pill-warn">cartella insolita</span>');
    return flags.join(" ");
  }

  function renderProcessTable(container, processes, showAll) {
    if (!processes.length) {
      container.innerHTML = '<div class="empty">' + (showAll ? "Nessun processo leggibile." : "Nessun programma ad alto impatto in questo momento.") + "</div>";
      return;
    }
    container.innerHTML = '<div class="table-wrap"><table class="table"><thead><tr><th>Processo</th><th class="num">CPU</th><th class="num">Memoria</th><th>Note</th><th>Percorso</th><th class="actions"></th></tr></thead><tbody>' +
      processes.map((x) => '<tr><td><strong>' + esc(x.name) + '</strong><div class="small muted">' + esc(x.description || "") + (x.company ? " · " + esc(x.company) : "") + " · PID " + x.pid + '</div></td>' +
        '<td class="num">' + fmtPct(x.cpuPercent) + '</td><td class="num">' + fmtBytes(x.memoryBytes) + '</td><td>' + processFlags(x) + '</td><td class="path">' + esc(x.path || "(non leggibile)") + '</td>' +
        '<td class="actions">' + (x.protected ? "" : '<button type="button" class="btn btn-sm btn-secondary" data-kill="' + x.pid + '" data-name="' + esc(x.name) + '">Termina…</button>') + "</td></tr>").join("") +
      "</tbody></table></div>";
    $$("[data-kill]", container).forEach((btn) => btn.addEventListener("click", () => killProcess(Number(btn.dataset.kill), btn.dataset.name)));
  }

  async function loadPerformance(force) {
    const sample = Number($("#sample-select").value || 2000);
    if (state.performance && !force && state.performance.sampleMilliseconds >= sample - 300) {
      renderPerformance(state.performance);
      return;
    }
    $("#processes-table").innerHTML = '<div class="empty">Campionamento di ' + (sample / 1000) + " secondi in corso…</div>";
    const p = await api("/api/performance?sampleMs=" + sample);
    state.performance = p;
    renderPerformance(p);
  }

  async function killProcess(pid, name) {
    const ok = await confirmAction({
      title: "Terminare " + name + "?",
      html: "<p>Il processo <strong>" + esc(name) + "</strong> (PID " + pid + ") verrà chiuso immediatamente.</p><p>Se è un programma in uso, <strong>i dati non salvati andranno persi</strong>. Se è un servizio, Windows potrebbe riavviarlo da solo.</p>",
      impact: "Il programma si chiude subito, senza salvare.",
      reversible: "Parzialmente: puoi riaprire il programma, ma non recuperare i dati non salvati.",
      confirmLabel: "Termina processo"
    });
    if (!ok) return;
    try {
      let outcome = await api("/api/performance/process/kill", { method: "POST", body: { pid, name, confirmed: true, allowElevation: false } });
      if (!outcome.success && outcome.requiresAdmin) {
        const elevate = await confirmAction({
          title: "Servono i permessi di amministratore",
          html: "<p>Il processo è protetto. PC Check può chiedere a Windows i permessi di amministratore (finestra UAC) per eseguire <code>taskkill /PID " + pid + " /F</code> una sola volta.</p>",
          impact: "Comparirà la richiesta UAC di Windows.",
          reversible: "Come sopra.",
          confirmLabel: "Riprova come amministratore"
        });
        if (elevate) {
          outcome = await api("/api/performance/process/kill", { method: "POST", body: { pid, name, confirmed: true, allowElevation: true } });
        }
      }
      showOutcome(outcome);
      state.overview = null;
      await loadPerformance(true);
      if (currentSection() === "interventi") await loadActions(true);
    } catch (e) {
      toast(e.message, "error");
    }
  }

  // ------------------------------------------------------------------
  // Avvio e App
  // ------------------------------------------------------------------
  function renderStartup(entries) {
    const enabled = entries.filter((e) => e.enabled).length;
    $("#startup-summary").textContent = entries.length + " voci, " + enabled + " attive";
    const el = $("#startup-table");
    if (!entries.length) { el.innerHTML = '<div class="empty">Nessuna voce di avvio trovata.</div>'; return; }
    el.innerHTML = '<div class="table-wrap"><table class="table"><thead><tr><th>Nome</th><th>Stato</th><th>Ambito</th><th>Origine</th><th>Comando</th><th class="actions"></th></tr></thead><tbody>' +
      entries.map((e) => '<tr><td><strong>' + esc(e.name) + "</strong>" + (e.publisher ? '<div class="small muted">' + esc(e.publisher) + "</div>" : "") + (e.unknown ? ' <span class="pill pill-warn">da verificare</span>' : "") + '</td>' +
        '<td>' + (e.enabled ? '<span class="pill pill-ok">attiva</span>' : '<span class="pill pill-neutral">disattivata</span>') + '</td><td>' + esc(e.scope) + (e.requiresAdmin ? '<div class="small muted">richiede amministratore</div>' : "") + '</td><td class="small">' + esc(e.location) + '</td><td class="path">' + esc(e.command) + '</td>' +
        '<td class="actions">' + (e.canToggle ? '<button type="button" class="btn btn-sm btn-secondary" data-toggle="' + esc(e.id) + '" data-enabled="' + (e.enabled ? 1 : 0) + '" data-name="' + esc(e.name) + '" data-admin="' + (e.requiresAdmin ? 1 : 0) + '">' + (e.enabled ? "Disattiva…" : "Attiva…") + "</button>" : "") + "</td></tr>").join("") +
      "</tbody></table></div>";
    $$("[data-toggle]", el).forEach((btn) => btn.addEventListener("click", () => toggleStartup(btn.dataset.toggle, btn.dataset.enabled !== "1", btn.dataset.name, btn.dataset.admin === "1")));
  }

  async function toggleStartup(id, enable, name, requiresAdmin) {
    const ok = await confirmAction({
      title: (enable ? "Attivare" : "Disattivare") + " \"" + name + "\" all'avvio?",
      html: "<p>" + (enable ? "Il programma tornerà ad avviarsi automaticamente al prossimo accesso a Windows." : "Il programma <strong>non si avvierà più automaticamente</strong> al prossimo accesso. Resta installato e puoi aprirlo manualmente.") + "</p>" +
        "<p>PC Check scrive solo nella chiave <code>StartupApproved</code>, la stessa usata da Gestione attività: la voce originale non viene cancellata.</p>" +
        (requiresAdmin ? "<p><strong>Voce di sistema:</strong> Windows chiederà i permessi di amministratore (UAC) per eseguire <code>reg.exe add</code> su questa sola voce.</p>" : ""),
      impact: enable ? "Avvio leggermente più lento, programma disponibile subito." : "Avvio più rapido; il programma non parte da solo.",
      reversible: "Sì, in qualsiasi momento da qui o da Gestione attività.",
      confirmLabel: enable ? "Attiva" : "Disattiva",
      danger: false
    });
    if (!ok) return;
    try {
      const outcome = await api("/api/startup/toggle", { method: "POST", body: { id, enabled: enable, confirmed: true } });
      showOutcome(outcome);
      state.overview = null;
      state.startup = await api("/api/startup");
      renderStartup(state.startup);
      if (currentSection() === "interventi") renderActionsStartup();
    } catch (e) {
      toast(e.message, "error");
    }
  }

  function renderApps() {
    const filter = ($("#apps-filter").value || "").toLowerCase().trim();
    const onlyFlagged = $("#apps-only-flagged").checked;
    const apps = state.apps.filter((a) => {
      if (onlyFlagged && !(a.hints && a.hints.length)) return false;
      if (!filter) return true;
      return (a.name || "").toLowerCase().includes(filter) || (a.publisher || "").toLowerCase().includes(filter);
    });
    const el = $("#apps-table");
    if (!apps.length) { el.innerHTML = '<div class="empty">Nessun programma corrisponde ai criteri.</div>'; return; }
    el.innerHTML = '<div class="small muted" style="margin-bottom:6px">' + apps.length + " programmi su " + state.apps.length + "</div>" +
      '<div class="table-wrap"><table class="table"><thead><tr><th>Programma</th><th>Editore</th><th>Versione</th><th>Installato</th><th class="num">Dimensione</th><th>Note</th><th class="actions"></th></tr></thead><tbody>' +
      apps.map((a) => '<tr><td><strong>' + esc(a.name) + '</strong><div class="path">' + esc(a.installLocation || "") + '</div></td><td>' + (a.publisher ? esc(a.publisher) : '<span class="text-warn">non dichiarato</span>') + '</td><td>' + esc(a.version || "") + '</td><td>' + esc(fmtDateOnly(a.installDate)) +
        '</td><td class="num">' + (a.estimatedSizeBytes ? fmtBytes(a.estimatedSizeBytes) : "") + '</td><td>' + (a.hints || []).map((h) => '<span class="pill pill-warn">' + esc(h) + "</span>").join(" ") + "</td>" +
        '<td class="actions"><button type="button" class="btn btn-sm btn-secondary" data-uninstall="' + esc(a.id) + '" data-name="' + esc(a.name) + '" data-msi="' + (a.isMsi ? 1 : 0) + '">Disinstalla…</button></td></tr>').join("") +
      "</tbody></table></div>";
    $$("[data-uninstall]", el).forEach((btn) => btn.addEventListener("click", () => uninstallApp(btn.dataset.uninstall, btn.dataset.name, btn.dataset.msi === "1")));
  }

  async function uninstallApp(id, name, isMsi) {
    const winget = state.info && state.info.wingetAvailable;
    const tool = winget ? "<code>winget uninstall --exact --name \"" + esc(name) + "\" --interactive</code>" : (isMsi ? "Windows Installer (<code>msiexec /x {codice prodotto}</code>)" : "la pagina Impostazioni > App di Windows");
    const ok = await confirmAction({
      title: "Disinstallare \"" + name + "\"?",
      html: "<p>Verrà avviata la <strong>procedura standard di disinstallazione</strong> tramite " + tool + ". Il programma di disinstallazione mostrerà le proprie finestre: segui le indicazioni.</p><p>PC Check non esegue mai le stringhe di disinstallazione lette dal registro.</p>",
      impact: "Il programma e le sue impostazioni potrebbero essere rimossi dal computer.",
      reversible: "No: per tornare indietro dovrai reinstallare il programma.",
      confirmLabel: "Avvia disinstallazione"
    });
    if (!ok) return;
    try {
      const outcome = await api("/api/apps/uninstall", { method: "POST", body: { id, confirmed: true } });
      showOutcome(outcome);
      state.overview = null;
      state.apps = await api("/api/apps");
      renderApps();
      if (currentSection() === "interventi") renderActionsApps();
    } catch (e) {
      toast(e.message, "error");
    }
  }

  async function loadStartupAndApps(force) {
    if (!state.startup.length || force) state.startup = await api("/api/startup");
    if (!state.apps.length || force) state.apps = await api("/api/apps");
    renderStartup(state.startup);
    renderApps();
  }

  // ------------------------------------------------------------------
  // Interventi
  // ------------------------------------------------------------------
  function renderTemp(temp) {
    const el = $("#temp-targets");
    el.innerHTML = temp.targets.map((t) => {
      const disabled = !t.exists || !t.accessible;
      return '<div class="temp-target"><div class="temp-target-head"><label class="inline"><input type="checkbox" data-target="' + esc(t.key) + '" ' + (disabled ? "disabled" : (t.key === "user" ? "checked" : "")) + '> <strong>' + esc(t.label) + "</strong></label>" +
        '<span><strong>' + fmtBytes(t.totalBytes) + "</strong> in " + num(t.fileCount) + " file · " + fmtBytes(t.olderThan7DaysBytes) + " più vecchi di 7 giorni</span></div>" +
        '<div class="path">' + esc(t.path) + "</div>" +
        (t.requiresAdmin ? '<div class="small text-warn">Richiede i permessi di amministratore per i file protetti.</div>' : "") +
        (t.error ? '<div class="small text-warn">' + esc(t.error) + "</div>" : "") +
        (t.topFolders && t.topFolders.length ? '<ul class="temp-folders">' + t.topFolders.map((f) => "<li>" + esc(f.path) + " · " + fmtBytes(f.bytes) + " (" + num(f.files) + " file)</li>").join("") + "</ul>" : "") +
        "</div>";
    }).join("") + '<div class="small muted">Analisi del ' + esc(fmtDate(temp.analyzedAt)) + " · totale recuperabile " + fmtBytes(temp.totalReclaimableBytes) + "</div>";
  }

  async function cleanTemp() {
    const targets = $$("#temp-targets [data-target]:checked").map((c) => c.dataset.target);
    if (!targets.length) { toast("Seleziona almeno una cartella.", "warn"); return; }
    const olderThan = Number($("#temp-older").value || 0);
    const elevate = $("#temp-elevate").checked && targets.includes("windows");
    const labels = { user: "%TEMP%", windows: "C:\\Windows\\Temp" };
    const ok = await confirmAction({
      title: "Eliminare i file temporanei?",
      html: "<p>Verranno eliminati i file in <strong>" + targets.map((t) => esc(labels[t] || t)).join("</strong> e <strong>") + "</strong>" + (olderThan ? " più vecchi di <strong>" + olderThan + " giorni</strong>" : " di <strong>qualsiasi età</strong>") + ".</p>" +
        "<ul><li>I file in uso da programmi aperti vengono saltati automaticamente.</li><li>Nessun file fuori da queste due cartelle viene toccato.</li>" + (elevate ? "<li>Per C:\\Windows\\Temp comparirà la richiesta UAC: PC Check avvierà una propria copia elevata solo per questa pulizia.</li>" : "") + "</ul>",
      impact: "Alcuni programmi dovranno ricreare i propri file temporanei (può rallentare il primo avvio).",
      reversible: "No: i file eliminati non finiscono nel Cestino. Sono però file temporanei che Windows può ricreare.",
      confirmLabel: "Elimina file temporanei"
    });
    if (!ok) return;
    const out = $("#temp-result");
    out.innerHTML = '<div class="muted">Pulizia in corso…</div>';
    try {
      const results = await api("/api/temp/clean", { method: "POST", body: { targets, olderThanDays: olderThan, confirmed: true, elevate } });
      out.innerHTML = results.map((r) => '<div class="alert ' + (r.success ? "alert-ok" : "alert-error") + '"><strong>' + esc(labels[r.target] || r.target || "") + "</strong>: " + esc(r.message) +
        (r.success ? '<div class="small">Liberati ' + fmtBytes(r.freedBytes) + " · " + num(r.deletedFiles) + " file e " + num(r.deletedDirectories) + " cartelle eliminati · " + num(r.skippedFiles) + " saltati" + (r.elevationUsed ? " · con permessi di amministratore" : "") + "</div>" : "") +
        (r.errors && r.errors.length ? '<div class="small muted">' + r.errors.map(esc).join("<br>") + "</div>" : "") + "</div>").join("");
      const freed = results.reduce((s, r) => s + (r.freedBytes || 0), 0);
      toast("Pulizia terminata: liberati " + fmtBytes(freed), "ok");
      state.overview = null;
      state.temp = await api("/api/temp?refresh=true");
      renderTemp(state.temp);
    } catch (e) {
      out.innerHTML = '<div class="alert alert-error">' + esc(e.message) + "</div>";
    }
  }

  function renderActionsStartup() {
    const el = $("#actions-startup");
    const items = state.startup.filter((s) => s.enabled && s.unknown);
    if (!items.length) { el.innerHTML = '<div class="empty">Nessuna voce di avvio sospetta: tutte le voci attive provengono da cartelle di programmi o di sistema.</div>'; return; }
    el.innerHTML = '<div class="table-wrap"><table class="table"><thead><tr><th>Nome</th><th>Comando</th><th class="actions"></th></tr></thead><tbody>' +
      items.map((e) => '<tr><td><strong>' + esc(e.name) + "</strong>" + (e.publisher ? '<div class="small muted">' + esc(e.publisher) + "</div>" : '<div class="small text-warn">editore non dichiarato</div>') + '</td><td class="path">' + esc(e.command) + '</td><td class="actions"><button type="button" class="btn btn-sm btn-secondary" data-toggle="' + esc(e.id) + '" data-enabled="1" data-name="' + esc(e.name) + '" data-admin="' + (e.requiresAdmin ? 1 : 0) + '">Disattiva…</button></td></tr>').join("") +
      "</tbody></table></div>";
    $$("[data-toggle]", el).forEach((btn) => btn.addEventListener("click", () => toggleStartup(btn.dataset.toggle, false, btn.dataset.name, btn.dataset.admin === "1")));
  }

  function renderActionsApps() {
    const el = $("#actions-apps");
    const items = state.apps.filter((a) => a.hints && a.hints.length);
    if (!items.length) { el.innerHTML = '<div class="empty">Nessun programma da verificare.</div>'; return; }
    el.innerHTML = '<div class="table-wrap"><table class="table"><thead><tr><th>Programma</th><th>Perché verificarlo</th><th>Installato</th><th class="actions"></th></tr></thead><tbody>' +
      items.map((a) => '<tr><td><strong>' + esc(a.name) + "</strong>" + (a.publisher ? '<div class="small muted">' + esc(a.publisher) + "</div>" : "") + '<div class="path">' + esc(a.installLocation || "") + '</div></td><td>' + a.hints.map((h) => '<span class="pill pill-warn">' + esc(h) + "</span>").join(" ") + "</td><td>" + esc(fmtDateOnly(a.installDate)) +
        '</td><td class="actions"><button type="button" class="btn btn-sm btn-secondary" data-uninstall="' + esc(a.id) + '" data-name="' + esc(a.name) + '" data-msi="' + (a.isMsi ? 1 : 0) + '">Disinstalla…</button></td></tr>').join("") +
      "</tbody></table></div>";
    $$("[data-uninstall]", el).forEach((btn) => btn.addEventListener("click", () => uninstallApp(btn.dataset.uninstall, btn.dataset.name, btn.dataset.msi === "1")));
  }

  async function loadActions(force) {
    const needPerf = !state.performance || force;
    const tasks = [
      (state.temp && !force) ? Promise.resolve(state.temp) : api("/api/temp" + (force ? "?refresh=true" : "")),
      needPerf ? api("/api/performance?sampleMs=1500") : Promise.resolve(state.performance),
      (state.startup.length && !force) ? Promise.resolve(state.startup) : api("/api/startup"),
      (state.apps.length && !force) ? Promise.resolve(state.apps) : api("/api/apps")
    ];
    if (needPerf) $("#actions-processes").innerHTML = '<div class="empty">Campionamento in corso…</div>';
    const [temp, perf, startup, apps] = await Promise.all(tasks);
    state.temp = temp; state.performance = perf; state.startup = startup; state.apps = apps;
    renderTemp(temp);
    renderProcessTable($("#actions-processes"), perf.highImpactProcesses.filter((p) => !p.protected), false);
    renderActionsStartup();
    renderActionsApps();
  }

  // ------------------------------------------------------------------
  // Report
  // ------------------------------------------------------------------
  function download(filename, content, type) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  function stamp() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, "0");
    return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + "-" + p(d.getHours()) + p(d.getMinutes());
  }

  function renderReportSummary(r) {
    const bySeverity = {};
    r.findings.forEach((f) => { bySeverity[f.severity] = (bySeverity[f.severity] || 0) + 1; });
    $("#report-summary").innerHTML =
      '<div class="cards" style="margin:12px 0">' +
      '<article class="card stat"><div class="stat-label">Segnalazioni</div><div class="stat-value">' + r.findings.length + '</div><div class="stat-detail">' + ["Critical", "High", "Medium", "Low", "Info"].filter((k) => bySeverity[k]).map((k) => bySeverity[k] + " " + ({ Critical: "gravi", High: "alte", Medium: "medie", Low: "basse", Info: "informative" })[k]).join(", ") + "</div></article>" +
      '<article class="card stat"><div class="stat-label">Minacce</div><div class="stat-value">' + r.threats.length + '</div><div class="stat-detail">' + r.threats.filter((t) => t.isActive).length + " attive</div></article>" +
      '<article class="card stat"><div class="stat-label">Avvio</div><div class="stat-value">' + r.startup.filter((s) => s.enabled).length + '</div><div class="stat-detail">voci attive su ' + r.startup.length + "</div></article>" +
      '<article class="card stat"><div class="stat-label">Programmi</div><div class="stat-value">' + r.apps.length + '</div><div class="stat-detail">' + r.apps.filter((a) => a.hints && a.hints.length).length + " da verificare</div></article>" +
      '<article class="card stat"><div class="stat-label">File temporanei</div><div class="stat-value">' + fmtBytes(r.temp.totalReclaimableBytes) + '</div><div class="stat-detail">recuperabili</div></article>' +
      "</div>" +
      '<h3>Segnalazioni</h3><ul class="findings">' + r.findings.map((f) => '<li class="finding">' + severityPill(f.severity) + '<div><div class="finding-title">' + esc(f.title) + '</div><div class="finding-desc">' + esc(f.description) + "</div></div><span></span></li>").join("") + "</ul>" +
      '<p class="small muted" style="margin-top:12px">' + esc(r.disclaimer) + "</p>";
  }

  async function generateReport() {
    const btn = $("#btn-report-generate");
    btn.disabled = true;
    $("#report-status").textContent = "Raccolta dei dati in corso (campionamento di 1,5 secondi)…";
    try {
      const r = await api("/api/report");
      state.report = r;
      $("#report-status").textContent = "Rapporto generato alle " + fmtDate(r.generatedAt) + ".";
      renderReportSummary(r);
      $("#btn-report-html").disabled = false;
      $("#btn-report-json").disabled = false;
      $("#btn-report-save").disabled = false;
    } catch (e) {
      $("#report-status").textContent = "Errore: " + e.message;
    } finally {
      btn.disabled = false;
    }
  }

  // ------------------------------------------------------------------
  // Eventi
  // ------------------------------------------------------------------
  function bindEvents() {
    $("#btn-refresh").addEventListener("click", () => {
      state.overview = null;
      showSection(currentSection(), true);
    });
    $("#btn-shutdown").addEventListener("click", async () => {
      const ok = await confirmAction({
        title: "Chiudere PC Check?",
        html: "<p>Il programma in background verrà arrestato e questa pagina smetterà di funzionare. Le scansioni di Microsoft Defender già avviate proseguono per conto proprio.</p>",
        impact: "Nessuno sul computer.",
        reversible: "Sì: basta riavviare PC Check.",
        confirmLabel: "Chiudi PC Check",
        danger: false
      });
      if (!ok) return;
      try {
        await api("/api/system/shutdown", { method: "POST" });
      } catch (e) { /* il server si sta chiudendo */ }
      document.body.innerHTML = '<div class="auth-error"><div class="auth-card"><h1>PC Check è stato chiuso</h1><p>Puoi chiudere questa scheda. Per usarlo di nuovo, riavvia PC Check.</p></div></div>';
    });
    $("#btn-scan-quick").addEventListener("click", () => startScan("quick"));
    $("#btn-scan-full").addEventListener("click", () => startScan("full"));
    $("#btn-scan-cancel").addEventListener("click", async () => {
      try { renderScan(await api("/api/defender/scan/cancel", { method: "POST" })); } catch (e) { toast(e.message, "error"); }
    });
    $("#sample-select").addEventListener("change", () => loadPerformance(true).catch((e) => toast(e.message, "error")));
    $("#apps-filter").addEventListener("input", renderApps);
    $("#apps-only-flagged").addEventListener("change", renderApps);
    $("#btn-temp-refresh").addEventListener("click", async () => {
      try { state.temp = await api("/api/temp?refresh=true"); renderTemp(state.temp); } catch (e) { toast(e.message, "error"); }
    });
    $("#btn-temp-clean").addEventListener("click", cleanTemp);
    $("#btn-report-generate").addEventListener("click", generateReport);
    $("#btn-report-json").addEventListener("click", () => {
      if (state.report) download("PC-Check-rapporto-" + stamp() + ".json", JSON.stringify(state.report, null, 2), "application/json");
    });
    $("#btn-report-html").addEventListener("click", async () => {
      try {
        const html = await apiText("/api/report/html");
        download("PC-Check-rapporto-" + stamp() + ".html", html, "text/html");
      } catch (e) { toast(e.message, "error"); }
    });
    $("#btn-report-save").addEventListener("click", async () => {
      try {
        const outcome = await api("/api/report/save", { method: "POST" });
        showOutcome(outcome);
      } catch (e) { toast(e.message, "error"); }
    });
  }

  // ------------------------------------------------------------------
  // Avvio
  // ------------------------------------------------------------------
  async function init() {
    if (!token) { showAuthError(); return; }
    bindEvents();
    try {
      await loadSystemInfo();
    } catch (e) {
      if (e.message !== "Sessione non valida.") setGlobalError(e.message);
      return;
    }
    $("#app").hidden = false;
    showSection(currentSection(), false);
    // Se una scansione era già in corso (pagina ricaricata), riprende il monitoraggio.
    api("/api/defender/scan").then((s) => { if (s && s.state === "Running") renderScan(s); }).catch(() => {});
  }

  document.addEventListener("DOMContentLoaded", init);
})();
