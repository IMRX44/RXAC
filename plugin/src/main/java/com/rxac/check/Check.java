package com.rxac.check;

import com.rxac.RXAC;
import com.rxac.player.PlayerData;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Base class for every detection. A check is a stateless detector that reads
 * per-player state from {@link PlayerData} and reports {@link #fail}/{@link #reward}
 * into that player's violation tracker.
 *
 * <p>Subclasses override only the hooks they care about; the {@link CheckManager}
 * dispatches the relevant gameplay events to all checks.</p>
 */
public abstract class Check {

    protected final RXAC plugin;
    private final String name;
    private final CheckCategory category;

    private boolean enabled;
    private double maxVl;
    private double decayPerSecond;

    protected Check(RXAC plugin, String name, CheckCategory category) {
        this.plugin = plugin;
        this.name = name;
        this.category = category;
        reload();
    }

    /** Re-reads thresholds from config. Safe to call on /rxac reload. */
    public void reload() {
        ConfigurationSection s = config();
        this.enabled = s != null && s.getBoolean("enabled", true);
        this.maxVl = s == null ? 15 : s.getDouble("max-vl", 15);
        this.decayPerSecond = s == null ? 0.25 : s.getDouble("decay", 0.25);
    }

    /** The config section for this check, e.g. checks.movement.speed. */
    protected ConfigurationSection config() {
        String path = "checks." + category.name().toLowerCase() + "." + name.toLowerCase();
        return plugin.getConfig().getConfigurationSection(path);
    }

    protected double cfgDouble(String key, double def) {
        ConfigurationSection s = config();
        return s == null ? def : s.getDouble(key, def);
    }

    protected int cfgInt(String key, int def) {
        ConfigurationSection s = config();
        return s == null ? def : s.getInt(key, def);
    }

    // --- Detection hooks (override as needed) ---------------------------------

    /** Called on every movement (FLYING-family) packet after PlayerData is updated. */
    public void onMovement(PlayerData data) {}

    /** Called when the player attacks an entity (Bukkit damage event). */
    public void onAttack(PlayerData data, AttackContext ctx) {}

    /** Called on each arm-swing / click packet. */
    public void onSwing(PlayerData data) {}

    /** Called when the player should be taking knockback (PlayerVelocityEvent). */
    public void onVelocity(PlayerData data) {}

    /** Called when the player places a block (BlockPlaceEvent). */
    public void onBlockPlace(PlayerData data, BlockPlaceContext ctx) {}

    /** Called when the player releases a bow shot, with draw force and time. */
    public void onBowShoot(PlayerData data, float force, long drawMs) {}

    // --- Violation helpers ----------------------------------------------------

    /** Increase this check's violation level and emit an alert/punishment. */
    protected void fail(PlayerData data, double amount, String debug) {
        data.getViolations().fail(this, amount, debug);
    }

    protected void fail(PlayerData data, String debug) {
        fail(data, 1.0, debug);
    }

    /** Reward legitimate behavior by trimming a little VL (asymmetric to fail). */
    protected void reward(PlayerData data, double amount) {
        data.getViolations().reward(this, amount);
    }

    // --- Accessors ------------------------------------------------------------

    public String getName() { return name; }
    public CheckCategory getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public double getMaxVl() { return maxVl; }
    public double getDecayPerSecond() { return decayPerSecond; }
}
