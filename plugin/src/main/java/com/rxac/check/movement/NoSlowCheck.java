package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * NoSlow detection. While a player is using an item (eating, drinking, drawing a
 * bow, blocking) vanilla slows movement to ~20% of walking speed. NoSlow hacks
 * remove that penalty, so moving fast with the hand raised is a strong signal.
 */
public final class NoSlowCheck extends Check {

    public NoSlowCheck(RXAC plugin) {
        super(plugin, "NoSlow", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (!p.isHandRaised()) { return; }                 // not using an item
        if (p.isInsideVehicle() || data.inLiquid) return;
        if (p.isFlying() || p.getAllowFlight()) return;
        if (data.ticksSinceVelocity < 20) return;

        // Slowed walking is ~0.057 b/t; allow generous slack for speed pots/jumps.
        double cap = cfgDouble("max-use-speed", 0.16);
        double speed = data.horizontalSpeed();
        if (speed > cap) {
            fail(data, Math.min(3.0, (speed - cap) * 12 + 1),
                    String.format("useSpeed=%.3f>%.2f", speed, cap));
        }
    }
}
