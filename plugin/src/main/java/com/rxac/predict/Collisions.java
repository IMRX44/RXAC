package com.rxac.predict;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative AABB collision, mirroring vanilla's axis-by-axis sweep
 * (Y, then X, then Z). This is the foundation of the prediction engine: it lets
 * RXAC compute where a player *could* legally move, independent of what the
 * client claims, so movement cheats cannot simply stay "under a threshold".
 *
 * <p>Block collision shapes are taken from {@link Block#getBoundingBox()} and
 * gated by {@link Block#isPassable()}, so slabs, stairs and other partial blocks
 * are handled with reasonable fidelity.</p>
 */
public final class Collisions {

    public static final double WIDTH = 0.6;
    public static final double HEIGHT = 1.8;

    private Collisions() {}

    /** Build a player AABB from a feet position. */
    public static BoundingBox playerBox(double x, double y, double z) {
        double h = WIDTH / 2.0;
        return new BoundingBox(x - h, y, z - h, x + h, y + HEIGHT, z + h);
    }

    /**
     * Clip a desired motion against the world so the box never penetrates a
     * solid. Returns the adjusted {dx, dy, dz}. The passed box is shifted in
     * place to the resulting position.
     */
    public static double[] collide(World world, BoundingBox box,
                                   double dx, double dy, double dz) {
        // Expand the search region to cover the full swept motion.
        BoundingBox sweep = box.clone();
        sweep.resize(
                Math.min(box.getMinX(), box.getMinX() + dx),
                Math.min(box.getMinY(), box.getMinY() + dy),
                Math.min(box.getMinZ(), box.getMinZ() + dz),
                Math.max(box.getMaxX(), box.getMaxX() + dx),
                Math.max(box.getMaxY(), box.getMaxY() + dy),
                Math.max(box.getMaxZ(), box.getMaxZ() + dz));

        List<BoundingBox> solids = nearbySolids(world, sweep);

        double ndy = dy;
        for (BoundingBox b : solids) ndy = clipY(b, box, ndy);
        box.shift(0, ndy, 0);

        double ndx = dx;
        for (BoundingBox b : solids) ndx = clipX(b, box, ndx);
        box.shift(ndx, 0, 0);

        double ndz = dz;
        for (BoundingBox b : solids) ndz = clipZ(b, box, ndz);
        box.shift(0, 0, ndz);

        return new double[]{ndx, ndy, ndz};
    }

    /** True if a solid surface is immediately beneath the box. */
    public static boolean onGround(World world, BoundingBox box) {
        BoundingBox probe = box.clone();
        double moved = collide(world, probe, 0, -0.02, 0)[1];
        return moved > -0.02 + 1.0E-4;   // motion was clipped by ground
    }

    /** Does this box currently intersect any solid block (Phase / NoClip)? */
    public static boolean insideSolid(World world, BoundingBox box) {
        for (BoundingBox b : nearbySolids(world, box)) {
            if (box.overlaps(b)) return true;
        }
        return false;
    }

    private static List<BoundingBox> nearbySolids(World world, BoundingBox region) {
        List<BoundingBox> out = new ArrayList<>();
        int minX = (int) Math.floor(region.getMinX()) - 1;
        int maxX = (int) Math.floor(region.getMaxX()) + 1;
        int minY = (int) Math.floor(region.getMinY()) - 1;
        int maxY = (int) Math.floor(region.getMaxY()) + 1;
        int minZ = (int) Math.floor(region.getMinZ()) - 1;
        int maxZ = (int) Math.floor(region.getMaxZ()) + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable() || block.isLiquid()) continue;
                    out.add(block.getBoundingBox());
                }
            }
        }
        return out;
    }

    // --- axis clip helpers (classic vanilla calculateOffset) -----------------

    private static double clipY(BoundingBox o, BoundingBox box, double dy) {
        if (box.getMaxX() > o.getMinX() && box.getMinX() < o.getMaxX()
                && box.getMaxZ() > o.getMinZ() && box.getMinZ() < o.getMaxZ()) {
            if (dy > 0 && box.getMaxY() <= o.getMinY()) {
                double d = o.getMinY() - box.getMaxY();
                if (d < dy) dy = d;
            } else if (dy < 0 && box.getMinY() >= o.getMaxY()) {
                double d = o.getMaxY() - box.getMinY();
                if (d > dy) dy = d;
            }
        }
        return dy;
    }

    private static double clipX(BoundingBox o, BoundingBox box, double dx) {
        if (box.getMaxY() > o.getMinY() && box.getMinY() < o.getMaxY()
                && box.getMaxZ() > o.getMinZ() && box.getMinZ() < o.getMaxZ()) {
            if (dx > 0 && box.getMaxX() <= o.getMinX()) {
                double d = o.getMinX() - box.getMaxX();
                if (d < dx) dx = d;
            } else if (dx < 0 && box.getMinX() >= o.getMaxX()) {
                double d = o.getMaxX() - box.getMinX();
                if (d > dx) dx = d;
            }
        }
        return dx;
    }

    private static double clipZ(BoundingBox o, BoundingBox box, double dz) {
        if (box.getMaxX() > o.getMinX() && box.getMinX() < o.getMaxX()
                && box.getMaxY() > o.getMinY() && box.getMinY() < o.getMaxY()) {
            if (dz > 0 && box.getMaxZ() <= o.getMinZ()) {
                double d = o.getMinZ() - box.getMaxZ();
                if (d < dz) dz = d;
            } else if (dz < 0 && box.getMinZ() >= o.getMaxZ()) {
                double d = o.getMaxZ() - box.getMinZ();
                if (d > dz) dz = d;
            }
        }
        return dz;
    }
}
