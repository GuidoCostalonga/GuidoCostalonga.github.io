"""Servizio del Monitor della percezione pubblica FVG.

Avvio:  uvicorn app:app --host 127.0.0.1 --port 8080
(il servizio va esposto solo dietro un proxy inverso con HTTPS, vedi SICUREZZA.md)

Flusso:  connettori → coda → analisi a lotti (Claude) → archivio
         → motore allerte → notifiche staff + diffusione SSE al cruscotto
"""
from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import logging
import re
import time
from contextlib import asynccontextmanager, suppress
from datetime import timedelta

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from allerte import MotoreAllerte
from analisi import analizza_lotto
from archivio import Archivio
from configurazione import IMPOSTAZIONI as I, carica_fonti
from connettori import bluesky, meta, rss, telegram, x
from modelli import Menzione, MenzioneGrezza, adesso, pseudonimo
import notifiche

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
log = logging.getLogger("servizio")

archivio = Archivio()
motore = MotoreAllerte()
coda: asyncio.Queue[MenzioneGrezza] = asyncio.Queue(maxsize=5000)
iscritti: set[asyncio.Queue[str]] = set()
contatore_eventi = 0


# --------------------------------------------------------------------------- diffusione SSE

def _evento(tipo: str, dati: dict) -> str:
    global contatore_eventi
    contatore_eventi += 1
    return f"id: {contatore_eventi}\nevent: {tipo}\ndata: {json.dumps(dati, ensure_ascii=False)}\n\n"


def diffondi(tipo: str, dati: dict) -> None:
    messaggio = _evento(tipo, dati)
    for q in list(iscritti):
        if q.full():                       # cruscotto troppo lento: lo si scollega
            iscritti.discard(q)
        else:
            q.put_nowait(messaggio)


# --------------------------------------------------------------------------- cicli di lavoro

async def accoda(menzioni: list[MenzioneGrezza]) -> None:
    for m in menzioni:
        if m.testo.strip() and not archivio.gia_vista(m.chiave):
            await coda.put(m)


# Politico o partito monitorato in questo momento (varianti del nome, es. nome e sigla).
# Si aggiunge alle parole e alle ricerche di fonti.yaml e sopravvive ai riavvii.
FONTI = carica_fonti()
obiettivo: list[str] = archivio.leggi_impostazione("obiettivo", [])
risveglio = asyncio.Event()       # fa partire subito una raccolta quando cambia l'obiettivo


def parole_correnti() -> list[str]:
    return FONTI.get("parole_chiave", []) + obiettivo


def ricerche_correnti() -> list[str]:
    # Tra virgolette, così le piattaforme cercano il nome esatto
    return FONTI.get("ricerche_social", []) + [f'"{v}"' for v in obiettivo]


async def attendi(secondi: int) -> None:
    with suppress(asyncio.TimeoutError):
        await asyncio.wait_for(risveglio.wait(), secondi)


async def ciclo_rss() -> None:
    while True:
        try:
            await accoda(await rss.raccogli(FONTI.get("feed_rss", []), parole_correnti()))
        except Exception:
            log.exception("Errore nella lettura dei feed RSS")
        await attendi(I.intervallo_rss)


async def ciclo_social() -> None:
    while True:
        ricerche = ricerche_correnti()
        for connettore in (lambda: bluesky.raccogli(ricerche), lambda: x.raccogli(ricerche), meta.raccogli):
            try:
                await accoda(await connettore())
            except Exception:
                log.exception("Errore in un connettore social")
        await attendi(I.intervallo_social)


async def ciclo_telegram() -> None:
    coda_tg: asyncio.Queue = asyncio.Queue()
    ascolto = asyncio.create_task(
        telegram.ascolta(FONTI.get("canali_telegram", []), parole_correnti, coda_tg)
    )
    try:
        while True:
            await accoda([await coda_tg.get()])
    finally:
        ascolto.cancel()


async def ciclo_analisi() -> None:
    """Raccoglie un lotto (fino a DIMENSIONE_LOTTO o 5 secondi) e lo analizza."""
    while True:
        lotto = [await coda.get()]
        scadenza = time.monotonic() + 5
        while len(lotto) < I.dimensione_lotto and (resto := scadenza - time.monotonic()) > 0:
            with suppress(asyncio.TimeoutError):
                lotto.append(await asyncio.wait_for(coda.get(), resto))

        esiti = await analizza_lotto(lotto)
        nuove: list[dict] = []
        for grezza in lotto:
            analisi = esiti.get(grezza.chiave)
            if not analisi:
                continue
            m = Menzione(
                chiave=grezza.chiave, fonte=grezza.fonte, testo=grezza.testo, url=grezza.url,
                testata=grezza.testata, autore_pseudonimo=pseudonimo(grezza.autore),
                pubblicato=grezza.pubblicato.isoformat(), raccolto=adesso().isoformat(),
                interazioni=grezza.interazioni, analisi=analisi,
            )
            archivio.salva_menzione(m)
            dati = m.in_dizionario()
            nuove.append(dati)
            diffondi("menzione", dati)

        if len(esiti) < len(lotto):
            log.warning("%d menzioni non analizzate in questo lotto", len(lotto) - len(esiti))
            await asyncio.sleep(10)        # pausa prima del lotto successivo

        for allerta in motore.aggiungi(nuove):
            archivio.salva_allerta(allerta)
            diffondi("allerta", allerta)
            await notifiche.invia(allerta)


async def ciclo_pulizia() -> None:
    while True:
        archivio.pulizia()
        await asyncio.sleep(6 * 3600)


@asynccontextmanager
async def ciclo_vita(_: FastAPI):
    # Ricarica in memoria le ultime ore, così le allerte hanno subito una base di confronto
    motore.memoria.extend(sorted(archivio.menzioni_recenti(ore=I.base_ore), key=lambda m: m["raccolto"]))
    compiti = [asyncio.create_task(c()) for c in
               (ciclo_rss, ciclo_social, ciclo_telegram, ciclo_analisi, ciclo_pulizia)]
    yield
    for c in compiti:
        c.cancel()


app = FastAPI(title="Monitor percezione pubblica FVG", lifespan=ciclo_vita,
              docs_url="/api/documentazione" if I.modalita_sviluppo else None, redoc_url=None)

app.add_middleware(
    CORSMiddleware,
    allow_origins=I.origini_ammesse,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)


@app.middleware("http")
async def intestazioni_sicurezza(request: Request, chiamata):
    risposta = await chiamata(request)
    risposta.headers["X-Content-Type-Options"] = "nosniff"
    risposta.headers["Referrer-Policy"] = "no-referrer"
    risposta.headers["Cache-Control"] = "no-store"
    return risposta


# --------------------------------------------------------------------------- accesso

DURATA_SESSIONE = timedelta(hours=12)


def _firma(testo: str) -> str:
    return hmac.new(I.segreto_sessione.encode(), testo.encode(), hashlib.sha256).hexdigest()


def richiede_accesso(request: Request) -> None:
    if I.modalita_sviluppo and not I.codici_accesso:
        return
    gettone = request.cookies.get("sessione_monitor", "")
    scadenza, _, firma = gettone.partition(".")
    if not (scadenza.isdigit() and I.segreto_sessione
            and hmac.compare_digest(firma, _firma(scadenza)) and int(scadenza) > time.time()):
        raise HTTPException(status_code=401, detail="Accesso richiesto")


class RichiestaAccesso(BaseModel):
    codice: str = Field(min_length=8, max_length=200)


@app.post("/api/accesso")
async def accesso(dati: RichiestaAccesso, response: Response):
    await asyncio.sleep(0.5)               # rallenta i tentativi a raffica
    if not any(hmac.compare_digest(dati.codice, c) for c in I.codici_accesso):
        raise HTTPException(status_code=403, detail="Codice non valido")
    scadenza = str(int(time.time() + DURATA_SESSIONE.total_seconds()))
    response.set_cookie(
        "sessione_monitor", f"{scadenza}.{_firma(scadenza)}",
        max_age=int(DURATA_SESSIONE.total_seconds()), httponly=True, secure=True, samesite="lax",
    )
    return {"esito": "ok"}


@app.post("/api/esci")
async def esci(response: Response):
    response.delete_cookie("sessione_monitor")
    return {"esito": "ok"}


# --------------------------------------------------------------------------- dati per il cruscotto

@app.get("/api/salute")
async def salute():
    return {"stato": "attivo", "in_coda": coda.qsize(), "cruscotti_collegati": len(iscritti)}


@app.get("/api/istantanea", dependencies=[Depends(richiede_accesso)])
async def istantanea():
    return {
        "menzioni": archivio.menzioni_recenti(ore=24, limite=1500),
        "allerte": archivio.allerte_recenti(ore=24),
        "obiettivo": obiettivo,
        "generato": adesso().isoformat(),
    }


@app.get("/api/flusso", dependencies=[Depends(richiede_accesso)])
async def flusso(request: Request):
    q: asyncio.Queue[str] = asyncio.Queue(maxsize=500)
    iscritti.add(q)

    async def generatore():
        try:
            yield "retry: 5000\n\n"
            while not await request.is_disconnected():
                try:
                    yield await asyncio.wait_for(q.get(), timeout=15)
                except asyncio.TimeoutError:
                    yield ": battito\n\n"     # tiene viva la connessione attraverso i proxy
        finally:
            iscritti.discard(q)

    return StreamingResponse(generatore(), media_type="text/event-stream",
                             headers={"X-Accel-Buffering": "no"})


# --------------------------------------------------------------------------- obiettivo del monitoraggio

_VARIANTE = re.compile(r"^[\w][\w\s'’.&-]{1,79}$")


class Obiettivo(BaseModel):
    varianti: list[str] = Field(default_factory=list, max_length=5)


@app.get("/api/obiettivo", dependencies=[Depends(richiede_accesso)])
async def leggi_obiettivo():
    return {"varianti": obiettivo}


@app.post("/api/obiettivo", dependencies=[Depends(richiede_accesso)])
async def imposta_obiettivo(dati: Obiettivo):
    """Imposta il politico o il partito da cercare (lista vuota = nessuno)."""
    varianti = list(dict.fromkeys(" ".join(v.split()) for v in dati.varianti if v.strip()))
    if any(not _VARIANTE.match(v) for v in varianti):
        raise HTTPException(status_code=422, detail="Nome non valido: usare solo lettere, cifre, spazi, apostrofi, punti, trattini e &")
    obiettivo[:] = varianti
    archivio.scrivi_impostazione("obiettivo", varianti)
    risveglio.set()
    risveglio.clear()
    diffondi("obiettivo", {"varianti": varianti})
    log.info("Obiettivo del monitoraggio: %s", varianti or "nessuno")
    return {"varianti": varianti}


# --------------------------------------------------------------------------- ingresso esterno

class MenzioneEsterna(BaseModel):
    fonte: str = Field(default="webhook", max_length=20)
    id_esterno: str = Field(max_length=300)
    testo: str = Field(min_length=1, max_length=8000)
    url: str | None = Field(default=None, max_length=1000)
    testata: str | None = Field(default=None, max_length=200)
    autore: str | None = Field(default=None, max_length=200)


@app.post("/api/ingresso")
async def ingresso(request: Request):
    """Punto di ingresso per strumenti esterni (n8n, Make, servizi di monitoraggio).

    Il mittente firma il corpo della richiesta con SEGRETO_WEBHOOK e invia
    l'intestazione  X-Firma: sha256=<esadecimale>.
    """
    corpo = await request.body()
    attesa = "sha256=" + hmac.new(I.segreto_webhook.encode(), corpo, hashlib.sha256).hexdigest()
    if not I.segreto_webhook or not hmac.compare_digest(request.headers.get("X-Firma", ""), attesa):
        raise HTTPException(status_code=401, detail="Firma non valida")
    try:
        elenco = [MenzioneEsterna(**v) for v in json.loads(corpo)]
    except (ValueError, TypeError) as e:
        raise HTTPException(status_code=422, detail="Formato non valido") from e
    await accoda([MenzioneGrezza(fonte="webhook", id_esterno=v.id_esterno, testo=v.testo,
                                 url=v.url, testata=v.testata or v.fonte, autore=v.autore)
                  for v in elenco])
    return {"accolte": len(elenco)}
