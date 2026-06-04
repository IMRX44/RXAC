package com.rxac.check;

import org.bukkit.block.Block;

/** Snapshot of a block placement, passed to building checks (e.g. Scaffold). */
public final class BlockPlaceContext {

    private final Block placed;
    private final Block against;
    private final long time;

    public BlockPlaceContext(Block placed, Block against, long time) {
        this.placed = placed;
        this.against = against;
        this.time = time;
    }

    public Block getPlaced() { return placed; }
    public Block getAgainst() { return against; }
    public long getTime() { return time; }
}
