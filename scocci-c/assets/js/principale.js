/* ==========================================================
   Comportamenti comuni: menu, barra, comparse, contatori,
   modulo contatti, canali ufficiali.
   ========================================================== */
(function () {
  "use strict";
  var C = window.CONFIGURAZIONE || {};
  var DA_VERIFICARE = '<span class="da-verificare">DA VERIFICARE</span>';

  /* ---- Barra fissa ---- */
  var barra = document.querySelector(".barra");
  function aggiornaBarra() { if (barra) barra.classList.toggle("scorrimento", window.scrollY > 12); }
  window.addEventListener("scroll", aggiornaBarra, { passive: true });
  aggiornaBarra();

  /* ---- Menu su schermi piccoli ---- */
  var interruttore = document.querySelector(".interruttore-menu");
  var menu = document.getElementById("menu-principale");
  if (interruttore && menu) {
    interruttore.addEventListener("click", function () {
      var aperto = interruttore.getAttribute("aria-expanded") === "true";
      interruttore.setAttribute("aria-expanded", String(!aperto));
      menu.classList.toggle("aperto", !aperto);
    });
    menu.addEventListener("click", function (e) {
      if (e.target.tagName === "A") { interruttore.setAttribute("aria-expanded", "false"); menu.classList.remove("aperto"); }
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && menu.classList.contains("aperto")) { interruttore.click(); interruttore.focus(); }
    });
  }

  /* ---- Comparsa allo scorrimento ---- */
  var elementi = document.querySelectorAll(".comparsa");
  if ("IntersectionObserver" in window) {
    var osservatore = new IntersectionObserver(function (voci) {
      voci.forEach(function (v) { if (v.isIntersecting) { v.target.classList.add("visibile"); osservatore.unobserve(v.target); } });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    elementi.forEach(function (el) { osservatore.observe(el); });
  } else {
    elementi.forEach(function (el) { el.classList.add("visibile"); });
  }

  /* ---- Contatori animati ---- */
  var riduciMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  function anima(el) {
    var fine = parseFloat(el.getAttribute("data-valore"));
    if (isNaN(fine)) return;
    if (riduciMovimento) { el.textContent = fine.toLocaleString("it-IT"); return; }
    var durata = 700, inizio = null;
    function passo(t) {
      if (!inizio) inizio = t;
      var p = Math.min((t - inizio) / durata, 1);
      var facilitato = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(fine * facilitato).toLocaleString("it-IT");
      if (p < 1) requestAnimationFrame(passo);
    }
    requestAnimationFrame(passo);
  }
  var contatori = document.querySelectorAll("[data-valore]");
  if ("IntersectionObserver" in window) {
    var oc = new IntersectionObserver(function (voci) {
      voci.forEach(function (v) { if (v.isIntersecting) { anima(v.target); oc.unobserve(v.target); } });
    }, { threshold: 0.5 });
    contatori.forEach(function (el) { oc.observe(el); });
  } else { contatori.forEach(anima); }

  /* ---- Anno nel piè di pagina ---- */
  document.querySelectorAll("[data-anno]").forEach(function (el) { el.textContent = new Date().getFullYear(); });

  /* ---- Dati configurabili (email, canali) ---- */
  document.querySelectorAll("[data-config]").forEach(function (el) {
    var chiave = el.getAttribute("data-config");
    var valore = C[chiave];
    if (el.tagName === "A") {
      if (valore) {
        el.href = chiave === "emailSegreteria" ? "mailto:" + valore : valore;
        if (el.hasAttribute("data-mostra-valore")) el.textContent = valore;
        if (chiave !== "emailSegreteria") { el.target = "_blank"; el.rel = "noopener"; }
      } else {
        el.href = "contatti.html#canali";
        if (el.hasAttribute("data-mostra-valore")) el.innerHTML = DA_VERIFICARE;
        el.setAttribute("title", "Indirizzo DA VERIFICARE");
      }
    } else {
      el.innerHTML = valore ? valore : DA_VERIFICARE;
    }
  });

  /* ---- Flussi dei canali ufficiali ---- */
  function iframe(src, titolo) {
    return '<iframe src="' + src + '" title="' + titolo + '" loading="lazy" allow="encrypted-media; picture-in-picture" allowfullscreen></iframe>';
  }
  function vuoto(nome) {
    return '<div><p><strong>' + nome + '</strong></p><p>Il flusso si attiva inserendo l’indirizzo ufficiale nel file <code>assets/js/configurazione.js</code>.</p><p>' + DA_VERIFICARE + '</p></div>';
  }
  var flussi = {
    facebook: function () {
      return C.facebook ? iframe("https://www.facebook.com/plugins/page.php?href=" + encodeURIComponent(C.facebook) + "&tabs=timeline&width=340&height=500&small_header=true&adapt_container_width=true&hide_cover=false", "Pagina Facebook") : vuoto("Facebook");
    },
    instagram: function () {
      if (!C.instagram) return vuoto("Instagram");
      var url = C.instagram.replace(/\/+$/, "") + "/embed";
      return iframe(url, "Profilo Instagram");
    },
    youtube: function () {
      if (C.youtubeElencoCaricamenti) return iframe("https://www.youtube-nocookie.com/embed/videoseries?list=" + encodeURIComponent(C.youtubeElencoCaricamenti), "Ultimi video YouTube");
      return C.youtube ? '<div><p>Guarda tutti i video sul canale ufficiale.</p><a class="pulsante pulsante--blu pulsante--piccolo" target="_blank" rel="noopener" href="' + C.youtube + '">Apri il canale</a></div>' : vuoto("YouTube");
    },
    linkedin: function () {
      return C.linkedin ? '<div><p>Aggiornamenti istituzionali e professionali.</p><a class="pulsante pulsante--blu pulsante--piccolo" target="_blank" rel="noopener" href="' + C.linkedin + '">Segui su LinkedIn</a></div>' : vuoto("LinkedIn");
    }
  };
  document.querySelectorAll("[data-flusso]").forEach(function (el) {
    var f = flussi[el.getAttribute("data-flusso")];
    if (f) el.innerHTML = f();
  });

  /* ---- Modulo contatti ---- */
  var modulo = document.getElementById("modulo-contatti");
  if (modulo) {
    var esito = document.getElementById("esito-modulo");
    var regole = {
      nome: function (v) { return v.trim().length >= 2 || "Indica nome e cognome."; },
      email: function (v) { return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()) || "Inserisci un indirizzo email valido."; },
      oggetto: function (v) { return v.trim().length >= 3 || "Scrivi un oggetto breve."; },
      messaggio: function (v) { return v.trim().length >= 20 || "Il messaggio deve contenere almeno 20 caratteri."; }
    };
    function controlla(campo) {
      var regola = regole[campo.name];
      if (!regola) return true;
      var r = regola(campo.value);
      var contenitore = campo.closest(".campo");
      var errore = contenitore && contenitore.querySelector(".errore");
      if (r === true) { contenitore.classList.remove("non-valido"); if (errore) errore.textContent = ""; campo.removeAttribute("aria-invalid"); return true; }
      contenitore.classList.add("non-valido"); if (errore) errore.textContent = r; campo.setAttribute("aria-invalid", "true"); return false;
    }
    modulo.querySelectorAll("input, textarea").forEach(function (c) {
      c.addEventListener("blur", function () { controlla(c); });
      c.addEventListener("input", function () { if (c.closest(".campo") && c.closest(".campo").classList.contains("non-valido")) controlla(c); });
    });

    modulo.addEventListener("submit", function (e) {
      e.preventDefault();
      var valido = true, primo = null;
      ["nome", "email", "oggetto", "messaggio"].forEach(function (n) {
        var c = modulo.elements[n];
        if (!controlla(c)) { valido = false; primo = primo || c; }
      });
      var consenso = modulo.elements["consenso"];
      if (!consenso.checked) { valido = false; primo = primo || consenso; esito.className = "esito visibile"; esito.style.background = "#FDECEA"; esito.style.color = "#C62828"; esito.textContent = "Per inviare il messaggio è necessario accettare l’informativa sul trattamento dei dati."; }
      if (!valido) { primo.focus(); return; }

      var dati = new FormData(modulo);
      var tipo = dati.get("tipo") || "Cittadino";
      var testo = "Mittente: " + dati.get("nome") + " (" + tipo + ")\n" +
        (dati.get("ente") ? "Ente o testata: " + dati.get("ente") + "\n" : "") +
        "Email: " + dati.get("email") + "\n" +
        (dati.get("comune") ? "Comune: " + dati.get("comune") + "\n" : "") +
        "Tema: " + dati.get("tema") + "\n\n" + dati.get("messaggio");

      esito.style.background = ""; esito.style.color = "";

      if (C.indirizzoServizioModulo) {
        fetch(C.indirizzoServizioModulo, { method: "POST", body: dati, headers: { "Accept": "application/json" } })
          .then(function (r) { if (!r.ok) throw new Error(); esito.className = "esito visibile"; esito.textContent = "Grazie, il tuo messaggio è stato inviato. La segreteria ti risponderà al più presto."; modulo.reset(); })
          .catch(function () { esito.className = "esito visibile"; esito.style.background = "#FDECEA"; esito.style.color = "#C62828"; esito.textContent = "Invio non riuscito. Riprova tra qualche minuto oppure scrivi direttamente alla segreteria."; });
        return;
      }
      if (C.emailSegreteria) {
        window.location.href = "mailto:" + C.emailSegreteria + "?subject=" + encodeURIComponent("[" + tipo + "] " + dati.get("oggetto")) + "&body=" + encodeURIComponent(testo);
        esito.className = "esito visibile";
        esito.textContent = "Si è aperto il tuo programma di posta con il messaggio già pronto: premi Invia per completare.";
        return;
      }
      esito.className = "esito visibile";
      esito.style.background = "#FDECEA"; esito.style.color = "#C62828";
      esito.innerHTML = "Modulo in fase di attivazione: l’indirizzo della segreteria è " + DA_VERIFICARE + ". Nel frattempo puoi usare la PEC indicata a lato.";
    });
  }
})();
