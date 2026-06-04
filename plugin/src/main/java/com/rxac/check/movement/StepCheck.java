package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Flags "Step" hacks that let a player rise more than the vanilla auto-step
 * height (0.6) in a single ground-to-ground transition without jumping.
 */
public final class StepCheck extends Check {

    public StepCheck(RXAC plugin) {
        super(plugin, "Step", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        Player p = data.getPlayer();
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()) return;
        if (p.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP) != null) return;

        double maxStep = cfgDouble("max-step", 0.63);

        // Ground-to-ground (or near-ground) instantaneous rise.
        boolean groundedNow = data.clientOnGround || data.nearGround;
        if (groundedNow && data.lastClientOnGround && data.deltaY > maxStep) {
            fail(data, Math.min(3.0, (data.deltaY - maxStep) * 6 + 1),
                    String.format("step dY=%.3f>%.2f", data.deltaY, maxStep));
        }
    }
}
