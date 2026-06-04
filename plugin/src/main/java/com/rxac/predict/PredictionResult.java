package com.rxac.predict;

/** Output of {@link PredictionEngine#predict}: the legal motion envelope. */
public final class PredictionResult {

    public boolean onGround;          // server-side collision ground
    public boolean insideSolid;       // body intersecting a solid block
    public double predictedVelY;      // expected vertical velocity (free-fall continuation)
    public double maxHorizontal;      // upper bound of legal horizontal speed this tick
    public boolean jumpTick;          // player just left the ground (jump allowed)

    @Override
    public String toString() {
        return String.format("ground=%b inside=%b predY=%.4f maxH=%.4f jump=%b",
                onGround, insideSolid, predictedVelY, maxHorizontal, jumpTick);
    }
}
