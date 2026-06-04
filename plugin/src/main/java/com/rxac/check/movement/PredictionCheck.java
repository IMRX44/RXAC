package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.predict.PredictionEngine;
import com.rxac.predict.PredictionResult;
import org.bukkit.entity.Player;

/**
 * The flagship movement check: instead of fixed thresholds it compares the
 * player's actual motion against the {@link PredictionEngine}'s physics
 * envelope. Three independent verdicts:
 *
 * <ul>
 *   <li><b>Vertical</b> — actual dY must match predicted free-fall/jump physics.</li>
 *   <li><b>Horizontal</b> — actual speed must not exceed the friction+input bound
 *       (catches Speed, Bhop, Strafe regardless of magnitude tuning).</li>
 *   <li><b>Clip</b> — the body must never be inside a solid block (Phase/NoClip).</li>
 * </ul>
 *
 * Verdicts are buffered through the decaying violation level, so a single noisy
 * tick never punishes — only sustained, physically impossible motion does. When
 * confident, it triggers a setback (rubber-band) if enabled.
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

        PredictionResult r = PredictionEngine.predict(data);

        double vTol = cfgDouble("vertical-tolerance", 0.06);
        double hTol = cfgDouble("horizontal-tolerance", 1.10);
        boolean confident = false;
        String reason = null;

        // (1) Clip: physically inside a solid block.
        if (r.insideSolid) {
            fail(data, 2.0, "clip/phase");
            confident = true;
            reason = "phase";
        }

        // (2) Vertical physics (skip the jump tick and ground contact).
        if (!r.onGround && !r.jumpTick && data.airTicks > 1 && data.lastDeltaY < 0) {
            double err = Math.abs(data.deltaY - r.predictedVelY);
            if (err > vTol) {
                double amt = Math.min(3.0, err * 10);
                fail(data, amt, String.format("vY dY=%.4f pred=%.4f err=%.4f",
                        data.deltaY, r.predictedVelY, err));
                if (err > vTol * 4) { confident = true; reason = "vertical"; }
            } else {
                reward(data, 0.4);
            }
        }

        // (3) Horizontal physics envelope.
        double actualH = data.horizontalSpeed();
        double allowedH = r.maxHorizontal * hTol + 0.02;   // small constant slack
        if (actualH > allowedH) {
            double over = actualH - allowedH;
            double amt = Math.min(3.0, 1 + over * 12);
            fail(data, amt, String.format("hSpeed=%.4f>%.4f", actualH, allowedH));
            if (over > 0.08) { confident = true; reason = "horizontal"; }
        } else {
            reward(data, 0.3);
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
