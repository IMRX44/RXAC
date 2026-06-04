package com.rxac.check;

import org.bukkit.entity.Entity;

/** Snapshot of a single attack, passed to combat checks. */
public final class AttackContext {

    private final Entity target;
    private final double reach;        // eye-to-hitbox distance, pre-computed
    private final long time;           // System.currentTimeMillis()

    public AttackContext(Entity target, double reach, long time) {
        this.target = target;
        this.reach = reach;
        this.time = time;
    }

    public Entity getTarget() { return target; }
    public double getReach() { return reach; }
    public long getTime() { return time; }
}
