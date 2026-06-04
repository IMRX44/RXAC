# RXAC — RX Anti-Cheat

> A full-stack, multi-layered, machine-learning-assisted anti-cheat for Minecraft Java servers (1.8 → 1.26 via Paper + ViaVersion).

RXAC is built around one honest principle: **no anti-cheat is 100% bypass-proof — anti-cheat is an arms race.** What RXAC *does* give you is a serious, professional, defense-in-depth architecture that makes bypasses as expensive as possible and catches them as fast as possible.

It combines:

1. **Deterministic packet-level checks** (the classic anti-cheat layer) — fast, low false-positive heuristics for movement and combat.
2. **A statistical / behavioral ML layer** — anomaly detection over per-player feature vectors to catch *new* cheats that slip past static checks.
3. **A live operations dashboard** — for staff to watch flags, review evidence, and ban.

## Architecture

```
┌────────────────────┐   features/violations   ┌─────────────────────┐
│   Core Plugin       │ ───────────────────────▶ │   ML Service        │
│   (Java / Paper)    │                          │   (Python/FastAPI)  │
│   ProtocolLib       │ ◀─────────────────────── │   scikit-learn      │
│   packet checks     │   anomaly verdicts       │   + Dashboard       │
└────────────────────┘                          └─────────────────────┘
        │                                                  ▲
        │ punishments / events                             │ browser
        ▼                                                  │
   Minecraft Server  ◀──────────────  Staff  ──────────────┘
```

| Layer | Tech | Directory | Responsibility |
|-------|------|-----------|----------------|
| Core plugin | Java 17, Paper API, ProtocolLib | [`plugin/`](plugin/) | Packet capture, deterministic movement & combat checks, violation/punishment system, ML bridge |
| ML service | Python 3.11, FastAPI, scikit-learn | [`ml-service/`](ml-service/) | Feature ingestion, anomaly detection, model training, REST API |
| Dashboard | HTML/JS (served by ML service) | [`ml-service/static/`](ml-service/static/) | Live flags, player inspection, manual bans |

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the deep dive.

## Detection coverage (initial)

**Movement:** Speed, Fly, NoFall, Motion, Timer, Phase, Jesus (water-walk), Step.
**Combat:** Reach, KillAura, AutoClicker / CPS, Aim, HitBox, Velocity (anti-knockback).

Every check produces a `CheckResult` with a violation level (VL). VLs decay over time and trigger configurable punishments (alert → kick → ban). High-VL events and full feature vectors are streamed to the ML service for secondary verification and offline retraining.

## Version support (1.8 → 1.26)

RXAC targets **Paper** and reads gameplay through **ProtocolLib** packets, so it is version-resilient. For *legacy clients* (1.8.x) connecting to a modern server, run **[ViaVersion](https://github.com/ViaVersion/ViaVersion)** + **ViaBackwards**; RXAC is `softdepend` on ViaVersion and adjusts thresholds per client protocol where relevant.

## Building

### Plugin
```bash
cd plugin
./gradlew shadowJar      # or: gradle shadowJar  (requires internet for deps)
# output: plugin/build/libs/RXAC-<version>.jar  → drop in server /plugins
```
Requires: JDK 17+, ProtocolLib installed on the server.

### ML service
```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
# dashboard: http://localhost:8000/
```

Point the plugin at the service via `config.yml → ml.endpoint`.

## Honest limitations

- "Stronger than Polar/Intave/Augustus" is not a measurable claim. RXAC is an open, extensible foundation — its real strength comes from tuning against *your* cheats over time.
- ML "auto-patching" here means: capture suspicious behavior → retrain model → redeploy. It is **human-supervised**, not magic.
- Ship with checks in **alert-only mode** first, gather data, tune thresholds, *then* enable auto-punishment. Aggressive defaults = false bans.

## License
MIT — see [`LICENSE`](LICENSE).
