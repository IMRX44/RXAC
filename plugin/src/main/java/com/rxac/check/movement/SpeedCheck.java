package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.util.MovementUtil;
import org.bukkit.entity.Player;

/**
 * Flags horizontal movement faster than the player's theoretical maximum for
 * their current state (sprint, potion, jump burst), with latency tolerance.
 */
public final class SpeedCheck extends Check {

    public SpeedCheck(RXAC plugin) {
        super(plugin, "Speed", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()) return;
        if (data.inLiquid) return;                       // swimming uses different physics
        if (data.ticksSinceVelocity < 20) return;        // knockback in progress

        double speed = data.horizontalSpeed();
        double tolerance = cfgDouble("tolerance", 1.08);
        double base = MovementUtil.baseSprintSpeed(p);

        // Allow a higher burst right after leaving the ground (sprint-jump).
        double allowed = base * tolerance;
        if (data.groundTicks <= 2) allowed *= 1.85;      // jump-tick burst
        else if (data.airTicks > 0) allowed *= 1.35;     // in-air momentum decay

        if (speed > allowed) {
            double over = speed - allowed;
            fail(data, Math.min(3.0, 1 + over * 8),
                    String.format("speed=%.3f>%.3f", speed, allowed));
        } else {
            reward(data, 0.25);
        }
    }

    static boolean recentlyTeleported(PlayerData data) {
        if (System.currentTimeMillis() - data.lastTeleportMs < 1000) return true;
        data.teleporting = false;
        return false;
    }
}
