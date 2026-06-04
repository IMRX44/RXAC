package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Detects water-walking ("Jesus"): the player stays at the water surface with
 * near-zero vertical motion while standing on liquid, instead of sinking.
 */
public final class JesusCheck extends Check {

    public JesusCheck(RXAC plugin) {
        super(plugin, "Jesus", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()) return;

        Material below = p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();
        Material feet = p.getLocation().getBlock().getType();

        boolean onLiquidSurface = isLiquid(below) && !feet.isSolid() && !isLiquid(feet);
        if (!onLiquidSurface) { reward(data, 0.3); return; }

        // On a liquid surface a legitimate player either sinks (dY<0) or bobs;
        // walking flat across the top (|dY|~0 while moving horizontally) is the
        // signature of Jesus.
        if (Math.abs(data.deltaY) < 0.005 && data.horizontalSpeed() > 0.1) {
            fail(data, 1.5, String.format("waterwalk hSpeed=%.3f", data.horizontalSpeed()));
        }
    }

    private boolean isLiquid(Material m) {
        return m == Material.WATER || m == Material.LAVA
                || m.name().contains("WATER") || m.name().contains("LAVA");
    }
}
