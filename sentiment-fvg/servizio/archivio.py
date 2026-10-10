"""Archivio SQLite delle menzioni analizzate e delle allerte.

Per volumi alti (oltre qualche decina di migliaia di menzioni al giorno) conviene
passare a PostgreSQL: le funzioni qui sotto sono l'unico punto da cambiare.
"""
from __future__ import annotations

import json
import sqlite3
from datetime import timedelta
from pathlib import Path

from configurazione import IMPOSTAZIONI
from modelli import Menzione, adesso


class Archivio:
    def __init__(self, percorso: str = IMPOSTAZIONI.percorso_db) -> None:
        Path(percorso).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(percorso, check_same_thread=False)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.executescript(
            """
            CREATE TABLE IF NOT EXISTS menzioni (
                chiave TEXT PRIMARY KEY,
                raccolto TEXT NOT NULL,
                dati TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_menzioni_raccolto ON menzioni(raccolto);
            CREATE TABLE IF NOT EXISTS allerte (
                id TEXT PRIMARY KEY,
                creata TEXT NOT NULL,
                dati TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS viste (
                chiave TEXT PRIMARY KEY,
                vista TEXT NOT NULL
            );
            """
        )
        self.db.commit()

    # Evita di analizzare due volte la stessa menzione (costo e conteggi falsati)
    def gia_vista(self, chiave: str) -> bool:
        cur = self.db.execute(
            "INSERT OR IGNORE INTO viste(chiave, vista) VALUES (?, ?)", (chiave, adesso().isoformat())
        )
        self.db.commit()
        return cur.rowcount == 0

    def salva_menzione(self, m: Menzione) -> None:
        self.db.execute(
            "INSERT OR REPLACE INTO menzioni(chiave, raccolto, dati) VALUES (?, ?, ?)",
            (m.chiave, m.raccolto, json.dumps(m.in_dizionario(), ensure_ascii=False)),
        )
        self.db.commit()

    def salva_allerta(self, allerta: dict) -> None:
        self.db.execute(
            "INSERT OR REPLACE INTO allerte(id, creata, dati) VALUES (?, ?, ?)",
            (allerta["id"], allerta["creata"], json.dumps(allerta, ensure_ascii=False)),
        )
        self.db.commit()

    def menzioni_recenti(self, ore: int = 24, limite: int = 2000) -> list[dict]:
        da = (adesso() - timedelta(hours=ore)).isoformat()
        righe = self.db.execute(
            "SELECT dati FROM menzioni WHERE raccolto >= ? ORDER BY raccolto DESC LIMIT ?",
            (da, limite),
        ).fetchall()
        return [json.loads(r[0]) for r in righe]

    def allerte_recenti(self, ore: int = 24) -> list[dict]:
        da = (adesso() - timedelta(hours=ore)).isoformat()
        righe = self.db.execute(
            "SELECT dati FROM allerte WHERE creata >= ? ORDER BY creata DESC", (da,)
        ).fetchall()
        return [json.loads(r[0]) for r in righe]

    def pulizia(self) -> None:
        """Cancella i dati più vecchi del periodo di conservazione stabilito."""
        limite = (adesso() - timedelta(days=IMPOSTAZIONI.giorni_conservazione)).isoformat()
        self.db.execute("DELETE FROM menzioni WHERE raccolto < ?", (limite,))
        self.db.execute("DELETE FROM allerte WHERE creata < ?", (limite,))
        self.db.execute("DELETE FROM viste WHERE vista < ?", (limite,))
        self.db.commit()
