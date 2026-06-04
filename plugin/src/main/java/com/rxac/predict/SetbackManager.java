package com.rxac.predict;

import com.rxac.RXAC;
import com.rxac.player.PlayerData;
import org.bukkit.Location;

/**
 * Rubber-band / setback system. When a movement check is highly confident a
 * motion was impossible, instead of merely flagging we teleport the player back
 * to their last valid position. This neutralizes movement cheats in real time
 * (the cheater simply cannot make progress), which is far stronger than logging.
 *
 * <p>Opt-in via {@code setback.enabled}; rate-limited to avoid teleport spam.</p>
 */
public final class SetbackManager {

    private final RXAC plugin;

    public SetbackManager(RXAC plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("setback.enabled", false);
    }

    /** Record a position known to be physically valid. */
    public void markSafe(PlayerData data) {
        Location loc = data.getPlayer().getLocation();
        // Only update the anchor while genuinely supported, so we never rewind
        // a cheater back to mid-air.
        if (data.serverGround) data.lastSafe = loc.clone();
        else if (data.lastSafe == null) data.lastSafe = loc.clone();
    }

    /** Teleport the player back to their last safe location, if enabled. */
    public void setback(PlayerData data, String reason) {
        if (!enabled()) return;
        Location safe = data.lastSafe;
        if (safe == null) return;

        long cooldown = plugin.getConfig().getLong("setback.cooldown-ms", 400);
        long now = System.currentTimeMillis();
        if (now - data.lastSetbackMs < cooldown) return;
        data.lastSetbackMs = now;

        // Must run on the main thread (we already are, from dispatchMovement).
        data.teleporting = true;
        data.lastTeleportMs = now;
        data.hasPosition = false;
        data.getPlayer().teleport(safe);
        if (plugin.getConfig().getBoolean("setback.log", true)) {
            plugin.getLogger().info("[setback] " + data.getPlayer().getName()
                    + " -> " + reason);
        }
    }
}
