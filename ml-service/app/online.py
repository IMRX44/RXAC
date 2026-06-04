"""Online (streaming) anomaly layer.

Maintains running per-feature mean/variance over the live player population using
Welford's algorithm, so it continuously *learns* what "normal" looks like on THIS
server — no training step, no restart. Any behavior that sits many standard
deviations from the population is scored as anomalous, which is what lets the AI
react to brand-new cheats the moment their statistics deviate, before anyone has
labeled or retrained anything.

This is the honest core of "auto-detecting new bypasses": the model's notion of
normal drifts with the server, and outliers surface automatically.
"""

from __future__ import annotations

import math
import threading
from typing import List

from .features import FEATURE_ORDER


class OnlineStats:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.n = 0
        self._mean = [0.0] * len(FEATURE_ORDER)
        self._m2 = [0.0] * len(FEATURE_ORDER)

    def update(self, vec: List[float]) -> None:
        """Fold one sample into the running statistics."""
        with self._lock:
            self.n += 1
            for i, x in enumerate(vec):
                delta = x - self._mean[i]
                self._mean[i] += delta / self.n
                self._m2[i] += delta * (x - self._mean[i])

    def score(self, vec: List[float]) -> float:
        """Return a population-outlier score in [0, 1] (max z across features)."""
        with self._lock:
            if self.n < 30:
                return 0.0
            worst_z = 0.0
            for i, x in enumerate(vec):
                var = self._m2[i] / self.n
                std = math.sqrt(var) if var > 1e-9 else 0.0
                diff = abs(x - self._mean[i])
                if std > 1e-6:
                    worst_z = max(worst_z, diff / std)
                elif diff > 0.05 * (abs(self._mean[i]) + 1.0):
                    # The population has shown no variance in this feature yet,
                    # but this sample deviates from the learned constant — a
                    # novel behavior. Treat it as a strong outlier.
                    worst_z = max(worst_z, 6.0)
            # ~2σ starts to matter, ~6σ is clearly anomalous.
            return max(0.0, min(1.0, (worst_z - 2.0) / 4.0))

    def stats(self) -> dict:
        with self._lock:
            return {"samples": self.n,
                    "mean": {FEATURE_ORDER[i]: round(self._mean[i], 4)
                             for i in range(len(FEATURE_ORDER))}}


online = OnlineStats()
