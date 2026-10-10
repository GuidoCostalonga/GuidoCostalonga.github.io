"""Connettori verso le fonti. Ognuno espone una funzione asincrona che restituisce
un elenco di MenzioneGrezza; si attiva solo se ha le credenziali necessarie.
"""
from __future__ import annotations

import re

from modelli import MenzioneGrezza


def filtro_parole(parole: list[str]):
    """Restituisce una funzione che tiene solo i testi che citano le parole monitorate."""
    if not parole:
        return lambda testo: True
    schema = re.compile(r"\b(" + "|".join(re.escape(p) for p in parole) + r")\b", re.IGNORECASE)
    return lambda testo: bool(schema.search(testo or ""))


__all__ = ["MenzioneGrezza", "filtro_parole"]
