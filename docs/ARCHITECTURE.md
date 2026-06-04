# RXAC Architecture

## Design goals

1. **Defense in depth.** Deterministic checks catch known cheats cheaply and
   with near-zero false positives; the ML layer catches *novel* behavior the
   static rules miss. Neither layer is trusted alone.
2. **Fail-open.** If the ML service is down, the plugin keeps running on its
   deterministic checks. Anti-cheat must never take the server down with it.
3. **Tune-then-punish.** Everything ships observable (alerts + dashboard)
   before it is allowed to punish. False bans are worse than missed cheats.
4. **Version resilience.** Reading gameplay through ProtocolLib packets keeps
   the core decoupled from server-version-specific NMS, and ViaVersion bridges
   legacy 1.8 clients.

## Data flow

```
 client packets
      │ (netty thread)
      ▼
 PacketListener ──► PlayerData (rolling per-player state)
      │                  │
      │ (main thread)    │ read by
      ▼                  ▼
 CheckManager ─────► Movement & Combat checks
      │                  │ fail()/reward()
      │                  ▼
      │            ViolationTracker (decaying VL per check)
      │                  │ threshold crossed
      │                  ▼
      │            PunishmentManager (alert → kick → ban)
      │
      └──► MLBridge ──(async HTTP batches)──► ML Service
                                                 │
                                          IsolationForest score
                                                 │
                                          Store + Dashboard
```

## Threading model

- Packets arrive on **netty threads**. We read raw fields and the precise
  arrival timestamp there (TimerCheck needs accurate timing), then bounce the
  `PlayerData` mutation + check dispatch to the **main thread** so checks may
  safely touch world state (block collision, potion effects).
- The **MLBridge** runs entirely on async scheduler threads and uses
  `java.net.http.HttpClient` so network latency never blocks gameplay.
- `ViolationTracker` is synchronized per player; `PlayerDataManager` is a
  `ConcurrentHashMap`.

## The check framework

Every detection extends `Check` and overrides only the hooks it needs:

| Hook | Fired by | Used by |
|------|----------|---------|
| `onMovement(data)` | every FLYING-family packet | Speed, Fly, NoFall, Motion, Timer, Phase, Jesus, Step, Velocity |
| `onAttack(data, ctx)` | `EntityDamageByEntityEvent` | Reach, KillAura, Aim, HitBox |
| `onSwing(data)` | `ARM_ANIMATION` packet | AutoClicker |
| `onVelocity(data)` | `PlayerVelocityEvent` | Velocity (records expectation) |

A check calls `fail(data, amount, debug)` to add violation level or
`reward(data, amount)` to trim it. VL **decays continuously** so legitimate
players drift back to zero while cheaters accumulate past the punish threshold.

### Why these checks

| Check | Principle exploited |
|-------|--------------------|
| Speed | Horizontal speed has a hard physical max per state (sprint/jump/potion). |
| Fly | Free-fall must accelerate downward; hovering/climbing in air is illegal. |
| NoFall | Client cannot claim `onGround` with no block beneath it while descending. |
| Motion | Vertical velocity follows `(v−0.08)·0.98`; deviation is anomalous. |
| Timer | Vanilla sends ~20 movement packets/s; more = sped-up client clock. |
| Phase | A body inside a full solid block violates collision. |
| Jesus | Walking flat across a liquid surface (|dY|≈0) instead of sinking. |
| Step | Rising >0.6 ground-to-ground without a jump is auto-step hacking. |
| Reach | Eye-to-hitbox distance beyond survival reach + latency slack. |
| KillAura | Multi-target in a tiny window, or hitting outside the view cone. |
| AutoClicker | CPS over human max, or robotic inter-click consistency. |
| Aim | Rotation snaps + collapsed sensitivity GCD (direct angle writes). |
| HitBox | Look ray fails to intersect the real (slightly expanded) target box. |
| Velocity | Player absorbs far less knockback than the server applied. |

## ML layer

- **Features** (`features.py`): a fixed-order vector — hSpeed, dY, yawDelta,
  pitchDelta, airTicks, cps, totalVl. Order is canonical; never reorder without
  retraining.
- **Model** (`model.py`): `StandardScaler → IsolationForest` trained on
  *legitimate* behavior (one-class anomaly detection). Falls back to a
  transparent heuristic before any training, so the service is useful immediately.
- **Feedback loop**: events persist to `data/features.csv` with weak labels;
  staff review flags on the dashboard, relabel, and retrain. This is the honest
  meaning of "auto-patching": fast, human-supervised retraining.

## Extending

- **New deterministic check**: subclass `Check`, register it in
  `CheckManager.register()`, add a config block under `checks.<category>.<name>`.
- **New ML feature**: append to `FEATURE_ORDER`, emit it from `MLBridge`, and
  retrain. Keep ordering stable.
- **Production storage**: replace `store.Store` (in-memory) with a DB-backed
  implementation behind the same method signatures.

## Known limitations & honesty

- No anti-cheat is unbypassable; this is an arms race. RXAC's edge comes from
  *combining* deterministic + behavioral layers and from continuous tuning.
- Default thresholds are conservative starting points. Run in `alert-only` mode,
  collect a few days of data on your player base, tune, then enable punishment.
- Packet-field offsets read via ProtocolLib can shift across major versions;
  validate the movement/swing reads when targeting a new server version.
