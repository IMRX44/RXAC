package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * HitBox detection: the player's look ray must actually intersect the target's
 * real bounding box (with a small latency margin). Hits that only connect with
 * an artificially enlarged box — but miss the true one — are the signature of
 * a hitbox expander / aim-assist.
 */
public final class HitBoxCheck extends Check {

    public HitBoxCheck(RXAC plugin) {
        super(plugin, "HitBox", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        Player p = data.getPlayer();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        BoundingBox real = ctx.getTarget().getBoundingBox();
        // Small expansion absorbs ~1 tick of target movement / latency.
        BoundingBox lenient = real.clone().expand(0.1);
        double maxReach = cfgDouble("max-reach", 3.5);

        RayTraceResult hit = lenient.rayTrace(eye.toVector(), dir, maxReach);
        if (hit == null) {
            // Look ray does not touch the target at all, yet a hit registered.
            fail(data, 2.0, "ray-miss");
        } else {
            reward(data, 0.4);
        }
    }
}
