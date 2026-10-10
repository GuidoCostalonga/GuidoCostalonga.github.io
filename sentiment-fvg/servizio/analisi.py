"""Livello di analisi del linguaggio: sentimento, sarcasmo, emozioni, entità.

Usa Claude con uscita strutturata (schema JSON garantito) e analizza le menzioni
a lotti, per contenere costi e tempi. La chiave ANTHROPIC_API_KEY viene letta
dall'ambiente del server e non viene mai inviata al cruscotto.
"""
from __future__ import annotations

import json
import logging

import anthropic

from configurazione import IMPOSTAZIONI
from modelli import Analisi, Entita, MenzioneGrezza

log = logging.getLogger("analisi")

client = anthropic.AsyncAnthropic()  # legge ANTHROPIC_API_KEY dall'ambiente

ISTRUZIONI = """\
Sei un analista della comunicazione politica specializzato nel Friuli Venezia Giulia.
Ricevi menzioni pubbliche (articoli, post, commenti) e per ciascuna valuti la percezione
espressa dall'autore verso il bersaglio del discorso.

Criteri da applicare sempre:

1. Sarcasmo e ironia. Distingui l'approvazione letterale da quella ironica. Segnali tipici:
   lodi esagerate su fatti negativi ("bravissimi, altro cantiere fermo da due anni"),
   virgolette usate per prendere le distanze, emoji in contrasto con il testo (👏 dopo una
   critica), domande retoriche, "complimenti" o "grazie" rivolti a un disservizio.
   Se c'è sarcasmo, la polarità è quella REALE (di solito negativa), non quella letterale.

2. Varietà linguistiche del territorio. Riconosci friulano, triestino, bisiacco, veneto di
   Pordenone, sloveno e tedesco delle aree di confine, e le loro espressioni colorite.
   Un'imprecazione dialettale può indicare frustrazione, affetto o semplice intercalare:
   valuta il contesto. Indica la varietà nel campo "dialetto", oppure null se il testo è in
   italiano standard.

3. Emozioni. Assegna a rabbia, paura, entusiasmo, fiducia e tristezza un valore da 0 a 1
   ciascuno (non devono sommare a 1). Scegli l'emozione dominante; usa "indifferenza"
   per testi informativi o freddi.

4. Punteggio. Da -1 (molto negativo) a +1 (molto positivo); 0 per testi neutri o
   puramente informativi. La confidenza (da 0 a 1) misura quanto sei sicuro del giudizio:
   abbassala per testi brevi, ambigui o fuori contesto.

5. Entità. Estrai persone, partiti, enti (Regione, Comuni, aziende sanitarie...), riforme
   o leggi, località. Usa la forma estesa e normalizzata dei nomi.

6. Temi. Da 1 a 3 etichette brevi in italiano, minuscole, riutilizzabili tra menzioni
   diverse (es. "sanità", "viabilità", "sicurezza", "lavoro", "ambiente", "trasporti").

7. Ostilità. Da 0 a 1: insulti, minacce, linguaggio d'odio, toni aggressivi.

8. Motivazione. Una frase breve che spieghi il giudizio a un addetto stampa.

Gli articoli di cronaca che riportano fatti senza giudizio sono "neutro". Non attribuire
all'autore opinioni che non esprime. Restituisci un risultato per ogni menzione, con lo
stesso "id" ricevuto.
"""

_NUMERO = {"type": "number"}
_STRINGA_O_NULL = {"anyOf": [{"type": "string"}, {"type": "null"}]}

SCHEMA = {
    "type": "object",
    "properties": {
        "risultati": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "polarita": {"type": "string", "enum": ["positivo", "negativo", "neutro"]},
                    "punteggio": _NUMERO,
                    "confidenza": _NUMERO,
                    "sarcasmo": {"type": "boolean"},
                    "emozione_dominante": {
                        "type": "string",
                        "enum": ["rabbia", "paura", "entusiasmo", "fiducia", "tristezza", "indifferenza"],
                    },
                    "emozioni": {
                        "type": "object",
                        "properties": {k: _NUMERO for k in ("rabbia", "paura", "entusiasmo", "fiducia", "tristezza")},
                        "required": ["rabbia", "paura", "entusiasmo", "fiducia", "tristezza"],
                        "additionalProperties": False,
                    },
                    "dialetto": _STRINGA_O_NULL,
                    "entita": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "testo": {"type": "string"},
                                "tipo": {
                                    "type": "string",
                                    "enum": ["persona", "partito", "ente", "riforma", "localita", "altro"],
                                },
                            },
                            "required": ["testo", "tipo"],
                            "additionalProperties": False,
                        },
                    },
                    "temi": {"type": "array", "items": {"type": "string"}},
                    "bersaglio": _STRINGA_O_NULL,
                    "ostilita": _NUMERO,
                    "motivazione": {"type": "string"},
                },
                "required": [
                    "id", "polarita", "punteggio", "confidenza", "sarcasmo", "emozione_dominante",
                    "emozioni", "dialetto", "entita", "temi", "bersaglio", "ostilita", "motivazione",
                ],
                "additionalProperties": False,
            },
        }
    },
    "required": ["risultati"],
    "additionalProperties": False,
}


def _limita(valore: float, minimo: float, massimo: float) -> float:
    return max(minimo, min(massimo, float(valore)))


def _in_analisi(r: dict) -> Analisi:
    return Analisi(
        polarita=r["polarita"],
        punteggio=_limita(r["punteggio"], -1, 1),
        confidenza=_limita(r["confidenza"], 0, 1),
        sarcasmo=bool(r["sarcasmo"]),
        emozione_dominante=r["emozione_dominante"],
        emozioni={k: _limita(v, 0, 1) for k, v in r["emozioni"].items()},
        dialetto=r["dialetto"],
        entita=[Entita(**e) for e in r["entita"]],
        temi=[t.strip().lower() for t in r["temi"]][:3],
        bersaglio=r["bersaglio"],
        ostilita=_limita(r["ostilita"], 0, 1),
        motivazione=r["motivazione"],
    )


async def analizza_lotto(menzioni: list[MenzioneGrezza]) -> dict[str, Analisi]:
    """Analizza un lotto di menzioni. Restituisce {chiave: Analisi}.

    Le menzioni che non ricevono risposta valida vengono semplicemente escluse
    dal risultato: il chiamante le può rimettere in coda.
    """
    if not menzioni:
        return {}

    blocco = [
        {"id": m.chiave, "fonte": m.fonte, "testata": m.testata, "testo": m.testo[:4000]}
        for m in menzioni
    ]

    try:
        risposta = await client.beta.messages.create(
            model=IMPOSTAZIONI.modello,
            max_tokens=16000,
            # Le istruzioni non cambiano mai: restano in memoria tampone (prompt caching)
            system=[{"type": "text", "text": ISTRUZIONI, "cache_control": {"type": "ephemeral"}}],
            messages=[{
                "role": "user",
                "content": "Analizza queste menzioni:\n" + json.dumps(blocco, ensure_ascii=False),
            }],
            output_config={
                "effort": IMPOSTAZIONI.sforzo,
                "format": {"type": "json_schema", "schema": SCHEMA},
            },
            # Se un filtro di sicurezza declina la richiesta, il server la ripete
            # in automatico su un modello di riserva
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
    except anthropic.RateLimitError as e:
        log.warning("Limite di richieste raggiunto, riprovo più tardi: %s", e.message)
        return {}
    except anthropic.APIStatusError as e:
        log.error("Errore del servizio di analisi (%s): %s", e.status_code, e.message)
        return {}
    except anthropic.APIConnectionError:
        log.error("Servizio di analisi non raggiungibile")
        return {}

    if risposta.stop_reason == "refusal":
        log.warning("Lotto declinato dal modello: %s", risposta.stop_details)
        return {}
    if risposta.stop_reason == "max_tokens":
        log.warning("Risposta troncata: ridurre DIMENSIONE_LOTTO")
        return {}

    testo = next((b.text for b in risposta.content if b.type == "text"), "")
    try:
        risultati = json.loads(testo)["risultati"]
    except (json.JSONDecodeError, KeyError):
        log.error("Risposta non leggibile")
        return {}

    validi = {m.chiave for m in menzioni}
    esito: dict[str, Analisi] = {}
    for r in risultati:
        if r.get("id") in validi:
            try:
                esito[r["id"]] = _in_analisi(r)
            except (KeyError, TypeError, ValueError):
                log.warning("Risultato scartato per id %s", r.get("id"))
    return esito
