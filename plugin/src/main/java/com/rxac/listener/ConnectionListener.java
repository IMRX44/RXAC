package com.rxac.listener;

import com.rxac.RXAC;
import com.rxac.player.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Lifecycle: create/destroy PlayerData and resolve client protocol version. */
public final class ConnectionListener implements Listener {

    private final RXAC plugin;

    public ConnectionListener(RXAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(event.getPlayer());
        data.setProtocolVersion(ProtocolResolver.resolve(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer());
        if (data == null) return;
        // Suppress movement checks briefly after a teleport to avoid false flags.
        data.teleporting = true;
        data.lastTeleportMs = System.currentTimeMillis();
        data.hasPosition = false;
    }
}
