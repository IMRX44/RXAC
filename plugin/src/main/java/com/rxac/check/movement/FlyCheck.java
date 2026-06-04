package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Flags sustained airborne hovering: many ticks off the ground while vertical
 * motion stays non-negative (a falling player accelerates downward; a flyer
 * hovers or climbs).
 */
public final class FlyCheck extends Check {

    public FlyCheck(RXAC plugin) {
        super(plugin, "Fly", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()) return;
        if (data.inLiquid || data.nearGround) { reward(data, 1.0); return; }
        if (hasLevitation(p) || p.isGliding()) return;
        if (data.ticksSinceVelocity < 20) return;

        int maxAir = cfgInt("max-air-ticks", 80);

        // Hovering: airborne a long time but not descending.
        if (data.airTicks > maxAir && data.deltaY >= -0.005) {
            fail(data, 2.0, String.format("air=%d dY=%.4f", data.airTicks, data.deltaY));
        }
        // Ascending in mid-air without a jump source.
        else if (data.airTicks > 6 && data.deltaY > 0 && data.lastDeltaY > 0
                && data.deltaY >= data.lastDeltaY) {
            fail(data, 1.0, String.format("ascend dY=%.4f", data.deltaY));
        } else {
            reward(data, 0.2);
        }
    }

    private boolean hasLevitation(Player p) {
        return p.getPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING) != null;
    }
}
