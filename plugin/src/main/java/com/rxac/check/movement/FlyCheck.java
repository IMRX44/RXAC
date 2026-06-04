package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.predict.PredictionEngine;
import com.rxac.predict.PredictionResult;
import org.bukkit.entity.Player;

/**
 * Dedicated Fly detection on top of the prediction engine. Three impossibilities
 * are flagged, all using server-side collision ground (never the client's claim):
 *
 * <ul>
 *   <li><b>Hover</b> — airborne for many ticks with no meaningful descent.</li>
 *   <li><b>Ascend</b> — gaining height in mid-air without a jump/boost source.</li>
 *   <li><b>Gravity</b> — vertical velocity that ignores gravity over time.</li>
 * </ul>
 *
 * High-confidence hovering/ascending triggers a setback when enabled.
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
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle() || p.isGliding()) return;
        if (data.inLiquid) { reward(data, 1.0); return; }
        if (data.ticksSinceVelocity < 20) return;
        if (hasVerticalEffect(p)) return;

        PredictionResult r = PredictionEngine.predict(data);
        if (r.onGround || data.serverGround) { reward(data, 1.0); return; }

        int maxAir = cfgInt("max-air-ticks", 80);
        boolean confident = false;
        String reason = null;

        // Hover: long airborne with negligible descent.
        if (data.airTicks > maxAir && data.deltaY > -0.05) {
            fail(data, 2.0, String.format("hover air=%d dY=%.4f", data.airTicks, data.deltaY));
            confident = true; reason = "hover";
        }
        // Ascend: climbing in mid-air with no source.
        else if (data.airTicks > 6 && data.deltaY > 0 && data.lastDeltaY > 0
                && data.deltaY >= data.lastDeltaY - 0.001) {
            fail(data, 1.5, String.format("ascend dY=%.4f", data.deltaY));
            confident = true; reason = "ascend";
        }
        // Gravity: free-fall velocity that doesn't match predicted decay.
        else if (data.airTicks > 2 && data.lastDeltaY < 0) {
            double err = Math.abs(data.deltaY - r.predictedVelY);
            if (err > cfgDouble("gravity-tolerance", 0.07)) {
                fail(data, Math.min(2.0, err * 8), String.format("gravity err=%.4f", err));
            } else {
                reward(data, 0.3);
            }
        }

        if (confident) {
            plugin.getSetbackManager().setback(data, "Fly/" + reason);
        }
    }

    private boolean hasVerticalEffect(Player p) {
        return p.getPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING) != null;
    }
}
