package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * KillAura detection via two independent signals:
 *  1) <b>Multi-aura</b>: hitting two or more distinct entities within a tiny
 *     window — a human cannot retarget that fast.
 *  2) <b>FOV</b>: landing a melee hit on a target that is well outside the
 *     player's view cone (you cannot legitimately hit what you are not facing).
 */
public final class KillAuraCheck extends Check {

    public KillAuraCheck(RXAC plugin) {
        super(plugin, "KillAura", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        long window = (long) cfgDouble("multi-target-window-ms", 250);
        long now = ctx.getTime();

        // (1) Multi-aura: distinct targets within the window.
        int distinct = data.distinctTargetsWithin(window, now);
        if (distinct >= 2) {
            fail(data, 3.0, "multi-target=" + distinct);
        }

        // (2) FOV: angle between look direction and direction to target.
        Player p = data.getPlayer();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection();
        Vector to = ctx.getTarget().getLocation().add(0, 1, 0).toVector()
                .subtract(eye.toVector());
        if (to.lengthSquared() > 1e-6) {
            double dot = look.normalize().dot(to.normalize());
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            if (angle > 80) {
                fail(data, Math.min(3.0, (angle - 80) / 20 + 1),
                        String.format("fov=%.1f", angle));
            } else {
                reward(data, 0.4);
            }
        }
    }
}
