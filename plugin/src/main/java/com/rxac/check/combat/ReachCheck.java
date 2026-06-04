package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Reach with attacker-side latency rewind. Instead of measuring from a single
 * eye position, we take the <b>minimum</b> distance from the attacker's recent
 * eye positions (within a ping-bounded window) to the target's hitbox. This
 * gives the player the full benefit of their own latency, so legitimate hits
 * never false-flag — yet a reach hack still exceeds the cap from every sample.
 */
public final class ReachCheck extends Check {

    public ReachCheck(RXAC plugin) {
        super(plugin, "Reach", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        double max = cfgDouble("max-reach", 3.04);
        int ping = safePing(data);
        long window = (long) Math.min(cfgDouble("max-rewind-ms", 300), ping + 60);

        BoundingBox box = ctx.getTarget().getBoundingBox();
        double best = ctx.getReach();   // fallback: event-time distance

        long now = ctx.getTime();
        for (double[] s : data.getRecentPositions()) {
            if (now - s[3] > window) continue;
            // Eye position from a recent feet sample (~1.62 standing eye height).
            double ex = s[0], ey = s[1] + 1.62, ez = s[2];
            double cx = clamp(ex, box.getMinX(), box.getMaxX());
            double cy = clamp(ey, box.getMinY(), box.getMaxY());
            double cz = clamp(ez, box.getMinZ(), box.getMaxZ());
            double d = new Vector(ex - cx, ey - cy, ez - cz).length();
            if (d < best) best = d;
        }

        if (best > max) {
            fail(data, Math.min(3.0, 1 + (best - max) * 5),
                    String.format("reach=%.3f>%.2f (ping=%d)", best, max, ping));
        } else {
            reward(data, 0.5);
        }
    }

    private int safePing(PlayerData data) {
        try {
            return Math.max(0, data.getPlayer().getPing());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }
}
