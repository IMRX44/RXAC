package com.rxac.punish;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Centralizes alerts and the punishment ladder (alert → kick → ban). */
public final class PunishmentManager {

    private final RXAC plugin;

    public PunishmentManager(RXAC plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("general.prefix", "&8[&cRXAC&8] "));
    }

    /** Broadcast a violation to staff with the rxac.alerts permission. */
    public void alert(PlayerData data, Check check, double vl, String debug) {
        if (!plugin.getConfig().getBoolean("punishments.alert", true)) return;

        String msg = prefix() + ChatColor.GRAY + data.getPlayer().getName()
                + ChatColor.RED + " failed " + ChatColor.WHITE + check.getName()
                + ChatColor.GRAY + " (" + check.getCategory().name().toLowerCase(Locale.ROOT) + ")"
                + ChatColor.DARK_GRAY + " vl=" + String.format("%.1f", vl)
                + (debug == null || debug.isEmpty() ? "" : ChatColor.DARK_GRAY + " [" + debug + "]");

        // Alerts must run on the main thread (they touch the player list/chat).
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("rxac.alerts")) p.sendMessage(msg);
            }
            plugin.getLogger().info("[ALERT] " + ChatColor.stripColor(msg));
        });
    }

    /** Execute the configured punishment for a check that exceeded its max VL. */
    public void punish(PlayerData data, Check check, double vl) {
        if (plugin.getConfig().getBoolean("general.alert-only", false)) {
            plugin.getLogger().info("[alert-only] would punish " + data.getPlayer().getName()
                    + " for " + check.getName() + " vl=" + vl);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = data.getPlayer();
            if (player == null || !player.isOnline()) return;

            if (plugin.getConfig().getBoolean("punishments.ban.enabled", false)) {
                String cmd = plugin.getConfig().getString("punishments.ban.command", "")
                        .replace("%player%", player.getName())
                        .replace("%check%", check.getName())
                        .replace("%vl%", String.format("%.0f", vl));
                if (!cmd.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("punishments.kick.enabled", true)) {
                String kick = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("punishments.kick.message",
                                "&cRXAC | Suspicious activity detected."));
                player.kickPlayer(kick);
            }
        });
    }
}
