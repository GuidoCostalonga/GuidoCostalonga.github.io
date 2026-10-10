"""Facebook tramite Meta Graph API: commenti ai post della PROPRIA pagina.

Limite importante: Meta non consente di cercare liberamente i post pubblici di
Facebook e Instagram. Con un token di pagina si leggono soltanto i contenuti e i
commenti delle pagine che si amministrano. La versione della Graph API va
indicata in META_VERSIONE_GRAPH (es. "v21.0") dopo averla verificata sulla
documentazione ufficiale per sviluppatori Meta.
"""
from __future__ import annotations

import logging
from datetime import datetime

import httpx

from configurazione import IMPOSTAZIONI as I
from connettori import MenzioneGrezza

log = logging.getLogger("meta")


async def raccogli() -> list[MenzioneGrezza]:
    if not (I.meta_page_token and I.meta_page_id and I.meta_versione_graph):
        return []
    base = f"https://graph.facebook.com/{I.meta_versione_graph}"
    campi = "message,permalink_url,created_time,comments.limit(50){id,message,created_time,from}"
    async with httpx.AsyncClient(timeout=20) as http:
        r = await http.get(f"{base}/{I.meta_page_id}/posts",
                           params={"fields": campi, "limit": 10, "access_token": I.meta_page_token})
    if r.status_code != 200:
        log.warning("Lettura pagina Facebook non riuscita (%s)", r.status_code)
        return []

    risultati: list[MenzioneGrezza] = []
    for post in r.json().get("data", []):
        for c in post.get("comments", {}).get("data", []):
            if not c.get("message"):
                continue
            risultati.append(MenzioneGrezza(
                fonte="facebook",
                id_esterno=c["id"],
                testo=c["message"],
                url=post.get("permalink_url"),
                testata="Facebook, commenti alla pagina",
                autore=(c.get("from") or {}).get("id"),
                pubblicato=datetime.strptime(c["created_time"], "%Y-%m-%dT%H:%M:%S%z"),
            ))
    return risultati
