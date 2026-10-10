"""Bluesky: ricerca dei post tramite le API AT Protocol.

Serve una "password per app" (Impostazioni → Privacy e sicurezza → Password per app),
mai la password principale dell'account.
"""
from __future__ import annotations

import logging
from datetime import datetime

import httpx

from configurazione import IMPOSTAZIONI as I
from connettori import MenzioneGrezza

log = logging.getLogger("bluesky")
SERVER = "https://bsky.social/xrpc"

_sessione: dict = {}
_ultimo_visto: dict[str, str] = {}


async def _token(http: httpx.AsyncClient) -> str | None:
    if _sessione.get("accessJwt"):
        return _sessione["accessJwt"]
    r = await http.post(f"{SERVER}/com.atproto.server.createSession",
                        json={"identifier": I.bluesky_utente, "password": I.bluesky_password_app})
    if r.status_code != 200:
        log.error("Accesso a Bluesky non riuscito (%s)", r.status_code)
        return None
    _sessione.update(r.json())
    return _sessione["accessJwt"]


async def raccogli(ricerche: list[str]) -> list[MenzioneGrezza]:
    if not (I.bluesky_utente and I.bluesky_password_app):
        return []
    risultati: list[MenzioneGrezza] = []
    async with httpx.AsyncClient(timeout=15) as http:
        token = await _token(http)
        if not token:
            return []
        for ricerca in ricerche:
            r = await http.get(f"{SERVER}/app.bsky.feed.searchPosts",
                               params={"q": ricerca, "lang": "it", "sort": "latest", "limit": 50},
                               headers={"Authorization": f"Bearer {token}"})
            if r.status_code == 401:          # sessione scaduta: si rinnova al giro successivo
                _sessione.clear()
                break
            if r.status_code != 200:
                log.warning("Ricerca Bluesky non riuscita per «%s» (%s)", ricerca, r.status_code)
                continue
            limite = _ultimo_visto.get(ricerca, "")
            post = r.json().get("posts", [])
            for p in post:
                creato = p["record"].get("createdAt", "")
                if creato <= limite:
                    continue
                handle = p["author"]["handle"]
                risultati.append(MenzioneGrezza(
                    fonte="bluesky",
                    id_esterno=p["uri"],
                    testo=p["record"].get("text", ""),
                    url=f"https://bsky.app/profile/{handle}/post/{p['uri'].rsplit('/', 1)[-1]}",
                    testata="Bluesky",
                    autore=p["author"]["did"],
                    pubblicato=datetime.fromisoformat(creato.replace("Z", "+00:00")),
                    interazioni=p.get("likeCount", 0) + p.get("repostCount", 0) + p.get("replyCount", 0),
                ))
            if post:
                _ultimo_visto[ricerca] = max(p["record"].get("createdAt", "") for p in post)
    return risultati
