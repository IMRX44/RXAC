package com.rxac.check.player;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;

/**
 * The behavioral-AI verdict, fed by the ML service rather than by gameplay
 * hooks. When the anomaly model scores a player's recent behavior as highly
 * abnormal, {@link com.rxac.ml.MLBridge} raises this check's violation level —
 * so the ML layer participates in the same alert/punishment pipeline as the
 * deterministic checks, instead of merely logging.
 */
public final class AICheck extends Check {

    public AICheck(RXAC plugin) {
        super(plugin, "AI", CheckCategory.PLAYER);
    }

    // Detection is driven externally via the violation tracker; no hooks needed.
}
