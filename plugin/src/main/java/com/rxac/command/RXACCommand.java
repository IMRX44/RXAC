package com.rxac.command;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code /rxac <alerts|status|vl|reload> [player]}. */
public final class RXACCommand implements CommandExecutor, TabCompleter {

    private final RXAC plugin;

    public RXACCommand(RXAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rxac.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/rxac <alerts|status|vl|reload> [player]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getCheckManager().reloadAll();
                sender.sendMessage(ChatColor.GREEN + "RXAC config reloaded.");
            }
            case "status" -> {
                sender.sendMessage(ChatColor.GRAY + "RXAC active. Checks: "
                        + plugin.getCheckManager().getChecks().size()
                        + " | alert-only=" + plugin.getConfig().getBoolean("general.alert-only")
                        + " | ml=" + plugin.getConfig().getBoolean("ml.enabled"));
            }
            case "alerts" -> {
                if (sender instanceof Player p) {
                    sender.sendMessage(ChatColor.GRAY + "Toggle alerts via the rxac.alerts permission.");
                } else {
                    sender.sendMessage(ChatColor.GRAY + "Console always logs alerts.");
                }
            }
            case "vl" -> showVl(sender, args);
            default -> sender.sendMessage(ChatColor.GRAY + "/rxac <alerts|status|vl|reload> [player]");
        }
        return true;
    }

    private void showVl(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rxac vl <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        if (data == null) {
            sender.sendMessage(ChatColor.GRAY + "No data for that player yet.");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "VL for " + target.getName()
                + " (total " + String.format("%.1f", data.getViolations().totalVl()) + "):");
        for (Check c : plugin.getCheckManager().getChecks()) {
            double vl = data.getViolations().getVl(c);
            if (vl > 0.01) {
                sender.sendMessage(ChatColor.DARK_GRAY + " - " + c.getName() + ": "
                        + ChatColor.WHITE + String.format("%.1f", vl));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(Arrays.asList("alerts", "status", "vl", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vl")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return new ArrayList<>();
    }
}
