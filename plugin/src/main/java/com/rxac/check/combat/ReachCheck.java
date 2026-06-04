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

        // Ping-aware slack: a lagging player's target may be slightly stale, so
        // we widen the allowance with latency (capped) to avoid false flags.
        int ping = safePing(data);
        double pingSlack = Math.min(cfgDouble("max-ping-slack", 0.5), ping * 0.0016);
        double allowed = max + pingSlack;

        double reach = ctx.getReach();
        if (reach > allowed) {
            fail(data, Math.min(3.0, 1 + (reach - allowed) * 4),
                    String.format("reach=%.3f>%.2f (ping=%d)", reach, allowed, ping));
        } else {
            reward(data, 0.5);
        }
    }

    private int safePing(PlayerData data) {
        try {
            return Math.max(0, data.getPlayer().getPing());
        } catch (Throwable t) {
            return 0;   // getPing() may be unavailable on some forks
        }
    }
}
