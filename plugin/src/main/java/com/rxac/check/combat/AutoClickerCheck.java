package com.rxac.check.combat;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;
import com.rxac.util.MathUtil;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * AutoClicker / high-CPS detection on two axes:
 *  1) <b>CPS cap</b>: more clicks per second than is humanly sustainable.
 *  2) <b>Consistency</b>: the standard deviation of inter-click intervals is too
 *     low — humans jitter, macros tick like a metronome.
 */
public final class AutoClickerCheck extends Check {

    public AutoClickerCheck(RXAC plugin) {
        super(plugin, "AutoClicker", CheckCategory.COMBAT);
    }

    @Override
    public void onSwing(PlayerData data) {
        int cps = data.cps();
        int maxCps = cfgInt("max-cps", 22);
        if (cps > maxCps) {
            fail(data, Math.min(3.0, (cps - maxCps) * 0.5 + 1), "cps=" + cps);
            return;
        }

        // Consistency: need a decent sample of recent clicks.
        Deque<Long> times = data.getClickTimes();
        if (times.size() < 8) return;

        List<Long> intervals = new ArrayList<>();
        Long prev = null;
        for (Long t : times) {
            if (prev != null) intervals.add(t - prev);
            prev = t;
        }
        double stddev = MathUtil.stddev(intervals);
        double minStddev = cfgDouble("min-interval-stddev", 8.0);

        // Robotic regularity at non-trivial speed.
        if (stddev < minStddev && cps >= 8) {
            fail(data, 1.5, String.format("stddev=%.2f cps=%d", stddev, cps));
        } else {
            reward(data, 0.3);
        }
    }
}
