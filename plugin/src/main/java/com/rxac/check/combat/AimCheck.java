package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Aim/aimbot detection via rotation analysis around the moment of attack:
 *  1) <b>Snap</b>: an abnormally large single-tick rotation immediately before
 *     a hit (the bot snapping onto the target).
 *  2) <b>GCD</b>: legitimate mouse input is quantized by sensitivity, so yaw
 *     deltas share a measurable greatest-common-divisor. Aimbots that write
 *     angles directly collapse that GCD toward zero.
 */
public final class AimCheck extends Check {

    public AimCheck(RXAC plugin) {
        super(plugin, "Aim", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        // (1) Snap: very large yaw change on the tick of the hit.
        if (data.deltaYaw > 30 && data.lastDeltaYaw() < 4) {
            fail(data, 1.5, String.format("snap dYaw=%.1f", data.deltaYaw));
        }

        // (2) GCD over recent non-zero yaw deltas.
        List<Float> samples = new ArrayList<>();
        for (Float f : data.getYawDeltaSamples()) {
            if (f > 0.05f && f < 30f) samples.add(f);
        }
        if (samples.size() >= 10) {
            double g = samples.get(0);
            for (int i = 1; i < samples.size(); i++) {
                g = MathUtil.gcd(g, samples.get(i));
            }
            // A healthy GCD is well above ~0.0007 (1/1440 of a sens step).
            if (g > 0 && g < 0.0008) {
                fail(data, 1.0, String.format("gcd=%.6f", g));
            } else {
                reward(data, 0.2);
            }
        }
    }
}
