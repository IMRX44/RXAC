package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.predict.MovementInputPredictor;
import org.bukkit.entity.Player;

/**
 * Flagship movement check, powered by {@link MovementInputPredictor}'s
 * "all possible inputs" engine. The player's actual motion is compared to the
 * closest physically achievable velocity; the residual offset — not a fixed
 * speed threshold — drives detection, so Speed/Bhop/Strafe/Glide all reduce to
 * the same question: "could vanilla physics produce this motion?".
 *
 * <p>Tolerances scale with measured transaction latency, so high-ping players
 * are not punished for legitimate jitter. Violations are buffered through the
 * decaying VL; high-confidence impossibilities trigger a setback.</p>
 */
public final class PredictionCheck extends Check {

    public PredictionCheck(RXAC plugin) {
        super(plugin, "Prediction", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle() || p.isGliding()) return;
        if (data.inLiquid) return;
        if (data.ticksSinceVelocity < 20) return;
        if (hasMovementEffect(p)) return;

        MovementInputPredictor.Result r = MovementInputPredictor.predict(data);

        // Latency-scaled tolerance: 1 cm baseline + a little per 50 ms of ping.
        double ping = data.latencyMs();
        double lat = Math.min(0.08, ping / 50.0 * 0.012);
        double hTol = cfgDouble("horizontal-tolerance", 0.06) + lat;
        double vTol = cfgDouble("vertical-tolerance", 0.06) + lat * 0.5;

        boolean confident = false;
        String reason = null;

        // (1) Clip / Phase — body intersecting a solid block.
        if (r.insideSolid) {
            fail(data, 2.0, "clip/phase");
            confident = true; reason = "phase";
        }

        // (2) Horizontal — no legal input set can explain the motion.
        if (r.horizontalOffset > hTol) {
            double over = r.horizontalOffset - hTol;
            fail(data, Math.min(3.0, 1 + over * 14),
                    String.format("hOff=%.4f>%.4f", r.horizontalOffset, hTol));
            if (over > 0.08) { confident = true; reason = "horizontal"; }
        } else {
            reward(data, 0.3);
        }

        // (3) Vertical — gravity/jump physics mismatch (skip ground & jump tick).
        if (!r.onGround && !(data.lastServerGround && data.deltaY > 0)
                && data.airTicks > 1 && data.lastDeltaY < 0) {
            if (r.verticalOffset > vTol) {
                fail(data, Math.min(3.0, r.verticalOffset * 10),
                        String.format("vOff=%.4f>%.4f", r.verticalOffset, vTol));
                if (r.verticalOffset > vTol * 4) { confident = true; reason = "vertical"; }
            } else {
                reward(data, 0.3);
            }
        }

        if (confident) {
            plugin.getSetbackManager().setback(data, "Prediction/" + reason);
        }
    }

    private boolean hasMovementEffect(Player p) {
        return p.getPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP) != null
                || p.getPotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE) != null;
    }
}
