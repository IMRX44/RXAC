package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * FastBow detection. A fully charged bow (force ≈ 1.0) requires ~1 second of
 * draw time in vanilla. A shot that leaves the bow at high force after an
 * impossibly short draw is the signature of a FastBow / auto-bow hack.
 */
public final class FastBowCheck extends Check {

    public FastBowCheck(RXAC plugin) {
        super(plugin, "FastBow", CheckCategory.COMBAT);
    }

    @Override
    public void onBowShoot(PlayerData data, float force, long drawMs) {
        // Map force back to the minimum legal draw time. Vanilla: a 20-tick
        // (1000 ms) draw reaches force 1.0; force scales with sqrt of charge.
        double minDrawMs = force * force * 1000.0;
        double allowed = minDrawMs * 0.85;   // 15% slack for latency/timing.

        if (force > 0.25 && drawMs < allowed) {
            fail(data, Math.min(3.0, (allowed - drawMs) / 100.0 + 1),
                    String.format("force=%.2f draw=%dms<%.0f", force, drawMs, allowed));
        }
    }
}
