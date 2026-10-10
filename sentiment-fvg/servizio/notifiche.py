"""Invio delle allerte allo staff: Telegram e, in aggiunta, un webhook generico
(utile per collegare posta elettronica, Slack, Teams o un'automazione n8n/Make).
"""
from __future__ import annotations

import logging

import httpx

from configurazione import IMPOSTAZIONI as I

log = logging.getLogger("notifiche")

_SIMBOLO = {"crisi": "🔴", "opportunita": "🟢"}


def _testo(allerta: dict) -> str:
    righe = [
        f"{_SIMBOLO.get(allerta['tipo'], '⚪')} {allerta['titolo']}",
        f"Tipo: {allerta['sottotipo']} · Gravità {allerta['gravita']}/3",
        allerta["descrizione"],
    ]
    if allerta["esempi"]:
        righe.append("\nEsempi:")
        for e in allerta["esempi"]:
            righe.append(f"• {e['testo'][:160]}" + (f"\n  {e['url']}" if e.get("url") else ""))
    return "\n".join(righe)


async def invia(allerta: dict) -> None:
    async with httpx.AsyncClient(timeout=10) as http:
        if I.telegram_bot_token and I.telegram_chat_staff:
            try:
                await http.post(
                    f"https://api.telegram.org/bot{I.telegram_bot_token}/sendMessage",
                    json={"chat_id": I.telegram_chat_staff, "text": _testo(allerta),
                          "disable_web_page_preview": True},
                )
            except httpx.HTTPError as e:
                log.error("Notifica Telegram non inviata: %s", e)
        if I.webhook_notifiche:
            try:
                await http.post(I.webhook_notifiche, json=allerta)
            except httpx.HTTPError as e:
                log.error("Webhook di notifica non raggiunto: %s", e)
