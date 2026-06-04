package com.rxac.check;

import com.rxac.RXAC;
import com.rxac.check.combat.*;
import com.rxac.check.movement.*;
import com.rxac.check.player.*;
import com.rxac.player.PlayerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the check instances and dispatches gameplay events to them.
 * Bypass permission and global toggles are enforced here so individual checks
 * stay simple.
 */
public final class CheckManager {

    private final RXAC plugin;
    private final List<Check> checks = new ArrayList<>();

    public CheckManager(RXAC plugin) {
        this.plugin = plugin;
        register();
    }

    private void register() {
        // Movement
        checks.add(new SpeedCheck(plugin));
        checks.add(new FlyCheck(plugin));
        checks.add(new NoFallCheck(plugin));
        checks.add(new MotionCheck(plugin));
        checks.add(new TimerCheck(plugin));
        checks.add(new PhaseCheck(plugin));
        checks.add(new JesusCheck(plugin));
        checks.add(new StepCheck(plugin));
        checks.add(new NoSlowCheck(plugin));
        checks.add(new PredictionCheck(plugin));
        // Combat
        checks.add(new ReachCheck(plugin));
        checks.add(new KillAuraCheck(plugin));
        checks.add(new AutoClickerCheck(plugin));
        checks.add(new AimCheck(plugin));
        checks.add(new HitBoxCheck(plugin));
        checks.add(new FastBowCheck(plugin));
        // Player / world interaction
        checks.add(new ScaffoldCheck(plugin));
        checks.add(new FastPlaceCheck(plugin));
        checks.add(new NukerCheck(plugin));
    }

    public void reloadAll() {
        checks.forEach(Check::reload);
    }

    private boolean active(PlayerData data) {
        if (!plugin.getConfig().getBoolean("general.enabled", true)) return false;
        return !data.getPlayer().hasPermission("rxac.bypass");
    }

    public void dispatchMovement(PlayerData data) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled() && c.getCategory() == CheckCategory.MOVEMENT) {
                safe(() -> c.onMovement(data), c);
            }
        }
    }

    public void dispatchAttack(PlayerData data, AttackContext ctx) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled() && c.getCategory() == CheckCategory.COMBAT) {
                safe(() -> c.onAttack(data, ctx), c);
            }
        }
    }

    public void dispatchSwing(PlayerData data) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled() && c.getCategory() == CheckCategory.COMBAT) {
                safe(() -> c.onSwing(data), c);
            }
        }
    }

    public void dispatchVelocity(PlayerData data) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled() && c.getCategory() == CheckCategory.MOVEMENT) {
                safe(() -> c.onVelocity(data), c);
            }
        }
    }

    public void dispatchBlockPlace(PlayerData data, BlockPlaceContext ctx) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled()) safe(() -> c.onBlockPlace(data, ctx), c);
        }
    }

    public void dispatchBowShoot(PlayerData data, float force, long drawMs) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled()) safe(() -> c.onBowShoot(data, force, drawMs), c);
        }
    }

    public void dispatchBlockBreak(PlayerData data, org.bukkit.block.Block block) {
        if (!active(data)) return;
        for (Check c : checks) {
            if (c.isEnabled()) safe(() -> c.onBlockBreak(data, block), c);
        }
    }

    /** A misbehaving check must never crash the netty thread. */
    private void safe(Runnable r, Check c) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().warning("Check " + c.getName() + " threw: " + t);
        }
    }

    public List<Check> getChecks() {
        return Collections.unmodifiableList(checks);
    }
}
