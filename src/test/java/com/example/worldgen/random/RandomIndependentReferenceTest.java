package com.example.worldgen.random;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Independent references, besides the vanilla golden vectors: the legacy sources implement the
 * {@code java.util.Random} LCG, so their integer/long/float/double streams must equal the JDK's.
 */
class RandomIndependentReferenceTest {

    private static final long[] SEEDS = {0L, 1L, -1L, 42L, 0x5DEECE66DL, Long.MAX_VALUE, Long.MIN_VALUE};

    @Test
    void legacySourcesMatchJavaUtilRandom() {
        for (long seed : SEEDS) {
            RandomSource[] ports = {new LegacyRandomSource(seed), new SingleThreadedRandomSource(seed),
                    new ThreadSafeLegacyRandomSource(seed)};
            for (RandomSource port : ports) {
                Random jdk = new Random(seed);
                for (int i = 0; i < 200; i++) {
                    assertEquals(jdk.nextInt(), port.nextInt());
                    assertEquals(jdk.nextInt(1 + i * 7919), port.nextInt(1 + i * 7919));
                    assertEquals(jdk.nextInt(64), port.nextInt(64));
                    assertEquals(jdk.nextLong(), port.nextLong());
                    assertEquals(jdk.nextBoolean(), port.nextBoolean());
                    assertEquals(Float.floatToRawIntBits(jdk.nextFloat()), Float.floatToRawIntBits(port.nextFloat()));
                    assertEquals(Double.doubleToRawLongBits(jdk.nextDouble()), Double.doubleToRawLongBits(port.nextDouble()));
                }
            }
        }
    }

    @Test
    void setSeedRestartsTheSequence() {
        RandomSource a = new XoroshiroRandomSource(99L);
        long first = a.nextLong();
        a.nextGaussian();
        a.setSeed(99L);
        assertEquals(first, a.nextLong());
    }

    @Test
    void allZeroXoroshiroStateIsReplaced() {
        Xoroshiro128PlusPlus zero = new Xoroshiro128PlusPlus(0L, 0L);
        Xoroshiro128PlusPlus golden = new Xoroshiro128PlusPlus(RandomSupport.GOLDEN_RATIO_64, RandomSupport.SILVER_RATIO_64);
        for (int i = 0; i < 10; i++) {
            assertEquals(golden.nextLong(), zero.nextLong());
        }
    }

    @Test
    void invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new XoroshiroRandomSource(1L).nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> new LegacyRandomSource(1L).nextInt(-3));
        assertThrows(IllegalArgumentException.class, () -> new LegacyRandomSource(1L).nextInt(5, 5));
    }

    @Test
    void worldgenAlgorithmCreatesTheMatchingSource() {
        assertEquals(new LegacyRandomSource(7L).nextLong(), WorldgenRandom.Algorithm.LEGACY.newInstance(7L).nextLong());
        assertEquals(new XoroshiroRandomSource(7L).nextLong(), WorldgenRandom.Algorithm.XOROSHIRO.newInstance(7L).nextLong());
    }
}
