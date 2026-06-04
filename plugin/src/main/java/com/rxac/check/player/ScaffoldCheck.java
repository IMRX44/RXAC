package com.rxac.check.player;

import com.rxac.RXAC;
import com.rxac.check.BlockPlaceContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Multi-signal Scaffold detection. Legitimate bridging means looking down at the
 * face you place against, at a human cadence. Scaffold hacks bridge while facing
 * forward (or away), place against the air behind them, snap their rotation onto
 * the face for a single tick, and place at robotic, fixed intervals.
 *
 * <p>Each independent signal contributes violation level; combined signals on a
 * single placement escalate quickly and (if enabled) trigger a setback.</p>
 */
public final class ScaffoldCheck extends Check {

    public ScaffoldCheck(RXAC plugin) {
        super(plugin, "Scaffold", CheckCategory.PLAYER);
    }

    @Override
    public void onBlockPlace(PlayerData data, BlockPlaceContext ctx) {
        Player p = data.getPlayer();
        Block placed = ctx.getPlaced();

        boolean below = placed.getY() < Math.floor(p.getLocation().getY());
        long since = ctx.getTime() - data.lastBlockPlaceMs;
        data.lastBlockPlaceMs = ctx.getTime();
        if (!below) { reward(data, 0.4); return; }

        int signals = 0;
        StringBuilder why = new StringBuilder();
        float pitch = p.getLocation().getPitch();

        // Signal A: not looking down enough to see the face being placed against.
        if (pitch < cfgDouble("min-down-pitch", 30.0)) {
            signals++; why.append("pitch=").append(String.format("%.0f ", pitch));
        }

        // Signal B: rotation snap onto the face for a single tick (aim-then-place).
        if (data.deltaYaw > 25 && data.lastDeltaYaw() < 3) {
            signals++; why.append("snap ");
        }

        // Signal C: moving opposite to where the player is facing (placing behind).
        double moveYaw = Math.toDegrees(Math.atan2(-data.deltaX, data.deltaZ));
        double diff = Math.abs(wrap(moveYaw - p.getLocation().getYaw()));
        if (data.horizontalSpeed() > 0.12 && diff > 100) {
            signals++; why.append(String.format("behind=%.0f ", diff));
        }

        // Signal D: robotic placement cadence (very regular fast bursts).
        if (since > 0 && since < cfgInt("min-place-interval-ms", 110)) {
            signals++; why.append("rapid ");
        }

        if (signals >= 1) {
            // Escalate sharply when multiple independent signals agree.
            double amount = signals >= 2 ? 2.5 : 1.0;
            fail(data, amount, why.toString().trim() + " sig=" + signals);
            if (signals >= 2) {
                plugin.getSetbackManager().setback(data, "Scaffold");
            }
        } else {
            reward(data, 0.3);
        }
    }

    private static double wrap(double a) {
        a %= 360.0;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }
}
