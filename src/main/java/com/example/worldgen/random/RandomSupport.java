package com.example.worldgen.random;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Port of {@code net.minecraft.world.level.levelgen.RandomSupport} (26.3): seed mixing for the
 * Xoroshiro generator. Constants read from the 26.3 bytecode:
 * {@code GOLDEN_RATIO_64 = -7046029254386353131L (0x9E3779B97F4A7C15)},
 * {@code SILVER_RATIO_64 = 7640891576956012809L (0x6A09E667F3BCC909)}, Stafford variant 13
 * multipliers {@code -4658895280553007687L} / {@code -7723592293110705685L} with shifts 30/27/31,
 * and the seed uniquifier start {@code 8682522807148012L} multiplied by {@code 1181783497276652981L}.
 */
public final class RandomSupport {

    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    private RandomSupport() {
    }

    /** Stafford's "Mix13" 64-bit finaliser. */
    public static long mixStafford13(long z) {
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }

    /** {@code lo = seed ^ SILVER_RATIO_64}, {@code hi = lo + GOLDEN_RATIO_64}, not yet mixed. */
    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        return new Seed128bit(lo, hi);
    }

    /** {@link #upgradeSeedTo128bitUnmixed} followed by {@link Seed128bit#mixed()}. */
    public static Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    /**
     * MD5 of the UTF-8 bytes of {@code name} (vanilla uses Guava {@code Hashing.md5().hashString});
     * bytes 0..7 big-endian form {@code seedLo}, bytes 8..15 big-endian form {@code seedHi}
     * (Guava {@code Longs.fromBytes}).
     */
    public static Seed128bit seedFromHashOf(String name) {
        byte[] hash = md5(name.getBytes(StandardCharsets.UTF_8));
        return new Seed128bit(bigEndianLong(hash, 0), bigEndianLong(hash, 8));
    }

    /** {@code SEED_UNIQUIFIER.updateAndGet(v -> v * 1181783497276652981L) ^ System.nanoTime()}. */
    public static long generateUniqueSeed() {
        return SEED_UNIQUIFIER.updateAndGet(v -> v * 1181783497276652981L) ^ System.nanoTime();
    }

    private static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is a mandatory JDK MessageDigest algorithm", e);
        }
    }

    private static long bigEndianLong(byte[] b, int off) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            v = v << 8 | (b[off + i] & 0xFFL);
        }
        return v;
    }

    /** {@code RandomSupport.Seed128bit}: the two 64-bit halves of a Xoroshiro state. */
    public record Seed128bit(long seedLo, long seedHi) {

        public Seed128bit xor(long lo, long hi) {
            return new Seed128bit(seedLo ^ lo, seedHi ^ hi);
        }

        public Seed128bit xor(Seed128bit other) {
            return xor(other.seedLo, other.seedHi);
        }

        public Seed128bit mixed() {
            return new Seed128bit(mixStafford13(seedLo), mixStafford13(seedHi));
        }
    }
}
