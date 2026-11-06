package com.ancientkira.mca.entity.ai;

import java.util.UUID;

/** Shared deterministic variety: each villager chooses a new dance phrase about every eight seconds. */
public final class DanceMotion {
    private DanceMotion() {}
    public static int style(UUID id, long time) {
        long mixed = id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ (Math.floorDiv(time, 160) * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 33; mixed *= 0xff51afd7ed558ccdL; mixed ^= mixed >>> 33;
        return (int) (mixed & 3);
    }
    public static float phase(UUID id) { return (id.hashCode() & 1023) * .006135923F; }
}
