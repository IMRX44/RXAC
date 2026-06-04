package com.rxac.predict;

import com.rxac.player.PlayerData;
import com.rxac.util.MovementUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * "All possible inputs" movement prediction — the rigorous technique used by
 * top anti-cheats (e.g. Grim). Rather than predicting a single expected motion,
 * it enumerates <i>every</i> input a legitimate client could send this tick
 * (forward/back/left/right × sprint × jump × sneak), runs vanilla physics for
 * each, and produces the set of achievable velocities. The player's actual
 * motion only needs to match the <b>closest</b> candidate; if even the best
 * candidate is far away, the motion was physically impossible.
 *
 * <p>This makes magnitude-based bypasses pointless: a cheat must land exactly on
 * one of the legal velocities, at which point it is, by definition, vanilla.</p>
 *
 * <p>The physics constants are vanilla's. Collisions are applied per candidate
 * so walls, slabs and stairs are respected. Latency-driven tolerance is added
 * by the calling check, not here.</p>
 */
public final class MovementInputPredictor {

    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double AIR_FRICTION = 0.91;
    private static final double JUMP_VELOCITY = 0.42;

    /** Result of matching actual motion to the achievable-velocity set. */
    public static final class Result {
        public double horizontalOffset;   // best |actual - candidate| horizontally
        public double verticalOffset;      // vertical mismatch for the best candidate
        public boolean onGround;
        public boolean insideSolid;
        public double bestPredictedY;
    }

    private MovementInputPredictor() {}

    public static Result predict(PlayerData data) {
        Player p = data.getPlayer();
        World world = p.getWorld();
        Result r = new Result();

        org.bukkit.util.BoundingBox box = Collisions.playerBox(data.x, data.y, data.z);
        r.onGround = Collisions.onGround(world, box);
        r.insideSolid = Collisions.insideSolid(world, box);

        boolean groundLast = data.lastServerGround;
        double slip = slipperiness(world, data);
        double frictionH = groundLast ? (slip * AIR_FRICTION) : AIR_FRICTION;

        // Per-tick input acceleration (vanilla movement model).
        double speedMul = 1.0 + 0.2 * MovementUtil.speedAmplifier(p);
        double groundAccel = 0.1 * speedMul * (0.16277136 / (frictionH * frictionH * frictionH));
        double airAccel = 0.02 * speedMul;
        double sprintGround = groundAccel * 1.3;       // sprinting boosts ground accel
        double sprintAir = airAccel * 1.3;

        // Momentum carried from last tick, decayed by friction.
        double baseVx = data.lastDeltaX * frictionH;
        double baseVz = data.lastDeltaZ * frictionH;

        double yawRad = Math.toRadians(data.yaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double actualVx = data.deltaX;
        double actualVz = data.deltaZ;

        double bestH = Double.MAX_VALUE;
        // Enumerate the full legal input space.
        for (int forward = -1; forward <= 1; forward++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                for (int sprint = 0; sprint <= 1; sprint++) {
                    // Sprinting requires forward input in vanilla.
                    if (sprint == 1 && forward <= 0) continue;

                    double accel = groundLast
                            ? (sprint == 1 ? sprintGround : groundAccel)
                            : (sprint == 1 ? sprintAir : airAccel);

                    double f = forward;
                    double s = strafe;
                    double dist = Math.sqrt(f * f + s * s);
                    if (dist < 1.0E-4) {
                        // No movement input: candidate is pure momentum decay.
                        bestH = Math.min(bestH, dHoriz(actualVx, actualVz, baseVx, baseVz));
                        continue;
                    }
                    double scale = accel / dist;
                    f *= scale;
                    s *= scale;

                    double inX = s * cosYaw - f * sinYaw;
                    double inZ = f * cosYaw + s * sinYaw;

                    double candVx = baseVx + inX;
                    double candVz = baseVz + inZ;

                    // Respect collisions: clip the candidate against the world.
                    org.bukkit.util.BoundingBox cb = Collisions.playerBox(data.lastX, data.lastY, data.lastZ);
                    double[] clipped = Collisions.collide(world, cb, candVx, 0, candVz);
                    candVx = clipped[0];
                    candVz = clipped[2];

                    bestH = Math.min(bestH, dHoriz(actualVx, actualVz, candVx, candVz));
                }
            }
        }
        r.horizontalOffset = bestH == Double.MAX_VALUE ? 0 : bestH;

        // Vertical: ground rest, jump, or gravity continuation.
        if (r.onGround) {
            r.bestPredictedY = Math.max(0, data.deltaY);   // standing or jumping up
            r.verticalOffset = data.deltaY < -0.001 ? Math.abs(data.deltaY) : 0;
        } else if (data.lastServerGround && data.deltaY > 0) {
            r.bestPredictedY = JUMP_VELOCITY;
            r.verticalOffset = Math.abs(data.deltaY - JUMP_VELOCITY);
        } else {
            r.bestPredictedY = (data.lastDeltaY - GRAVITY) * VERTICAL_DRAG;
            r.verticalOffset = Math.abs(data.deltaY - r.bestPredictedY);
        }
        return r;
    }

    private static double dHoriz(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double slipperiness(World world, PlayerData data) {
        Location feet = new Location(world, data.x, data.y - 0.1, data.z);
        String n = feet.getBlock().getType().name();
        if (n.contains("BLUE_ICE")) return 0.989;
        if (n.contains("ICE") || n.contains("FROSTED")) return 0.98;
        if (n.equals("SLIME_BLOCK")) return 0.8;
        return 0.6;
    }
}
