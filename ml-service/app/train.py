"""Offline training CLI.

Usage:
    python -m app.train [--contamination 0.02]

Reads data/features.csv (rows of canonical features + a weak label) and fits
the IsolationForest on the clean (label==0) rows, persisting to models/.
"""

from __future__ import annotations

import argparse
import csv
import os

from .features import FEATURE_ORDER
from .model import AnomalyModel
from .store import TRAIN_CSV


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the RXAC anomaly model.")
    parser.add_argument("--csv", default=TRAIN_CSV, help="path to features CSV")
    parser.add_argument("--contamination", type=float, default=0.02)
    args = parser.parse_args()

    if not os.path.exists(args.csv):
        raise SystemExit(f"no training data at {args.csv}")

    vectors = []
    with open(args.csv, newline="") as fh:
        for row in csv.DictReader(fh):
            if row.get("label") == "0":
                vectors.append([float(row[name]) for name in FEATURE_ORDER])

    model = AnomalyModel()
    info = model.train(vectors, contamination=args.contamination)
    print(f"Trained on {info['samples']} samples, {info['features']} features.")


if __name__ == "__main__":
    main()
