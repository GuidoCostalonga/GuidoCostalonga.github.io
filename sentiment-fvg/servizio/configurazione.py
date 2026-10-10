"""Configurazione del servizio, letta dalle variabili d'ambiente (file .env).

Nessuna chiave privata deve mai finire nel codice o nel cruscotto: tutto passa
da qui e resta sul server.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

import yaml
from dotenv import load_dotenv

CARTELLA = Path(__file__).resolve().parent
load_dotenv(CARTELLA / ".env")


def _bool(nome: str, predefinito: bool = False) -> bool:
    return os.getenv(nome, str(predefinito)).strip().lower() in {"1", "true", "si", "sì", "yes"}


def _lista(nome: str) -> list[str]:
    return [v.strip() for v in os.getenv(nome, "").split(",") if v.strip()]


@dataclass(frozen=True)
class Impostazioni:
    # Motore di analisi (Claude)
    modello: str = os.getenv("MODELLO_ANALISI", "claude-opus-5-5")
    sforzo: str = os.getenv("SFORZO_ANALISI", "medium")  # low | medium | high
    dimensione_lotto: int = int(os.getenv("DIMENSIONE_LOTTO", "10"))

    # Archivio
    percorso_db: str = os.getenv("PERCORSO_DB", str(CARTELLA / "dati" / "menzioni.sqlite"))
    giorni_conservazione: int = int(os.getenv("GIORNI_CONSERVAZIONE", "30"))

    # Accesso al cruscotto
    codici_accesso: list[str] = field(default_factory=lambda: _lista("CODICI_ACCESSO"))
    segreto_sessione: str = os.getenv("SEGRETO_SESSIONE", "")
    origini_ammesse: list[str] = field(default_factory=lambda: _lista("ORIGINI_AMMESSE"))
    segreto_webhook: str = os.getenv("SEGRETO_WEBHOOK", "")

    # Soglie di allerta
    finestra_minuti: int = int(os.getenv("FINESTRA_MINUTI", "15"))
    base_ore: int = int(os.getenv("BASE_ORE", "6"))
    volume_minimo: int = int(os.getenv("VOLUME_MINIMO", "12"))
    calo_crisi: float = float(os.getenv("CALO_CRISI", "30"))
    quota_negativa_crisi: float = float(os.getenv("QUOTA_NEGATIVA_CRISI", "0.6"))
    crescita_opportunita: float = float(os.getenv("CRESCITA_OPPORTUNITA", "2.0"))
    indice_opportunita: float = float(os.getenv("INDICE_OPPORTUNITA", "40"))
    pausa_allerta_minuti: int = int(os.getenv("PAUSA_ALLERTA_MINUTI", "45"))

    # Notifiche allo staff
    telegram_bot_token: str = os.getenv("TELEGRAM_BOT_TOKEN", "")
    telegram_chat_staff: str = os.getenv("TELEGRAM_CHAT_STAFF", "")
    webhook_notifiche: str = os.getenv("WEBHOOK_NOTIFICHE", "")

    # Connettori (ognuno si attiva solo se ha le sue credenziali)
    intervallo_rss: int = int(os.getenv("INTERVALLO_RSS_SECONDI", "300"))
    intervallo_social: int = int(os.getenv("INTERVALLO_SOCIAL_SECONDI", "120"))
    bluesky_utente: str = os.getenv("BLUESKY_UTENTE", "")
    bluesky_password_app: str = os.getenv("BLUESKY_PASSWORD_APP", "")
    x_bearer_token: str = os.getenv("X_BEARER_TOKEN", "")
    meta_page_token: str = os.getenv("META_PAGE_TOKEN", "")
    meta_page_id: str = os.getenv("META_PAGE_ID", "")
    meta_versione_graph: str = os.getenv("META_VERSIONE_GRAPH", "")
    telegram_api_id: str = os.getenv("TELEGRAM_API_ID", "")
    telegram_api_hash: str = os.getenv("TELEGRAM_API_HASH", "")

    modalita_sviluppo: bool = _bool("MODALITA_SVILUPPO")


IMPOSTAZIONI = Impostazioni()


def carica_fonti() -> dict:
    """Legge fonti.yaml: elenco feed RSS, canali Telegram e parole da monitorare."""
    percorso = CARTELLA / "fonti.yaml"
    with percorso.open(encoding="utf-8") as f:
        return yaml.safe_load(f) or {}
