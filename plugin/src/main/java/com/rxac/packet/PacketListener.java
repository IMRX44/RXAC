package com.rxac.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.rxac.RXAC;
import com.rxac.player.PlayerData;
import com.rxac.predict.Collisions;
import com.rxac.util.MovementUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Captures movement and swing packets through ProtocolLib.
 *
 * <p>Packet fields and the precise arrival timestamp are read on the netty
 * thread (where TimerCheck needs them), but the {@link PlayerData} mutation and
 * check dispatch are bounced to the main thread so checks can safely read world
 * state (block-collision ground checks, potion effects, etc.).</p>
 */
public final class PacketListener {

    private final RXAC plugin;
    private PacketAdapter adapter;

    public PacketListener(RXAC plugin) {
        this.plugin = plugin;
    }

    public void register() {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        // Captured into a local because PacketAdapter has its own inherited
        // `plugin` field (typed Plugin) that would otherwise shadow ours.
        final RXAC rxac = this.plugin;
        this.adapter = new PacketAdapter(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.FLYING,
                PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Client.PONG) {

            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                PlayerData data = rxac.getPlayerDataManager().getOrCreate(player);

                PacketType type = event.getPacketType();
                long now = System.currentTimeMillis();

                if (type == PacketType.Play.Client.PONG) {
                    // Confirm the transaction immediately on the netty thread so
                    // the latency measurement isn't skewed by main-thread lag.
                    int id = event.getPacket().getIntegers().read(0);
                    rxac.getTransactionManager().onPong(data, id);
                    return;
                }

                if (type == PacketType.Play.Client.ARM_ANIMATION) {
                    data.registerClick(now);
                    Bukkit.getScheduler().runTask(rxac,
                            () -> rxac.getCheckManager().dispatchSwing(data));
                    return;
                }

                // Movement family. Record timing immediately for TimerCheck.
                long window = rxac.getConfig().getLong("checks.movement.timer.sample-window-ms", 1000);
                data.markFlyingPacket(now, window);

                PacketContainer packet = event.getPacket();
                final boolean hasPos = type == PacketType.Play.Client.POSITION
                        || type == PacketType.Play.Client.POSITION_LOOK;
                final boolean hasLook = type == PacketType.Play.Client.LOOK
                        || type == PacketType.Play.Client.POSITION_LOOK;

                final double px = hasPos ? packet.getDoubles().read(0) : Double.NaN;
                final double py = hasPos ? packet.getDoubles().read(1) : Double.NaN;
                final double pz = hasPos ? packet.getDoubles().read(2) : Double.NaN;
                final float yaw = hasLook ? packet.getFloat().read(0) : Float.NaN;
                final float pitch = hasLook ? packet.getFloat().read(1) : Float.NaN;
                final boolean onGround = readGround(packet);

                Bukkit.getScheduler().runTask(rxac, () -> {
                    if (!player.isOnline()) return;
                    data.lastClientOnGround = data.clientOnGround;
                    data.clientOnGround = onGround;

                    if (hasLook && !Float.isNaN(yaw)) data.updateRotation(yaw, pitch);
                    if (hasPos && !Double.isNaN(px)) data.updatePosition(px, py, pz);

                    // Server-authoritative environment context.
                    data.inLiquid = MovementUtil.inLiquid(player);
                    data.nearGround = MovementUtil.nearGround(player, 0.5);

                    // Collision-based ground state, the basis of prediction.
                    data.lastServerGround = data.serverGround;
                    BoundingBox box = Collisions.playerBox(data.x, data.y, data.z);
                    data.serverGround = Collisions.onGround(player.getWorld(), box);

                    if (data.clientOnGround) data.groundTicks++; else data.groundTicks = 0;
                    if (!data.clientOnGround && !data.nearGround) data.airTicks++; else data.airTicks = 0;
                    if (data.ticksSinceVelocity != Integer.MAX_VALUE) data.ticksSinceVelocity++;
                    data.lastMovementMs = now;

                    data.registerPositionSample(now);
                    rxac.getSetbackManager().markSafe(data);
                    rxac.getCheckManager().dispatchMovement(data);
                });
            }
        };
        pm.addPacketListener(adapter);
    }

    /** The on-ground boolean is the last field of every movement packet. */
    private boolean readGround(PacketContainer packet) {
        try {
            return packet.getBooleans().read(0);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void unregister() {
        if (adapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        }
    }
}
