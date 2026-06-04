package com.rxac.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Helpers for server-authoritative movement context (effects, environment). */
public final class MovementUtil {

    private MovementUtil() {}

    public static int speedAmplifier(Player p) {
        PotionEffect e = p.getPotionEffect(PotionEffectType.SPEED);
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    public static int jumpAmplifier(Player p) {
        PotionEffect e = p.getPotionEffect(PotionEffectType.JUMP);
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    public static boolean inLiquid(Player p) {
        Material m = p.getLocation().getBlock().getType();
        return m == Material.WATER || m == Material.LAVA
                || m.name().contains("WATER") || m.name().contains("BUBBLE");
    }

    /** True if there is a solid block directly below within {@code dist} blocks. */
    public static boolean nearGround(Player p, double dist) {
        var loc = p.getLocation();
        for (double dy = 0; dy <= dist; dy += 0.25) {
            Material below = loc.clone().subtract(0, dy + 0.01, 0).getBlock().getType();
            if (below.isSolid()) return true;
        }
        return false;
    }

    /**
     * Theoretical max ground speed (blocks/tick) for sprint-jumping, before
     * potion/latency tolerance. Used as the baseline for SpeedCheck.
     */
    public static double baseSprintSpeed(Player p) {
        // Vanilla sprint ~0.2806 b/t; sprint-jump bursts higher, so checks add
        // tolerance and account for the jump tick separately.
        double base = 0.2806;
        int speed = speedAmplifier(p);
        if (speed > 0) base *= (1.0 + 0.2 * speed);
        return base;
    }
}
