package com.rxac.listener;

import com.rxac.RXAC;
import com.rxac.check.BlockPlaceContext;
import com.rxac.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * World-interaction input: block placement (Scaffold) and bow draw/release
 * timing (FastBow).
 */
public final class InteractionListener implements Listener {

    private final RXAC plugin;

    public InteractionListener(RXAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer());
        if (data == null) return;
        plugin.getCheckManager().dispatchBlockPlace(data,
                new BlockPlaceContext(event.getBlock(), event.getBlockAgainst(),
                        System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer());
        if (data == null) return;
        plugin.getCheckManager().dispatchBlockBreak(data, event.getBlock());
    }

    /** Record when the player starts drawing a bow. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().startsWith("RIGHT_CLICK")) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType() != Material.BOW && item.getType() != Material.CROSSBOW) return;
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer());
        if (data == null) return;
        if (data.bowDrawStartMs == 0) data.bowDrawStartMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        long start = data.bowDrawStartMs;
        long drawMs = start == 0 ? 0 : System.currentTimeMillis() - start;
        data.bowDrawStartMs = 0;
        if (drawMs <= 0) return;     // unknown draw start; skip rather than false-flag

        plugin.getCheckManager().dispatchBowShoot(data, event.getForce(), drawMs);
    }
}
