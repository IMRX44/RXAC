package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Multi-module KillAura detection. A legitimate melee hit must satisfy ALL of:
 * the target is in front of you, you can see it, you swung your arm, and your
 * aim arrived smoothly. KillAura clients break at least one of these, so we run
 * several independent modules and accumulate violation level across them.
 *
 * <ul>
 *   <li><b>MultiAura</b> — distinct targets struck within a tiny window.</li>
 *   <li><b>FOV</b> — hit landed outside the player's view cone.</li>
 *   <li><b>Wall</b> — line of sight to the target is blocked by solid blocks.</li>
 *   <li><b>NoSwing</b> — no arm-swing packet preceded the hit.</li>
 *   <li><b>Snap</b> — an instantaneous large rotation onto the target.</li>
 * </ul>
 */
public final class KillAuraCheck extends Check {

    public KillAuraCheck(RXAC plugin) {
        super(plugin, "KillAura", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerData data, AttackContext ctx) {
        Player p = data.getPlayer();
        long window = (long) cfgDouble("multi-target-window-ms", 250);
        long now = ctx.getTime();

        // (1) MultiAura
        int distinct = data.distinctTargetsWithin(window, now);
        if (distinct >= 2) {
            fail(data, 3.0, "multi-target=" + distinct);
        }

        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection();
        Location targetCenter = ctx.getTarget().getLocation().add(0, 1, 0);
        Vector to = targetCenter.toVector().subtract(eye.toVector());
        double dist = to.length();
        if (dist < 1e-4) return;

        // (2) FOV
        double dot = look.clone().normalize().dot(to.clone().normalize());
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
        if (angle > cfgDouble("max-fov", 75)) {
            fail(data, Math.min(3.0, (angle - 75) / 20 + 1), String.format("fov=%.1f", angle));
        }

        // (3) Wall — is line of sight to the target blocked by solid blocks?
        if (cfgBoolean("wall-check", true) && dist <= 6) {
            RayTraceResult hit = p.getWorld().rayTraceBlocks(
                    eye, look, dist, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) {
                double blockDist = eye.toVector().distance(hit.getHitPosition());
                if (blockDist < dist - 0.4) {
                    fail(data, 2.0, String.format("through-wall (block@%.2f, tgt@%.2f)",
                            blockDist, dist));
                }
            }
        }

        // (4) NoSwing — auras that don't animate the arm before hitting.
        long sinceSwing = now - data.lastSwingMs;
        if (cfgBoolean("swing-check", true) && sinceSwing > cfgDouble("max-swing-gap-ms", 400)) {
            fail(data, 1.0, "no-swing(" + sinceSwing + "ms)");
        }

        // (5) Snap — instantaneous large rotation onto the target.
        if (data.deltaYaw > 30 && data.lastDeltaYaw() < 4) {
            fail(data, 1.5, String.format("snap dYaw=%.1f", data.deltaYaw));
        }

        if (distinct < 2 && angle <= cfgDouble("max-fov", 75)) reward(data, 0.4);
    }

    private boolean cfgBoolean(String key, boolean def) {
        var s = config();
        return s == null ? def : s.getBoolean(key, def);
    }
}
