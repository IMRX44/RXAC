package com.rxac.check.player;

import com.rxac.RXAC;
import com.rxac.check.BlockPlaceContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Scaffold detection. Legitimate bridging requires looking down at the block
 * face you place against. Scaffold hacks bridge while looking forward (or even
 * away) and place blocks faster than a human can. We flag placements made
 * below the player while the pitch is too shallow, especially in rapid bursts.
 */
public final class ScaffoldCheck extends Check {

    public ScaffoldCheck(RXAC plugin) {
        super(plugin, "Scaffold", CheckCategory.PLAYER);
    }

    @Override
    public void onBlockPlace(PlayerData data, BlockPlaceContext ctx) {
        Player p = data.getPlayer();
        Block placed = ctx.getPlaced();

        boolean below = placed.getY() < Math.floor(p.getLocation().getY());
        if (!below) { reward(data, 0.5); return; }

        // Bridging downward but the player is not looking down enough to see the
        // face they claim to place against.
        float pitch = p.getLocation().getPitch();
        double minPitch = cfgDouble("min-down-pitch", 30.0);

        long since = ctx.getTime() - data.lastBlockPlaceMs;
        data.lastBlockPlaceMs = ctx.getTime();
        boolean rapid = since < cfgInt("min-place-interval-ms", 110);

        if (pitch < minPitch) {
            double amount = rapid ? 2.0 : 1.0;
            fail(data, amount, String.format("pitch=%.1f%s", pitch, rapid ? " rapid" : ""));
        } else {
            reward(data, 0.3);
        }
    }
}
