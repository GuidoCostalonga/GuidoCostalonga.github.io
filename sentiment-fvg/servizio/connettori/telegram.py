"""Canali Telegram pubblici tramite Telethon (protocollo MTProto).

Il Bot API di Telegram riceve solo i messaggi dei canali in cui il bot è
amministratore; per leggere canali pubblici di terzi serve un account con
api_id e api_hash (my.telegram.org). Al primo avvio Telethon chiede il codice
di verifica inviato al telefono e salva la sessione in dati/telegram.session.
"""
from __future__ import annotations

import asyncio
import logging
from typing import Callable

from configurazione import CARTELLA, IMPOSTAZIONI as I
from connettori import MenzioneGrezza, filtro_parole

log = logging.getLogger("telegram")


async def ascolta(canali: list[str], parole: Callable[[], list[str]], coda: asyncio.Queue) -> None:
    """Resta in ascolto e mette in coda i nuovi messaggi pertinenti."""
    if not (I.telegram_api_id and I.telegram_api_hash and canali):
        return
    try:
        from telethon import TelegramClient, events
    except ImportError:
        log.warning("Telethon non installato: connettore Telegram disattivato")
        return

    client = TelegramClient(str(CARTELLA / "dati" / "telegram"), int(I.telegram_api_id), I.telegram_api_hash)

    @client.on(events.NewMessage(chats=canali))
    async def nuovo(evento):
        testo = evento.raw_text or ""
        if not filtro_parole(parole())(testo):     # le parole possono cambiare nel tempo
            return
        chat = await evento.get_chat()
        nome = getattr(chat, "username", None)
        await coda.put(MenzioneGrezza(
            fonte="telegram",
            id_esterno=f"{evento.chat_id}:{evento.id}",
            testo=testo,
            url=f"https://t.me/{nome}/{evento.id}" if nome else None,
            testata=getattr(chat, "title", "Telegram"),
            pubblicato=evento.date,
            interazioni=getattr(evento.message, "views", 0) or 0,
        ))

    await client.start()
    log.info("In ascolto su %d canali Telegram", len(canali))
    await client.run_until_disconnected()
