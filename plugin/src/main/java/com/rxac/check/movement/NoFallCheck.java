package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * Flags clients that claim {@code onGround=true} while server-side collision
 * shows nothing beneath them — the classic NoFall trick used to negate fall
 * damage and mask flight.
 */
public final class NoFallCheck extends Check {

    public NoFallCheck(RXAC plugin) {
        super(plugin, "NoFall", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        if (!data.hasPosition) return;
        if (SpeedCheck.recentlyTeleported(data)) return;
        if (data.getPlayer().isFlying() || data.getPlayer().getAllowFlight()) return;
        if (data.inLiquid) return;

        // Client says grounded, but there is no block under it and it is moving
        // downward — physically impossible without a NoFall hack.
        if (data.clientOnGround && !data.nearGround && data.deltaY < -0.03) {
            fail(data, 1.5, String.format("fakeGround dY=%.3f", data.deltaY));
        } else {
            reward(data, 0.3);
        }
    }
}
