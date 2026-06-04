package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * Detects "Timer" hacks that speed up the client clock by sending more movement
 * packets per real second than vanilla's ~20. We count FLYING-family packets in
 * a sliding real-time window and compare to the expected rate.
 */
public final class TimerCheck extends Check {

    public TimerCheck(RXAC plugin) {
        super(plugin, "Timer", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        if (SpeedCheck.recentlyTeleported(data)) return;

        long window = (long) cfgDouble("sample-window-ms", 1000);
        double maxRatio = cfgDouble("max-ratio", 1.05);

        int packets = data.flyingPacketsInWindow();
        double expected = 20.0 * (window / 1000.0);   // vanilla packet rate
        double allowed = expected * maxRatio + 2;      // +2 absorbs burst jitter

        if (packets > allowed) {
            double ratio = packets / expected;
            fail(data, Math.min(2.0, (ratio - 1) * 10),
                    String.format("rate=%d/%dms ratio=%.2f", packets, window, ratio));
        }
    }
}
