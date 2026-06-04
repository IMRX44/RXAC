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
import threading

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from . import auth
from .features import FEATURE_ORDER, to_vector
from .model import AnomalyModel
from .online import online
from .schemas import (ActionRequest, IngestRequest, IngestResponse,
                      LoginRequest, LoginResponse, TrainResponse)
from .store import TRAIN_CSV, store

API_KEY = os.environ.get("RXAC_API_KEY", "change-me")
STATIC_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")

app = FastAPI(title="RXAC ML Service", version="0.1.0")
model = AnomalyModel()

# Auto-retrain: after this many fresh clean samples, retrain in the background
# so the model keeps adapting to the server without any manual step.
AUTO_RETRAIN_EVERY = int(os.environ.get("RXAC_AUTO_RETRAIN_EVERY", "750"))
_clean_since_train = 0
_training = False


def _maybe_auto_retrain() -> None:
    """Kick a background retrain once enough new clean data has accumulated."""
    global _clean_since_train, _training
    if _training or _clean_since_train < AUTO_RETRAIN_EVERY:
        return
    if not os.path.exists(TRAIN_CSV):
        return
    _training = True
    _clean_since_train = 0

    def _run():
        global _training
        try:
            vectors = []
            with open(TRAIN_CSV, newline="") as fh:
                for row in csv.DictReader(fh):
                    if row.get("label") == "0":
                        vectors.append([float(row[n]) for n in FEATURE_ORDER])
            model.train(vectors)
        except Exception:
            pass
        finally:
            _training = False

    threading.Thread(target=_run, daemon=True).start()


def _auth(key: str | None) -> None:
    """Plugin <-> service shared-secret auth."""
    if API_KEY and key != API_KEY:
        raise HTTPException(status_code=401, detail="bad or missing X-RXAC-Key")


def _bearer(authorization: str | None) -> str | None:
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[7:]
    return authorization


def require_role(*allowed: str):
    """Panel auth dependency enforcing one of the allowed roles."""
    def dep(authorization: str | None = Header(default=None)) -> dict:
        sess = auth.session(_bearer(authorization))
        if not sess:
            raise HTTPException(status_code=401, detail="login required")
        if allowed and sess["role"] not in allowed:
            raise HTTPException(status_code=403, detail="insufficient role")
        return sess
    return dep


# ----- panel auth -----

@app.post("/api/login", response_model=LoginResponse)
def login(req: LoginRequest):
    result = auth.login(req.username, req.password)
    if not result:
        raise HTTPException(status_code=401, detail="invalid credentials")
    token, role = result
    return LoginResponse(token=token, role=role)


@app.get("/api/me")
def me(session: dict = Depends(require_role())):
    return {"user": session["user"], "role": session["role"]}


@app.post("/api/logout")
def do_logout(authorization: str | None = Header(default=None)):
    auth.logout(_bearer(authorization))
    return {"ok": True}


@app.post("/api/ingest", response_model=IngestResponse)
def ingest(req: IngestRequest, x_rxac_key: str | None = Header(default=None)):
    _auth(x_rxac_key)
    global _clean_since_train
    scored = flagged = 0
    # Aggregate the worst anomaly per player in this batch -> verdicts.
    worst: dict[str, dict] = {}
    for ev in req.events:
        anomaly = model.score(ev.features)
        scored += 1
        is_flag = store.add_event(ev.model_dump(), anomaly)
        if is_flag:
            flagged += 1
        else:
            # Feed only non-violation samples into the streaming baseline so the
            # learned "normal" stays clean, and count toward auto-retrain.
            online.update(to_vector(ev.features))
            _clean_since_train += 1
        cur = worst.get(ev.uuid)
        if cur is None or anomaly > cur["anomaly"]:
            worst[ev.uuid] = {"uuid": ev.uuid, "name": ev.name, "anomaly": anomaly}

    _maybe_auto_retrain()
    return IngestResponse(
        received=len(req.events),
        scored=scored,
        flagged=flagged,
        verdicts=list(worst.values()),
    )


@app.get("/api/flags")
def flags(limit: int = 100, check: str = None, player: str = None,
          session: dict = Depends(require_role())):
    return store.recent_flags(limit, check=check, player=player)


@app.get("/api/logs")
def logs(limit: int = 200, check: str = None, player: str = None,
         session: dict = Depends(require_role())):
    return store.recent_flags(limit, check=check, player=player)


@app.get("/api/players")
def players(session: dict = Depends(require_role())):
    return store.player_list()


@app.get("/api/player/{uuid}")
def player_detail(uuid: str, session: dict = Depends(require_role())):
    detail = store.player_detail(uuid)
    if detail is None:
        raise HTTPException(status_code=404, detail="unknown player")
    return detail


@app.get("/api/stats")
def stats(session: dict = Depends(require_role())):
    s = store.stats()
    s["model_trained"] = model.is_trained()
    return s


# ----- moderation actions (panel -> queue -> plugin) -----

_ROLE_FOR_ACTION = {"ban": ("admin",), "kick": ("admin", "moderator"),
                    "clearvl": ("admin", "moderator")}


@app.post("/api/action")
def action(req: ActionRequest, authorization: str | None = Header(default=None)):
    sess = auth.session(_bearer(authorization))
    if not sess:
        raise HTTPException(status_code=401, detail="login required")
    allowed = _ROLE_FOR_ACTION.get(req.type)
    if allowed is None:
        raise HTTPException(status_code=400, detail="unknown action type")
    if sess["role"] not in allowed:
        raise HTTPException(status_code=403, detail=f"{req.type} requires {allowed}")

    store.enqueue_action({
        "uuid": req.uuid, "name": req.name, "type": req.type,
        "reason": req.reason, "by": sess["user"],
    })
    return {"queued": True, "type": req.type, "by": sess["user"]}


@app.get("/api/actions/pending")
def actions_pending(x_rxac_key: str | None = Header(default=None)):
    """The plugin polls this with the shared key and executes what it finds."""
    _auth(x_rxac_key)
    return {"actions": store.drain_actions()}


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
