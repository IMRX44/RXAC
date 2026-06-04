package com.rxac.check.player;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Nuker / FastBreak: breaking many blocks per second, or breaking a block the
 * player is not looking anywhere near (Nuker breaks everything around it without
 * aiming). Survival mining is rate-limited and always faces the target.
 */
public final class NukerCheck extends Check {

    public NukerCheck(RXAC plugin) {
        super(plugin, "Nuker", CheckCategory.PLAYER);
    }

    @Override
    public void onBlockBreak(PlayerData data, Block block) {
        Player p = data.getPlayer();
        long now = System.currentTimeMillis();
        int perSecond = data.registerAndCountBreaks(now);

        int max = cfgInt("max-per-second", 8);
        if (perSecond > max) {
            fail(data, Math.min(3.0, (perSecond - max) * 0.5 + 1), "breaks/s=" + perSecond);
            return;
        }

        // Direction check: is the player actually facing the broken block?
        var eye = p.getEyeLocation();
        var look = eye.getDirection();
        var to = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        if (to.lengthSquared() > 1e-6) {
            double dot = look.normalize().dot(to.normalize());
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            double maxAngle = cfgDouble("max-angle", 75);
            if (angle > maxAngle) {
                fail(data, 1.5, String.format("notFacing angle=%.1f", angle));
            }
        }
    }
}
