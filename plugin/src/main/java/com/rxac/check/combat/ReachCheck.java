package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * Flags attacks that land beyond the maximum survival reach. The distance is
 * measured from the attacker's eye to the nearest point of the target's
 * bounding box (computed in {@link com.rxac.listener.CombatListener}), so it is
 * latency-tolerant but rejects the inflated reach of reach hacks.
 */
public final class ReachCheck extends Check {

    public ReachCheck(RXAC plugin) {
        super(plugin, "Reach", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        double max = cfgDouble("max-reach", 3.12);
        double reach = ctx.getReach();
        if (reach > max) {
            fail(data, Math.min(3.0, 1 + (reach - max) * 4),
                    String.format("reach=%.3f>%.2f", reach, max));
        } else {
            reward(data, 0.5);
        }
    }
}
