# RXAC ML Service

FastAPI service that ingests behavioral events from the plugin, scores them for
anomalies (IsolationForest, with a transparent heuristic fallback before any
training), exposes a live dashboard, and supports retraining from collected
data.

## Run

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

export RXAC_API_KEY=change-me        # must match plugin config.yml ml.api-key
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Management panel: <http://localhost:8000/>

## Management panel

A full role-based operations panel is served at `/`:

- **Sign in** with one of two roles (credentials from env, see below).
- **Overview** — top suspects + live flag feed.
- **Players** — searchable table with a 0–100 *suspicion score*, per-player top
  checks, and an **Inspect** modal showing the full violation breakdown, recent
  flags, and moderation actions.
- **Logs** — filterable violation history (by check / player).
- **Actions** — Kick / Clear-VL (admin + moderator) and Ban (admin only). The
  panel queues actions; the plugin polls `/api/actions/pending` and executes
  them on the server in real time.

### Credentials (set these!)

```bash
export RXAC_ADMIN_USER=admin     RXAC_ADMIN_PASSWORD=change-me-admin
export RXAC_MOD_USER=mod         RXAC_MOD_PASSWORD=change-me-mod
```

Defaults are `admin/admin` and `mod/mod` — change them before exposing the panel.

| Role | Can do |
|------|--------|
| moderator | view everything, **kick**, **clear VL** |
| admin | everything moderators can, plus **ban** |

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/ingest` | Batch event ingestion (plugin → service) |
| GET  | `/api/flags` | Recent violation flags |
| GET  | `/api/players` | Per-player suspicion aggregates |
| GET  | `/api/stats` | Global counters + model status |
| POST | `/api/train` | Retrain anomaly model from `data/features.csv` |

All POST endpoints require the `X-RXAC-Key` header.

## The (human-supervised) "auto-patch" loop

1. Plugin streams features + deterministic violations here continuously.
2. Events are persisted to `data/features.csv` with a weak label
   (violation = 1, otherwise 0).
3. The IsolationForest trains on the **clean** rows to learn normal behavior;
   anything far from that manifold scores high — catching *novel* cheats the
   static checks missed.
4. A human reviews flags on the dashboard, confirms/relabels, and calls
   `POST /api/train` (or `python -m app.train`) to redeploy.

This is the realistic version of "AI that patches bypasses": fast feedback,
human in the loop — not unsupervised magic.
