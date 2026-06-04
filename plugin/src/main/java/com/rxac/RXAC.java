package com.rxac;

import com.rxac.check.CheckManager;
import com.rxac.command.RXACCommand;
import com.rxac.listener.CombatListener;
import com.rxac.listener.ConnectionListener;
import com.rxac.ml.MLBridge;
import com.rxac.packet.PacketListener;
import com.rxac.player.PlayerDataManager;
import com.rxac.punish.PunishmentManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RXAC — full-stack, ML-assisted anti-cheat.
 *
 * <p>The plugin captures gameplay through ProtocolLib packets and Bukkit events,
 * feeds them into per-player {@link com.rxac.player.PlayerData}, runs deterministic
 * checks via the {@link CheckManager}, and streams high-confidence events to the
 * Python ML service through the {@link MLBridge} for secondary verification.</p>
 */
public final class RXAC extends JavaPlugin {

    private static RXAC instance;

    private PlayerDataManager playerDataManager;
    private CheckManager checkManager;
    private PunishmentManager punishmentManager;
    private MLBridge mlBridge;
    private PacketListener packetListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!getConfig().getBoolean("general.enabled", true)) {
            getLogger().warning("RXAC is disabled in config.yml — nothing will run.");
            return;
        }

        // Core services.
        this.mlBridge = new MLBridge(this);
        this.punishmentManager = new PunishmentManager(this);
        this.playerDataManager = new PlayerDataManager();
        this.checkManager = new CheckManager(this);

        // Wire up inputs.
        this.packetListener = new PacketListener(this);
        this.packetListener.register();

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        RXACCommand command = new RXACCommand(this);
        getCommand("rxac").setExecutor(command);
        getCommand("rxac").setTabCompleter(command);

        mlBridge.start();

        getLogger().info("RXAC enabled — " + checkManager.getChecks().size()
                + " checks active. alert-only=" + getConfig().getBoolean("general.alert-only"));
    }

    @Override
    public void onDisable() {
        if (packetListener != null) packetListener.unregister();
        if (mlBridge != null) mlBridge.shutdown();
        if (playerDataManager != null) playerDataManager.clear();
        getLogger().info("RXAC disabled.");
    }

    public static RXAC get() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public MLBridge getMlBridge() {
        return mlBridge;
    }
}
