package com.example.worldgen.random;

/**
 * Port of {@code net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus} (26.3): the
 * xoroshiro128++ generator (rotations 17, 49, shift 21, rotation 28). An all-zero state is
 * replaced by {@code (GOLDEN_RATIO_64, SILVER_RATIO_64)} as in vanilla.
 */
public class Xoroshiro128PlusPlus {

    private long seedLo;
    private long seedHi;

    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
        this(seed.seedLo(), seed.seedHi());
    }

    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = RandomSupport.GOLDEN_RATIO_64;
            this.seedHi = RandomSupport.SILVER_RATIO_64;
        }
    }

    public long nextLong() {
        long lo = seedLo;
        long hi = seedHi;
        long result = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        seedLo = Long.rotateLeft(lo, 49) ^ hi ^ hi << 21;
        seedHi = Long.rotateLeft(hi, 28);
        return result;
    }
}
