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
    public boolean serverGround;       // server-side collision ground (this tick)
    public boolean lastServerGround;   // server-side collision ground (previous tick)
    public int airTicks;
    public int groundTicks;
    public boolean inLiquid;
    public boolean nearGround;     // server-side bounding-box ground check

    // Last position considered physically valid, for setback / rewind.
    public org.bukkit.Location lastSafe;
    public long lastSetbackMs;

    // --- Timing ---------------------------------------------------------------
    public long lastMovementMs;
    private final Deque<Long> flyingPacketTimes = new ArrayDeque<>();  // for TimerCheck
    private final Deque<Long> clickTimes = new ArrayDeque<>();          // for CPS
    private final Deque<Long> placeTimes = new ArrayDeque<>();          // for FastPlace
    private final Deque<Long> breakTimes = new ArrayDeque<>();          // for Nuker/FastBreak

    // --- Combat ---------------------------------------------------------------
    public long lastAttackMs;
    public long lastSwingMs;
    public long lastBlockPlaceMs;
    public long bowDrawStartMs;
    public int lastTargetId = -1;

    // Recent feet positions {x, y, z, timeMs} for attacker-side reach rewind.
    private final Deque<double[]> recentPositions = new ArrayDeque<>();
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

    // Precise round-trip latency measured via PING/PONG transactions (ms); -1
    // until the first transaction confirms. Far more accurate than getPing().
    public volatile double transactionPing = -1;
    private final java.util.Map<Integer, Long> pendingTransactions =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void addTransaction(int id, long sendNano) {
        pendingTransactions.put(id, sendNano);
        // Guard against unbounded growth if a client never answers.
        if (pendingTransactions.size() > 200) pendingTransactions.clear();
    }

    /** Returns the send-time (nanos) for a confirmed transaction, or null. */
    public Long confirmTransaction(int id) {
        return pendingTransactions.remove(id);
    }

    /** Best available latency estimate (transaction ping, else client ping). */
    public int latencyMs() {
        if (transactionPing >= 0) return (int) Math.round(transactionPing);
        try {
            return Math.max(0, getPlayer().getPing());
        } catch (Throwable t) {
            return 0;
        }
    }

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
        lastSwingMs = now;
        clickTimes.addLast(now);
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000L) {
            clickTimes.pollFirst();
        }
    }

    /** Snapshot the current position for the reach-rewind window. */
    public void registerPositionSample(long now) {
        recentPositions.addLast(new double[]{x, y, z, now});
        while (!recentPositions.isEmpty() && now - recentPositions.peekFirst()[3] > 500L) {
            recentPositions.pollFirst();
        }
        while (recentPositions.size() > 25) recentPositions.pollFirst();
    }

    public Deque<double[]> getRecentPositions() { return recentPositions; }

    /** Clicks in the last second == CPS. */
    public int cps() {
        return clickTimes.size();
    }

    public Deque<Long> getClickTimes() { return clickTimes; }

    public int registerAndCountPlaces(long now) {
        return registerAndCount(placeTimes, now);
    }

    public int registerAndCountBreaks(long now) {
        return registerAndCount(breakTimes, now);
    }

    private static int registerAndCount(Deque<Long> q, long now) {
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        return q.size();
    }

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
