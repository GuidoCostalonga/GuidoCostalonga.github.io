/* ==========================================================
   Sala stampa: schede, filtri per categoria, ricerca,
   finestra di lettura con galleria.
   ========================================================== */
(function () {
  "use strict";
  var NOTIZIE = window.NOTIZIE || [];
  var CATEGORIE = window.CATEGORIE || {};
  var MESI = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"];
  var FRECCIA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>';

  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function data(d) {
    if (!d) return '<span class="da-verificare">Data DA VERIFICARE</span>';
    var p = d.split("-");
    return '<time datetime="' + d + '">' + parseInt(p[2], 10) + " " + MESI[parseInt(p[1], 10) - 1] + " " + p[0] + "</time>";
  }
  /* Le notizie tratte dal notiziario hanno solo il periodo di uscita (es. "Maggio 2026") */
  function dataNotizia(n) {
    if (n.periodo) return '<time datetime="' + n.data.slice(0, 7) + '">' + esc(n.periodo) + "</time>";
    return data(n.data);
  }
  function etichetta(cat) {
    var c = CATEGORIE[cat] || { nome: cat, classe: "" };
    return '<span class="etichetta ' + c.classe + '">' + esc(c.nome) + "</span>";
  }
  function scheda(n, i) {
    return '<article class="scheda comparsa ritardo-' + ((i % 3) + 1) + '" data-categoria="' + n.categoria + '" data-id="' + n.id + '">' +
      '<div class="scheda__immagine"><img src="' + n.immagine + '" alt="" loading="lazy" width="1600" height="1000">' + etichetta(n.categoria) + "</div>" +
      '<div class="scheda__corpo"><div class="scheda__meta">' + dataNotizia(n) + (n.verificato ? "" : ' · <span class="da-verificare">Testo DA VERIFICARE</span>') + "</div>" +
      "<h3>" + esc(n.titolo) + "</h3><p>" + esc(n.sintesi) + "</p>" +
      '<a class="scheda__link" href="notizie.html#' + n.id + '" data-apri="' + n.id + '">Leggi la notizia ' + FRECCIA + "</a></div></article>";
  }

  /* ---- Anteprima nella pagina principale ---- */
  var anteprima = document.getElementById("anteprima-notizie");
  if (anteprima) {
    var scelte = (anteprima.getAttribute("data-scelte") || "").split(",").filter(Boolean);
    var elenco = scelte.length ? scelte.map(function (id) { return NOTIZIE.find(function (n) { return n.id === id; }); }).filter(Boolean) : NOTIZIE.slice(0, 3);
    anteprima.innerHTML = elenco.map(scheda).join("");
    attivaComparse(anteprima);
  }

  /* ---- Archivio completo ---- */
  var archivio = document.getElementById("archivio-notizie");
  if (!archivio) return;

  var ordinate = NOTIZIE.slice().sort(function (a, b) {
    if (a.data && b.data) return b.data.localeCompare(a.data);
    if (a.data) return -1; if (b.data) return 1; return 0;
  });
  archivio.innerHTML = ordinate.map(scheda).join("") + '<p class="notizia-vuota" hidden>Nessuna notizia corrisponde alla ricerca.</p>';
  attivaComparse(archivio);

  var filtri = document.querySelectorAll(".filtro");
  var ricerca = document.getElementById("ricerca-notizie");
  var attiva = "tutte";

  filtri.forEach(function (f) {
    var cat = f.getAttribute("data-filtro");
    var n = cat === "tutte" ? NOTIZIE.length : NOTIZIE.filter(function (x) { return x.categoria === cat; }).length;
    f.insertAdjacentHTML("beforeend", ' <span class="filtro__conteggio">' + n + "</span>");
    f.addEventListener("click", function () {
      attiva = cat;
      filtri.forEach(function (g) { g.setAttribute("aria-pressed", String(g === f)); });
      applica();
    });
  });
  if (ricerca) ricerca.addEventListener("input", applica);

  function applica() {
    var q = ricerca ? ricerca.value.trim().toLowerCase() : "";
    var visibili = 0;
    archivio.querySelectorAll(".scheda").forEach(function (s) {
      var n = NOTIZIE.find(function (x) { return x.id === s.getAttribute("data-id"); });
      var testo = (n.titolo + " " + n.sintesi + " " + n.testo.join(" ")).toLowerCase();
      var ok = (attiva === "tutte" || n.categoria === attiva) && (!q || testo.indexOf(q) !== -1);
      s.classList.toggle("nascosta", !ok);
      if (ok) { visibili++; s.classList.add("visibile"); }
    });
    archivio.querySelector(".notizia-vuota").hidden = visibili > 0;
    var annuncio = document.getElementById("annuncio-filtri");
    if (annuncio) annuncio.textContent = visibili + (visibili === 1 ? " notizia trovata" : " notizie trovate");
  }

  /* ---- Finestra di lettura ---- */
  var finestra = document.getElementById("finestra-notizia");
  var ultimoFuoco = null;
  function apri(id) {
    var n = NOTIZIE.find(function (x) { return x.id === id; });
    if (!n || !finestra) return;
    ultimoFuoco = document.activeElement;
    var galleria = (n.galleria || []).map(function (g) {
      return '<figure><a href="' + g.src + '" target="_blank" rel="noopener"><img src="' + g.src + '" alt="' + esc(g.didascalia) + '" loading="lazy"></a><figcaption>' + esc(g.didascalia) + "</figcaption></figure>";
    }).join("");
    var fonte = n.fonte
      ? '<p class="scheda__meta">Fonte: <a href="' + n.fonte + '" target="_blank" rel="noopener">' + esc(n.fonteNome || n.fonte) + "</a></p>"
      : n.fonteNome
        ? '<p class="scheda__meta">Fonte: ' + esc(n.fonteNome) + "</p>"
        : '<p class="nota-verifica">Fonte ufficiale DA VERIFICARE prima della pubblicazione.</p>';
    finestra.querySelector(".finestra__contenuto").innerHTML =
      '<button class="finestra__chiudi" type="button" aria-label="Chiudi la notizia">×</button>' +
      '<div class="finestra__copertina"><img src="' + n.immagine + '" alt=""></div>' +
      '<div class="finestra__corpo"><div class="scheda__meta">' + etichetta(n.categoria) + " " + dataNotizia(n) + "</div>" +
      '<h2 id="titolo-finestra">' + esc(n.titolo) + "</h2>" +
      '<p style="font-size:1.12rem;color:var(--blu);font-weight:500">' + esc(n.sintesi) + "</p>" +
      n.testo.map(function (p) { return "<p>" + esc(p) + "</p>"; }).join("") +
      (galleria ? '<h3 style="margin-top:32px">Galleria</h3><div class="galleria">' + galleria + "</div>" : "") +
      fonte + "</div>";
    finestra.classList.add("aperta");
    document.body.style.overflow = "hidden";
    finestra.querySelector(".finestra__chiudi").addEventListener("click", chiudi);
    finestra.querySelector(".finestra__chiudi").focus();
    if (history.replaceState) history.replaceState(null, "", "#" + id);
  }
  function chiudi() {
    finestra.classList.remove("aperta");
    document.body.style.overflow = "";
    if (history.replaceState) history.replaceState(null, "", location.pathname);
    if (ultimoFuoco) ultimoFuoco.focus();
  }
  if (finestra) {
    finestra.addEventListener("click", function (e) { if (e.target === finestra) chiudi(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && finestra.classList.contains("aperta")) chiudi(); });
  }
  archivio.addEventListener("click", function (e) {
    var a = e.target.closest("[data-apri]");
    if (a) { e.preventDefault(); apri(a.getAttribute("data-apri")); }
  });
  if (location.hash.length > 1) apri(location.hash.slice(1));

  function attivaComparse(radice) {
    var el = radice.querySelectorAll(".comparsa");
    if (!("IntersectionObserver" in window)) { el.forEach(function (x) { x.classList.add("visibile"); }); return; }
    var o = new IntersectionObserver(function (v) { v.forEach(function (x) { if (x.isIntersecting) { x.target.classList.add("visibile"); o.unobserve(x.target); } }); }, { threshold: 0.1 });
    el.forEach(function (x) { o.observe(x); });
  }
})();
