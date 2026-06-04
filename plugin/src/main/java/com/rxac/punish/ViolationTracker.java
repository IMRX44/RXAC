package com.rxac.punish;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.player.PlayerData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player violation ledger. Each check has its own decaying VL. When a
 * check's VL crosses its configured max, the {@link PunishmentManager} fires.
 *
 * <p>VL increases instantly on {@code fail} but decays continuously over time,
 * so a clean player drifts back to zero while a cheater accumulates.</p>
 */
public final class ViolationTracker {

    private static final class Entry {
        double vl;
        long lastUpdate = System.currentTimeMillis();
        boolean punished;
    }

    private final PlayerData data;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public ViolationTracker(PlayerData data) {
        this.data = data;
    }

    private Entry entry(Check check) {
        return entries.computeIfAbsent(check.getName(), k -> new Entry());
    }

    private void decay(Check check, Entry e) {
        long now = System.currentTimeMillis();
        double seconds = (now - e.lastUpdate) / 1000.0;
        if (seconds > 0) {
            e.vl = Math.max(0, e.vl - seconds * check.getDecayPerSecond());
            e.lastUpdate = now;
        }
    }

    public synchronized void fail(Check check, double amount, String debug) {
        Entry e = entry(check);
        decay(check, e);
        e.vl += amount;

        RXAC plugin = RXAC.get();
        PunishmentManager pm = plugin.getPunishmentManager();

        pm.alert(data, check, e.vl, debug);

        // Forward strong signals to the ML layer.
        double fwd = plugin.getConfig().getDouble("ml.forward-vl-threshold", 5);
        if (e.vl >= fwd) {
            plugin.getMlBridge().reportViolation(data, check, e.vl, debug);
        }

        if (e.vl >= check.getMaxVl() && !e.punished) {
            e.punished = true;
            pm.punish(data, check, e.vl);
        }
    }

    public synchronized void reward(Check check, double amount) {
        Entry e = entry(check);
        decay(check, e);
        e.vl = Math.max(0, e.vl - amount);
        if (e.vl < check.getMaxVl()) e.punished = false;
    }

    public synchronized double getVl(Check check) {
        Entry e = entry(check);
        decay(check, e);
        return e.vl;
    }

    /** Sum of all decayed VLs — a coarse "how sus is this player" score. */
    public synchronized double totalVl() {
        double total = 0;
        for (Entry e : entries.values()) total += e.vl;
        return total;
    }
}
