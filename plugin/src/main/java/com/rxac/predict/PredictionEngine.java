package com.rxac.predict;

import com.rxac.player.PlayerData;
import com.rxac.util.MovementUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Tick-by-tick movement predictor. Given a player's previous velocity and their
 * current environment (ground, block friction, potions), it computes the legal
 * motion envelope for this tick: the expected vertical velocity and the maximum
 * horizontal speed achievable with any input.
 *
 * <p>This replaces brittle fixed thresholds with vanilla physics, which is what
 * makes movement detections hard to bypass: a cheat must reproduce the physics
 * exactly to stay inside the envelope, at which point it is no longer cheating.</p>
 *
 * <p>The constants are vanilla's; the small additive tolerances are absorbed by
 * the calling check, not baked in here.</p>
 */
public final class PredictionEngine {

    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double AIR_FRICTION = 0.91;
    private static final double DEFAULT_SLIP = 0.6;     // generic ground slipperiness
    private static final double JUMP_VELOCITY = 0.42;

    private PredictionEngine() {}

    public static PredictionResult predict(PlayerData data) {
        Player p = data.getPlayer();
        World world = p.getWorld();
        PredictionResult r = new PredictionResult();

        BoundingBox box = Collisions.playerBox(data.x, data.y, data.z);
        r.onGround = Collisions.onGround(world, box);
        r.insideSolid = Collisions.insideSolid(world, box);
        r.jumpTick = data.lastServerGround && !r.onGround && data.deltaY > 0;

        // Vertical: continuation of free-fall. On the ground vertical velocity
        // resets; on a jump tick the model expects ~+0.42 before drag.
        if (r.onGround) {
            r.predictedVelY = 0;
        } else if (r.jumpTick) {
            r.predictedVelY = JUMP_VELOCITY;
        } else {
            r.predictedVelY = (data.lastDeltaY - GRAVITY) * VERTICAL_DRAG;
        }

        // Horizontal: momentum * friction + per-tick input acceleration.
        double slip = slipperiness(world, data);
        double frictionH = data.lastServerGround ? (AIR_FRICTION * slip) : AIR_FRICTION;
        double lastH = Math.hypot(data.lastDeltaX, data.lastDeltaZ);

        // Approximate sprint input acceleration; scaled by ground friction and
        // any Speed potion. Air control is much weaker than ground control.
        double speedMul = 1.0 + 0.2 * MovementUtil.speedAmplifier(p);
        double groundAccel = 0.1 * speedMul * (0.16277136 / (frictionH * frictionH * frictionH));
        double airAccel = 0.02 * speedMul;
        double accel = data.lastServerGround ? Math.max(groundAccel, 0.13 * speedMul) : airAccel;

        r.maxHorizontal = lastH * frictionH + accel;
        if (r.jumpTick) r.maxHorizontal += 0.2 * speedMul;   // sprint-jump burst

        return r;
    }

    private static double slipperiness(World world, PlayerData data) {
        Location feet = new Location(world, data.x, data.y - 0.1, data.z);
        Material below = feet.getBlock().getType();
        String n = below.name();
        if (n.contains("BLUE_ICE")) return 0.989;
        if (n.contains("ICE") || n.contains("FROSTED")) return 0.98;
        if (n.equals("SLIME_BLOCK")) return 0.8;
        return DEFAULT_SLIP;
    }
}
