package com.rxac.check.player;

import com.rxac.RXAC;
import com.rxac.check.BlockPlaceContext;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * FastPlace: placing blocks faster than vanilla allows. The client is limited
 * to a few placements per second; sustained high place-rates indicate a macro
 * or scaffold/printer hack.
 */
public final class FastPlaceCheck extends Check {

    public FastPlaceCheck(RXAC plugin) {
        super(plugin, "FastPlace", CheckCategory.PLAYER);
    }

    @Override
    public void onBlockPlace(PlayerData data, BlockPlaceContext ctx) {
        int perSecond = data.registerAndCountPlaces(ctx.getTime());
        int max = cfgInt("max-per-second", 9);
        if (perSecond > max) {
            fail(data, Math.min(3.0, (perSecond - max) * 0.5 + 1), "places/s=" + perSecond);
        }
    }
}
