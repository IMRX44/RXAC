package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * Anti-knockback ("Velocity") detection. When the server applies knockback we
 * record the expected velocity ({@link #onVelocity}); over the next few ticks we
 * measure how much of it the player actually absorbed ({@link #onMovement}).
 * Taking far less knockback than expected is the signature of velocity hacks.
 *
 * <p>Lives in the MOVEMENT category so it receives both the velocity event and
 * subsequent movement packets.</p>
 */
public final class VelocityCheck extends Check {

    public VelocityCheck(RXAC plugin) {
        super(plugin, "Velocity", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        // Measure on the first couple of ticks after knockback is applied.
        if (data.ticksSinceVelocity < 1 || data.ticksSinceVelocity > 2) return;
        if (SpeedCheck.recentlyTeleported(data)) return;

        double minH = cfgDouble("min-horizontal", 0.5);
        double minV = cfgDouble("min-vertical", 0.5);

        double expectH = Math.hypot(data.expectVelX, data.expectVelZ);
        double actualH = Math.hypot(data.deltaX, data.deltaZ);

        if (expectH > 0.05) {
            double ratioH = actualH / expectH;
            if (ratioH < minH) {
                fail(data, Math.min(3.0, (minH - ratioH) * 4 + 1),
                        String.format("kbH=%.0f%% (%.3f/%.3f)", ratioH * 100, actualH, expectH));
                return;
            }
        }

        if (data.expectVelY > 0.05) {
            double ratioV = data.deltaY / data.expectVelY;
            if (ratioV < minV) {
                fail(data, 1.0, String.format("kbV=%.0f%%", ratioV * 100));
            }
        }
    }
}
