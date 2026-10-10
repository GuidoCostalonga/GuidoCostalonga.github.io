"""X (già Twitter): ricerca recente tramite API v2.

La ricerca richiede un abbonamento a pagamento alle API di X: verificare il piano
e i limiti correnti sul portale sviluppatori prima dell'attivazione.
"""
from __future__ import annotations

import logging
from datetime import datetime

import httpx

from configurazione import IMPOSTAZIONI as I
from connettori import MenzioneGrezza

log = logging.getLogger("x")
_dall_id: dict[str, str] = {}


async def raccogli(ricerche: list[str]) -> list[MenzioneGrezza]:
    if not I.x_bearer_token:
        return []
    risultati: list[MenzioneGrezza] = []
    async with httpx.AsyncClient(timeout=15, headers={"Authorization": f"Bearer {I.x_bearer_token}"}) as http:
        for ricerca in ricerche:
            parametri = {
                "query": f"({ricerca}) lang:it -is:retweet",
                "max_results": 50,
                "tweet.fields": "created_at,public_metrics,author_id",
            }
            if ricerca in _dall_id:
                parametri["since_id"] = _dall_id[ricerca]
            r = await http.get("https://api.x.com/2/tweets/search/recent", params=parametri)
            if r.status_code == 429:
                log.warning("Limite API di X raggiunto: salto il giro")
                break
            if r.status_code != 200:
                log.warning("Ricerca X non riuscita (%s)", r.status_code)
                continue
            corpo = r.json()
            for t in corpo.get("data", []):
                m = t.get("public_metrics", {})
                risultati.append(MenzioneGrezza(
                    fonte="x",
                    id_esterno=t["id"],
                    testo=t["text"],
                    url=f"https://x.com/i/web/status/{t['id']}",
                    testata="X",
                    autore=t.get("author_id"),
                    pubblicato=datetime.fromisoformat(t["created_at"].replace("Z", "+00:00")),
                    interazioni=m.get("like_count", 0) + m.get("retweet_count", 0) + m.get("reply_count", 0),
                ))
            if corpo.get("meta", {}).get("newest_id"):
                _dall_id[ricerca] = corpo["meta"]["newest_id"]
    return risultati
