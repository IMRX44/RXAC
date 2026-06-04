"""Anomaly detection model.

Wraps a scikit-learn IsolationForest trained on *legitimate* player behavior.
At inference the model returns an anomaly score in [0, 1] (higher = more
anomalous). When no trained model is present it falls back to a transparent
heuristic so the service is useful on day one, before any data is collected.
"""

from __future__ import annotations

import os
from typing import List, Optional

import numpy as np

from .features import FEATURE_ORDER, to_vector

MODEL_PATH = os.environ.get("RXAC_MODEL_PATH", "models/anomaly.joblib")


class AnomalyModel:
    def __init__(self) -> None:
        self._pipeline = None  # sklearn Pipeline(StandardScaler, IsolationForest)
        self.load()

    # --- persistence ------------------------------------------------------
    def load(self) -> bool:
        if not os.path.exists(MODEL_PATH):
            return False
        try:
            import joblib

            self._pipeline = joblib.load(MODEL_PATH)
            return True
        except Exception:
            self._pipeline = None
            return False

    def is_trained(self) -> bool:
        return self._pipeline is not None

    # --- training ---------------------------------------------------------
    def train(self, vectors: List[List[float]], contamination: float = 0.02):
        """Fit on a matrix of legitimate-behavior feature vectors."""
        from sklearn.ensemble import IsolationForest
        from sklearn.pipeline import Pipeline
        from sklearn.preprocessing import StandardScaler
        import joblib

        if len(vectors) < 50:
            raise ValueError("need at least 50 samples to train a useful model")

        x = np.asarray(vectors, dtype=float)
        pipeline = Pipeline(
            steps=[
                ("scaler", StandardScaler()),
                (
                    "iforest",
                    IsolationForest(
                        n_estimators=200,
                        contamination=contamination,
                        random_state=42,
                        n_jobs=-1,
                    ),
                ),
            ]
        )
        pipeline.fit(x)
        os.makedirs(os.path.dirname(MODEL_PATH) or ".", exist_ok=True)
        joblib.dump(pipeline, MODEL_PATH)
        self._pipeline = pipeline
        return {"samples": len(vectors), "features": len(FEATURE_ORDER)}

    # --- inference --------------------------------------------------------
    def score(self, features: dict) -> float:
        """Return an anomaly score in [0, 1]; higher is more suspicious."""
        vec = to_vector(features)
        if self._pipeline is not None:
            # IsolationForest.decision_function: higher = more normal.
            raw = float(self._pipeline.decision_function([vec])[0])
            # Map roughly [-0.5, 0.5] -> [1, 0] and clamp.
            return float(min(1.0, max(0.0, 0.5 - raw)))
        return self._heuristic(features)

    @staticmethod
    def _heuristic(f: dict) -> float:
        """Transparent pre-training fallback: combine obvious red flags."""
        score = 0.0
        score += min(1.0, max(0.0, (f.get("hSpeed", 0) - 0.36) / 0.4)) * 0.30
        score += min(1.0, max(0.0, (f.get("cps", 0) - 16) / 12)) * 0.25
        score += min(1.0, max(0.0, (f.get("airTicks", 0) - 80) / 80)) * 0.20
        score += min(1.0, f.get("totalVl", 0) / 40.0) * 0.25
        return float(min(1.0, score))
