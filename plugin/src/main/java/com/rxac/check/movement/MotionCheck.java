package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Validates vertical motion against Minecraft's gravity model. In free-fall the
 * client's predicted next vertical velocity is {@code (v - 0.08) * 0.98}.
 * Persistent deviation (without a known cause) indicates a motion/glide/step
 * modification.
 */
public final class MotionCheck extends Check {

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    public MotionCheck(RXAC plugin) {
        super(plugin, "Motion", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()) return;
        if (data.inLiquid || data.nearGround || data.clientOnGround) return;
        if (data.ticksSinceVelocity < 20) return;
        if (p.isGliding()) return;
        if (p.getPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING) != null) return;

        // Only evaluate when clearly in free-fall (descending for 2+ ticks).
        if (data.airTicks < 2 || data.lastDeltaY >= 0) { reward(data, 0.2); return; }

        double predicted = (data.lastDeltaY - GRAVITY) * DRAG;
        double error = Math.abs(data.deltaY - predicted);

        // ~0.01 covers float noise; anything well above is a real anomaly.
        if (error > 0.05) {
            fail(data, Math.min(3.0, error * 10),
                    String.format("dY=%.4f pred=%.4f err=%.4f", data.deltaY, predicted, error));
        } else {
            reward(data, 0.3);
        }
    }
}
