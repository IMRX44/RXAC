"""RXAC ML service — FastAPI application.

Endpoints
---------
POST /api/ingest   ingest a batch of events from the plugin, score anomalies
GET  /api/flags    recent violation flags (for the dashboard)
GET  /api/players  per-player aggregate suspicion
GET  /api/stats    global counters
POST /api/train    retrain the anomaly model from the collected CSV
GET  /             the live dashboard (static HTML)
"""

from __future__ import annotations

import csv
import os

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .features import FEATURE_ORDER
from .model import AnomalyModel
from .schemas import IngestRequest, IngestResponse, TrainResponse
from .store import TRAIN_CSV, store

API_KEY = os.environ.get("RXAC_API_KEY", "change-me")
STATIC_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")

app = FastAPI(title="RXAC ML Service", version="0.1.0")
model = AnomalyModel()


def _auth(key: str | None) -> None:
    if API_KEY and key != API_KEY:
        raise HTTPException(status_code=401, detail="bad or missing X-RXAC-Key")


@app.post("/api/ingest", response_model=IngestResponse)
def ingest(req: IngestRequest, x_rxac_key: str | None = Header(default=None)):
    _auth(x_rxac_key)
    scored = flagged = 0
    for ev in req.events:
        anomaly = model.score(ev.features)
        scored += 1
        if store.add_event(ev.model_dump(), anomaly):
            flagged += 1
    return IngestResponse(received=len(req.events), scored=scored, flagged=flagged)


@app.get("/api/flags")
def flags(limit: int = 100):
    return store.recent_flags(limit)


@app.get("/api/players")
def players():
    return store.player_list()


@app.get("/api/stats")
def stats():
    s = store.stats()
    s["model_trained"] = model.is_trained()
    return s


@app.post("/api/train", response_model=TrainResponse)
def train(x_rxac_key: str | None = Header(default=None)):
    _auth(x_rxac_key)
    if not os.path.exists(TRAIN_CSV):
        return TrainResponse(trained=False, detail="no data collected yet")
    vectors = []
    with open(TRAIN_CSV, newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            # Train the anomaly model on clean (label==0) samples only.
            if row.get("label") == "0":
                vectors.append([float(row[name]) for name in FEATURE_ORDER])
    try:
        info = model.train(vectors)
    except ValueError as e:
        return TrainResponse(trained=False, detail=str(e))
    return TrainResponse(trained=True, detail="model retrained", samples=info["samples"])


@app.get("/")
def dashboard():
    return FileResponse(os.path.join(STATIC_DIR, "dashboard.html"))


# Serve any additional static assets under /static.
if os.path.isdir(STATIC_DIR):
    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
