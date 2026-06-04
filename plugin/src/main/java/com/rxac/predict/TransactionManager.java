package com.rxac.predict;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.rxac.RXAC;
import com.rxac.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transaction-based lag compensation.
 *
 * <p>Each tick we send every player a PING packet carrying a unique id and
 * record the send time in nanoseconds. The client answers with a matching PONG
 * the instant it receives it, before processing anything we send afterward.
 * When the PONG returns we know:</p>
 *
 * <ul>
 *   <li>the player's <b>precise</b> round-trip latency (independent of the
 *       coarse, averaged {@code Player#getPing()}); and</li>
 *   <li>that every packet we sent before the PING has now been acknowledged —
 *       the primitive every serious anti-cheat builds rewind/replay on.</li>
 * </ul>
 *
 * <p>This class delivers the latency half (used by reach rewind and prediction
 * tolerances). Full per-packet replay binding is layered on top of the same
 * id stream and can be extended without changing the wire protocol here.</p>
 */
public final class TransactionManager {

    private final RXAC plugin;
    private final ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
    // Start high so our ids are easy to recognize and don't clash with vanilla.
    private final AtomicInteger counter = new AtomicInteger(0x52414300);
    private BukkitTask task;

    public TransactionManager(RXAC plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("lag-compensation.enabled", true);
    }

    public void start() {
        if (!enabled()) return;
        long interval = Math.max(1, plugin.getConfig().getLong("lag-compensation.interval-ticks", 1));
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                PlayerData data = plugin.getPlayerDataManager().get(p);
                if (data != null) sendTransaction(p, data);
            }
        }, 20L, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    /** Send a fresh transaction (PING) to the player and record the send time. */
    public void sendTransaction(Player player, PlayerData data) {
        if (!enabled()) return;
        int id = counter.incrementAndGet();
        try {
            PacketContainer ping = protocol.createPacket(PacketType.Play.Server.PING);
            ping.getIntegers().write(0, id);
            data.addTransaction(id, System.nanoTime());
            protocol.sendServerPacket(player, ping);
        } catch (Throwable t) {
            // PING unsupported on this server build; latency falls back to getPing().
        }
    }

    /**
     * Handle a PONG from the client. Returns true if it matched one of our
     * transactions (and updated the measured latency).
     */
    public boolean onPong(PlayerData data, int id) {
        Long sent = data.confirmTransaction(id);
        if (sent == null) return false;     // not one of ours (e.g. vanilla)
        double ms = (System.nanoTime() - sent) / 1_000_000.0;
        // Light smoothing so a single spike doesn't whip the tolerance window.
        data.transactionPing = data.transactionPing < 0
                ? ms
                : data.transactionPing * 0.7 + ms * 0.3;
        return true;
    }
}
