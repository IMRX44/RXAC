package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Spider detection: climbing a solid wall like a ladder. A player who is rising
 * in mid-air while pressed against a vertical solid surface — with no ladder,
 * vine, scaffolding or jump to explain it — is using Spider/Climb.
 */
public final class SpiderCheck extends Check {

    public SpiderCheck(RXAC plugin) {
        super(plugin, "Spider", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle() || p.isGliding()) return;
        if (data.inLiquid || data.serverGround || data.nearGround) return;
        if (onClimbable(p)) { reward(data, 0.5); return; }

        // Rising through the air, sustained, while touching a wall.
        boolean rising = data.deltaY > 0.05 && data.lastDeltaY > 0.0;
        if (rising && data.airTicks > 1 && touchingWall(p)) {
            fail(data, 1.5, String.format("climb dY=%.3f air=%d", data.deltaY, data.airTicks));
        }
    }

    private boolean onClimbable(Player p) {
        String n = p.getLocation().getBlock().getType().name();
        return n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING")
                || n.contains("TWISTING") || n.contains("WEEPING") || n.contains("WEB");
    }

    private boolean touchingWall(Player p) {
        Location l = p.getLocation();
        double[][] around = {{0.32, 0, 0}, {-0.32, 0, 0}, {0, 0, 0.32}, {0, 0, -0.32}};
        for (double dy = 0.2; dy <= 1.6; dy += 0.7) {
            for (double[] off : around) {
                Material m = l.clone().add(off[0], dy, off[2]).getBlock().getType();
                if (m.isSolid() && !m.isAir()) return true;
            }
        }
        return false;
    }
}
