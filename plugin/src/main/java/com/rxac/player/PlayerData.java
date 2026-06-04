package com.rxac.player;

import com.rxac.punish.ViolationTracker;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Per-player rolling state. Updated from packets/events and read by checks.
 * Kept allocation-light because it is touched on the netty thread for every
 * movement packet.
 */
public final class PlayerData {

    private final UUID uuid;
    private final Player player;
    private final ViolationTracker violations;

    // Client protocol (via ViaVersion if present); -1 == server-native.
    private int protocolVersion = -1;

    // --- Position / motion ----------------------------------------------------
    public double x, y, z;
    public double lastX, lastY, lastZ;
    public double deltaX, deltaY, deltaZ;
    public double lastDeltaX, lastDeltaY, lastDeltaZ;
    public boolean hasPosition;

    // --- Rotation -------------------------------------------------------------
    public float yaw, pitch;
    public float lastYaw, lastPitch;
    public float deltaYaw, deltaPitch;
    private float prevDeltaYaw;

    // --- Ground / air state ---------------------------------------------------
    public boolean clientOnGround;
    public boolean lastClientOnGround;
    public int airTicks;
    public int groundTicks;
    public boolean inLiquid;
    public boolean nearGround;     // server-side bounding-box ground check

    // --- Timing ---------------------------------------------------------------
    public long lastMovementMs;
    private final Deque<Long> flyingPacketTimes = new ArrayDeque<>();  // for TimerCheck
    private final Deque<Long> clickTimes = new ArrayDeque<>();          // for CPS

    // --- Combat ---------------------------------------------------------------
    public long lastAttackMs;
    public long lastBlockPlaceMs;
    public long bowDrawStartMs;
    public int lastTargetId = -1;
    private final Deque<long[]> recentTargets = new ArrayDeque<>();      // {entityId, timeMs}

    // --- Velocity / knockback -------------------------------------------------
    public double expectVelX, expectVelY, expectVelZ;
    public int velocityTick = -1;          // tick the knockback was applied
    public int ticksSinceVelocity = Integer.MAX_VALUE;

    // --- Aim (GCD) ------------------------------------------------------------
    private final Deque<Float> yawDeltaSamples = new ArrayDeque<>();
    private final Deque<Float> pitchDeltaSamples = new ArrayDeque<>();

    // --- Lag / transaction ----------------------------------------------------
    public boolean teleporting;
    public long lastTeleportMs;

    public PlayerData(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.violations = new ViolationTracker(this);
    }

    /** Update positional state from a movement packet that carried coordinates. */
    public void updatePosition(double nx, double ny, double nz) {
        this.lastX = x; this.lastY = y; this.lastZ = z;
        this.x = nx; this.y = ny; this.z = nz;
        if (hasPosition) {
            this.lastDeltaX = deltaX; this.lastDeltaY = deltaY; this.lastDeltaZ = deltaZ;
            this.deltaX = x - lastX;
            this.deltaY = y - lastY;
            this.deltaZ = z - lastZ;
        }
        this.hasPosition = true;
    }

    /** Update rotation from a movement packet that carried yaw/pitch. */
    public void updateRotation(float nyaw, float npitch) {
        this.lastYaw = yaw; this.lastPitch = pitch;
        this.prevDeltaYaw = this.deltaYaw;
        this.yaw = nyaw; this.pitch = npitch;
        this.deltaYaw = Math.abs(wrap(yaw - lastYaw));
        this.deltaPitch = Math.abs(pitch - lastPitch);
        pushSample(yawDeltaSamples, deltaYaw, 40);
        pushSample(pitchDeltaSamples, deltaPitch, 40);
    }

    public void markFlyingPacket(long now, long windowMs) {
        flyingPacketTimes.addLast(now);
        while (!flyingPacketTimes.isEmpty() && now - flyingPacketTimes.peekFirst() > windowMs) {
            flyingPacketTimes.pollFirst();
        }
    }

    public int flyingPacketsInWindow() {
        return flyingPacketTimes.size();
    }

    public void registerClick(long now) {
        clickTimes.addLast(now);
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000L) {
            clickTimes.pollFirst();
        }
    }

    /** Clicks in the last second == CPS. */
    public int cps() {
        return clickTimes.size();
    }

    public Deque<Long> getClickTimes() { return clickTimes; }

    public void registerTarget(int entityId, long now) {
        recentTargets.addLast(new long[]{entityId, now});
        while (recentTargets.size() > 16) recentTargets.pollFirst();
    }

    /** Distinct entity ids attacked within the last {@code windowMs}. */
    public int distinctTargetsWithin(long windowMs, long now) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (long[] t : recentTargets) {
            if (now - t[1] <= windowMs) ids.add(t[0]);
        }
        return ids.size();
    }

    public Deque<long[]> getRecentTargets() { return recentTargets; }
    public Deque<Float> getYawDeltaSamples() { return yawDeltaSamples; }
    public Deque<Float> getPitchDeltaSamples() { return pitchDeltaSamples; }

    private static void pushSample(Deque<Float> q, float v, int cap) {
        q.addLast(v);
        while (q.size() > cap) q.pollFirst();
    }

    private static float wrap(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    public UUID getUuid() { return uuid; }
    public Player getPlayer() { return player; }
    public ViolationTracker getViolations() { return violations; }
    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int v) { this.protocolVersion = v; }

    public float lastDeltaYaw() { return prevDeltaYaw; }

    public double horizontalSpeed() {
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
}
