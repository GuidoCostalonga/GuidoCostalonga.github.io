"""Strutture dati condivise tra raccolta, analisi, allerte e cruscotto."""
from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Literal

Fonte = Literal["rss", "bluesky", "x", "facebook", "instagram", "telegram", "webhook"]
Polarita = Literal["positivo", "negativo", "neutro"]
Emozione = Literal["rabbia", "paura", "entusiasmo", "fiducia", "tristezza", "indifferenza"]


def adesso() -> datetime:
    return datetime.now(timezone.utc)


def pseudonimo(autore: str | None) -> str | None:
    """Minimizzazione dei dati (GDPR): dell'autore si conserva solo un'impronta."""
    if not autore:
        return None
    return hashlib.sha256(autore.strip().lower().encode()).hexdigest()[:16]


@dataclass
class MenzioneGrezza:
    """Ciò che arriva da un connettore, prima dell'analisi."""
    fonte: Fonte
    id_esterno: str
    testo: str
    url: str | None = None
    testata: str | None = None          # nome della testata o del canale
    autore: str | None = None           # solo per calcolare il pseudonimo
    pubblicato: datetime = field(default_factory=adesso)
    interazioni: int = 0                # mi piace, condivisioni, risposte: solo informativo

    @property
    def chiave(self) -> str:
        return hashlib.sha256(f"{self.fonte}:{self.id_esterno}".encode()).hexdigest()[:24]


@dataclass
class Entita:
    testo: str
    tipo: Literal["persona", "partito", "ente", "riforma", "localita", "altro"]


@dataclass
class Analisi:
    polarita: Polarita
    punteggio: float                    # da -1 (molto negativo) a +1 (molto positivo)
    confidenza: float                   # da 0 a 1
    sarcasmo: bool
    emozione_dominante: Emozione
    emozioni: dict[str, float]          # rabbia, paura, entusiasmo, fiducia, tristezza
    dialetto: str | None                # es. "friulano", "triestino", "veneto", None
    entita: list[Entita]
    temi: list[str]
    bersaglio: str | None               # verso chi o cosa è rivolto il giudizio
    ostilita: float                     # da 0 a 1: insulti, aggressività
    motivazione: str                    # una riga, utile allo staff per capire il giudizio


@dataclass
class Menzione:
    chiave: str
    fonte: Fonte
    testo: str
    url: str | None
    testata: str | None
    autore_pseudonimo: str | None
    pubblicato: str                     # ISO 8601
    raccolto: str                       # ISO 8601
    interazioni: int
    analisi: Analisi

    def in_dizionario(self) -> dict:
        return asdict(self)
