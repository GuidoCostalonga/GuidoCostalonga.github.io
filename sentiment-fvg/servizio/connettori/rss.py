"""Testate giornalistiche e siti istituzionali tramite feed RSS o Atom."""
from __future__ import annotations

import asyncio
import calendar
import logging
from datetime import datetime, timezone

import feedparser
import httpx

from connettori import MenzioneGrezza, filtro_parole

log = logging.getLogger("rss")


async def raccogli(feed: list[dict], parole: list[str]) -> list[MenzioneGrezza]:
    pertinente = filtro_parole(parole)
    risultati: list[MenzioneGrezza] = []
    async with httpx.AsyncClient(timeout=15, follow_redirects=True,
                                 headers={"User-Agent": "MonitorPercezioneFVG/1.0"}) as http:
        risposte = await asyncio.gather(*(http.get(f["url"]) for f in feed), return_exceptions=True)

    for fonte, risposta in zip(feed, risposte):
        if isinstance(risposta, Exception) or risposta.status_code != 200:
            log.warning("Feed non raggiungibile: %s", fonte["url"])
            continue
        documento = feedparser.parse(risposta.content)
        for voce in documento.entries:
            testo = f"{voce.get('title', '')}. {voce.get('summary', '')}".strip()
            if not pertinente(testo):
                continue
            data = voce.get("published_parsed") or voce.get("updated_parsed")
            pubblicato = (datetime.fromtimestamp(calendar.timegm(data), tz=timezone.utc)
                          if data else datetime.now(timezone.utc))
            risultati.append(MenzioneGrezza(
                fonte="rss",
                id_esterno=voce.get("id") or voce.get("link") or testo[:120],
                testo=testo,
                url=voce.get("link"),
                testata=fonte.get("nome"),
                pubblicato=pubblicato,
            ))
    return risultati
