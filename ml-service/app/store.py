"""In-memory rolling store for events, flags, and per-player aggregates.

Kept intentionally simple (deques + dicts) so the service runs with zero
external dependencies. For production, swap this for Postgres/Redis behind the
same interface. Feature rows are also appended to a CSV for offline training.
"""

from __future__ import annotations

import csv
import os
import threading
import time
from collections import defaultdict, deque
from typing import Deque, Dict, List

from .features import FEATURE_ORDER, to_vector

DATA_DIR = os.environ.get("RXAC_DATA_DIR", "data")
TRAIN_CSV = os.path.join(DATA_DIR, "features.csv")


class Store:
    def __init__(self, max_events: int = 5000, max_flags: int = 2000) -> None:
        self._lock = threading.Lock()
        self.events: Deque[dict] = deque(maxlen=max_events)
        self.flags: Deque[dict] = deque(maxlen=max_flags)
        self.players: Dict[str, dict] = {}
        self._stats = defaultdict(int)
        os.makedirs(DATA_DIR, exist_ok=True)

    def add_event(self, event: dict, anomaly: float) -> bool:
        """Record an event; returns True if it counts as a flag."""
        flagged = False
        with self._lock:
            event = {**event, "anomaly": anomaly}
            self.events.append(event)
            self._stats["events"] += 1

            p = self.players.setdefault(
                event["uuid"],
                {"uuid": event["uuid"], "name": event.get("name"),
                 "flags": 0, "max_anomaly": 0.0, "total_vl": 0.0, "last_seen": 0},
            )
            p["name"] = event.get("name", p["name"])
            p["last_seen"] = event.get("ts", int(time.time() * 1000))
            p["max_anomaly"] = max(p["max_anomaly"], anomaly)
            p["total_vl"] = max(p["total_vl"], float(event.get("features", {}).get("totalVl", 0)))

            if event.get("type") == "violation":
                self.flags.append(event)
                p["flags"] += 1
                self._stats["flags"] += 1
                flagged = True

        self._append_training_row(event, flagged)
        return flagged

    def _append_training_row(self, event: dict, flagged: bool) -> None:
        """Persist the feature vector + weak label for offline training."""
        vec = to_vector(event.get("features", {}))
        header = FEATURE_ORDER + ["label"]
        new_file = not os.path.exists(TRAIN_CSV)
        try:
            with open(TRAIN_CSV, "a", newline="") as fh:
                w = csv.writer(fh)
                if new_file:
                    w.writerow(header)
                # Weak label: 1 if this was a violation event, else 0.
                w.writerow(vec + [1 if flagged else 0])
        except OSError:
            pass

    def recent_flags(self, limit: int = 100) -> List[dict]:
        with self._lock:
            return list(self.flags)[-limit:][::-1]

    def player_list(self) -> List[dict]:
        with self._lock:
            return sorted(
                self.players.values(),
                key=lambda p: (p["flags"], p["max_anomaly"]),
                reverse=True,
            )

    def stats(self) -> dict:
        with self._lock:
            return {
                "events": self._stats["events"],
                "flags": self._stats["flags"],
                "players": len(self.players),
            }


store = Store()
