"""Motore delle allerte: "Allerta crisi" e "Opportunità di consenso".

Confronta la finestra recente (es. ultimi 15 minuti) con la base di riferimento
(es. le 6 ore precedenti), sia sul totale sia per singolo tema ed entità.
Tutte le soglie si regolano dal file .env senza toccare il codice.
"""
from __future__ import annotations

import re
import uuid
from collections import Counter, defaultdict, deque
from datetime import datetime, timedelta
from statistics import mean

from configurazione import IMPOSTAZIONI as I
from modelli import adesso

_URL = re.compile(r"https?://\S+|@\w+|#")
_SPAZI = re.compile(r"\s+")


def indice_netto(menzioni: list[dict]) -> float:
    """Indice netto del sentimento, da -100 a +100.

    Ogni menzione pesa per la sua confidenza: un giudizio incerto conta meno.
    Il sarcasmo è già incorporato nella polarità restituita dall'analisi.
    """
    if not menzioni:
        return 0.0
    somma = sum(m["analisi"]["punteggio"] * m["analisi"]["confidenza"] for m in menzioni)
    pesi = sum(m["analisi"]["confidenza"] for m in menzioni) or 1
    return round(100 * somma / pesi, 1)


def quota(menzioni: list[dict], polarita: str) -> float:
    if not menzioni:
        return 0.0
    return sum(1 for m in menzioni if m["analisi"]["polarita"] == polarita) / len(menzioni)


def media_emozione(menzioni: list[dict], emozione: str) -> float:
    if not menzioni:
        return 0.0
    return mean(m["analisi"]["emozioni"].get(emozione, 0) for m in menzioni)


def _impronta(testo: str) -> str:
    """Impronta del testo per riconoscere messaggi copiati e incollati in serie."""
    pulito = _SPAZI.sub(" ", _URL.sub("", testo.lower())).strip()
    return pulito[:90]


class MotoreAllerte:
    def __init__(self) -> None:
        self.memoria: deque[dict] = deque()
        self.ultime: dict[tuple[str, str], datetime] = {}

    def _ambiti(self, m: dict) -> set[str]:
        a = m["analisi"]
        ambiti = {"generale"} | {f"tema:{t}" for t in a["temi"]}
        ambiti |= {f"{e['tipo']}:{e['testo']}" for e in a["entita"] if e["tipo"] in ("persona", "partito", "riforma")}
        return ambiti

    def aggiungi(self, menzioni: list[dict]) -> list[dict]:
        """Registra nuove menzioni analizzate e restituisce le allerte scattate."""
        ora = adesso()
        self.memoria.extend(menzioni)
        limite = ora - timedelta(hours=I.base_ore, minutes=I.finestra_minuti)
        while self.memoria and datetime.fromisoformat(self.memoria[0]["raccolto"]) < limite:
            self.memoria.popleft()

        inizio_finestra = ora - timedelta(minutes=I.finestra_minuti)
        recenti: dict[str, list[dict]] = defaultdict(list)
        base: dict[str, list[dict]] = defaultdict(list)
        for m in self.memoria:
            destinazione = recenti if datetime.fromisoformat(m["raccolto"]) >= inizio_finestra else base
            for ambito in self._ambiti(m):
                destinazione[ambito].append(m)

        allerte = []
        for ambito, finestra in recenti.items():
            riferimento = base.get(ambito, [])
            for allerta in (
                self._crisi(ambito, finestra, riferimento),
                self._ondata_coordinata(ambito, finestra),
                self._opportunita(ambito, finestra, riferimento),
            ):
                if allerta and self._fuori_pausa(allerta, ora):
                    allerte.append(allerta)
        return allerte

    # ------------------------------------------------------------------ regole

    def _ritmo(self, n: int, minuti: float) -> float:
        return n / max(minuti, 1)

    def _crescita(self, finestra: list[dict], riferimento: list[dict]) -> float:
        ritmo_ora = self._ritmo(len(finestra), I.finestra_minuti)
        ritmo_base = self._ritmo(len(riferimento), I.base_ore * 60)
        return ritmo_ora / ritmo_base if ritmo_base else float(len(finestra))

    def _crisi(self, ambito: str, finestra: list[dict], riferimento: list[dict]) -> dict | None:
        if len(finestra) < I.volume_minimo:
            return None
        ind_ora, ind_base = indice_netto(finestra), indice_netto(riferimento)
        neg = quota(finestra, "negativo")
        rabbia = media_emozione(finestra, "rabbia")
        calo = ind_base - ind_ora if riferimento else 0

        crollo = calo >= I.calo_crisi and neg >= 0.5
        indignazione = neg >= I.quota_negativa_crisi and rabbia >= 0.5
        if not (crollo or indignazione):
            return None

        gravita = 3 if (crollo and indignazione) or neg >= 0.8 else 2
        motivo = (
            f"indice sceso di {calo:.0f} punti rispetto alle ultime {I.base_ore} ore"
            if crollo else f"{neg:.0%} di menzioni negative con rabbia media {rabbia:.2f}"
        )
        return self._crea(
            "crisi", "indignazione" if indignazione else "calo improvviso", ambito, gravita,
            f"{len(finestra)} menzioni in {I.finestra_minuti} minuti, {motivo}.",
            finestra, {"indice": ind_ora, "indice_base": ind_base, "quota_negativa": round(neg, 2),
                       "rabbia_media": round(rabbia, 2), "crescita_volume": round(self._crescita(finestra, riferimento), 1)},
        )

    def _ondata_coordinata(self, ambito: str, finestra: list[dict]) -> dict | None:
        """Molti messaggi quasi identici, da autori diversi, in pochi minuti."""
        if len(finestra) < I.volume_minimo:
            return None
        gruppi: dict[str, set] = defaultdict(set)
        for m in finestra:
            if m["fonte"] != "rss":
                gruppi[_impronta(m["testo"])].add(m.get("autore_pseudonimo"))
        impronta, autori = max(gruppi.items(), key=lambda kv: len(kv[1]), default=("", set()))
        ostilita = mean(m["analisi"]["ostilita"] for m in finestra)
        # Testo identico da almeno 5 account e da almeno un quarto della finestra:
        # evita falsi allarmi per frasi brevi e comuni ("Vergogna!", "Bravi!")
        copia_incolla = len(autori) >= 5 and len(autori) >= 0.25 * len(finestra)
        if not copia_incolla and not (ostilita >= 0.6 and len(finestra) >= 2 * I.volume_minimo):
            return None
        dettaglio = (
            f"{len(autori)} account diversi pubblicano lo stesso testo"
            if copia_incolla else f"ostilità media {ostilita:.2f} su {len(finestra)} menzioni"
        )
        return self._crea(
            "crisi", "ondata coordinata", ambito, 3,
            f"Possibile azione organizzata: {dettaglio}. Valutare prima di rispondere.",
            finestra, {"account_testo_identico": len(autori), "ostilita_media": round(ostilita, 2)},
        )

    def _opportunita(self, ambito: str, finestra: list[dict], riferimento: list[dict]) -> dict | None:
        if ambito == "generale" or len(finestra) < max(4, I.volume_minimo // 2):
            return None
        crescita = self._crescita(finestra, riferimento)
        ind = indice_netto(finestra)
        entusiasmo = media_emozione(finestra, "entusiasmo")
        if crescita < I.crescita_opportunita or ind < I.indice_opportunita or entusiasmo < 0.4:
            return None
        return self._crea(
            "opportunita", "tema in crescita favorevole", ambito, 2 if ind < 60 else 3,
            f"Volume {crescita:.1f} volte superiore alla media, indice {ind:+.0f}, entusiasmo {entusiasmo:.2f}.",
            finestra, {"indice": ind, "crescita_volume": round(crescita, 1), "entusiasmo_medio": round(entusiasmo, 2)},
        )

    # ------------------------------------------------------------------ servizio

    def _fuori_pausa(self, allerta: dict, ora: datetime) -> bool:
        """Evita di ripetere la stessa allerta più volte di seguito."""
        chiave = (allerta["tipo"] + allerta["sottotipo"], allerta["ambito"])
        ultima = self.ultime.get(chiave)
        if ultima and ora - ultima < timedelta(minutes=I.pausa_allerta_minuti):
            return False
        self.ultime[chiave] = ora
        return True

    def _crea(self, tipo, sottotipo, ambito, gravita, descrizione, finestra, metriche) -> dict:
        nome = ambito.split(":", 1)[-1]
        titolo = ("Allerta crisi" if tipo == "crisi" else "Opportunità di consenso") + f": {nome}"
        temi = Counter(t for m in finestra for t in m["analisi"]["temi"]).most_common(3)
        esempi = sorted(finestra, key=lambda m: m["analisi"]["confidenza"], reverse=True)[:3]
        return {
            "id": uuid.uuid4().hex,
            "tipo": tipo,
            "sottotipo": sottotipo,
            "ambito": ambito,
            "titolo": titolo,
            "descrizione": descrizione,
            "gravita": gravita,
            "metriche": metriche,
            "temi_collegati": [t for t, _ in temi],
            "esempi": [{"testo": m["testo"][:280], "url": m.get("url"), "fonte": m["fonte"]} for m in esempi],
            "creata": adesso().isoformat(),
        }
