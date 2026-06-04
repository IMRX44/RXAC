package com.rxac.listener;

import com.rxac.RXAC;
import com.rxac.check.AttackContext;
import com.rxac.player.PlayerData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Combat input: attack events (for reach / killaura / aim / hitbox) and
 * velocity events (for the anti-knockback VelocityCheck).
 */
public final class CombatListener implements Listener {

    private final RXAC plugin;

    public CombatListener(RXAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        long now = System.currentTimeMillis();
        double reach = computeReach(player, event.getEntity());

        data.lastAttackMs = now;
        data.lastTargetId = event.getEntity().getEntityId();
        data.registerTarget(event.getEntity().getEntityId(), now);

        plugin.getCheckManager().dispatchAttack(data,
                new AttackContext(event.getEntity(), reach, now));
    }

    /** Distance from the attacker's eye to the nearest point of the target box. */
    private double computeReach(Player attacker, org.bukkit.entity.Entity target) {
        Vector eye = attacker.getEyeLocation().toVector();
        BoundingBox box = target.getBoundingBox();
        double cx = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double cy = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double cz = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return eye.distance(new Vector(cx, cy, cz));
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer());
        if (data == null) return;
        Vector v = event.getVelocity();
        data.expectVelX = v.getX();
        data.expectVelY = v.getY();
        data.expectVelZ = v.getZ();
        data.ticksSinceVelocity = 0;
        plugin.getCheckManager().dispatchVelocity(data);
    }
}
