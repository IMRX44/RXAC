package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Flags a player whose body occupies a solid block (Phase / NoClip). We sample
 * the block at the player's torso and feet; standing inside a solid, full-cube
 * block is impossible under vanilla collision.
 */
public final class PhaseCheck extends Check {

    public PhaseCheck(RXAC plugin) {
        super(plugin, "Phase", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (data.getPlayer().getGameMode().name().equals("SPECTATOR")) return;

        Location feet = data.getPlayer().getLocation();
        Material at = feet.getBlock().getType();
        Material torso = feet.clone().add(0, 1, 0).getBlock().getType();

        if (isFullSolid(at) || isFullSolid(torso)) {
            fail(data, 1.0, "inside " + (isFullSolid(at) ? at : torso));
        }
    }

    private boolean isFullSolid(Material m) {
        if (!m.isSolid() || m.isAir()) return false;
        // Exclude blocks with non-full collision shapes that a player can stand
        // within (slabs, stairs, fences, etc.).
        String n = m.name();
        return !(n.contains("SLAB") || n.contains("STAIR") || n.contains("FENCE")
                || n.contains("WALL") || n.contains("GATE") || n.contains("DOOR")
                || n.contains("CARPET") || n.contains("SNOW") || n.contains("PANE")
                || n.contains("SIGN") || n.contains("BED") || n.contains("CHEST"));
    }
}
