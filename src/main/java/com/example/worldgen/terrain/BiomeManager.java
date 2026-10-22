package com.example.worldgen.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Port of {@code net.minecraft.world.level.biome.BiomeManager} (26.3): the block-resolution biome lookup ("zoom")
 * over a quart-resolution {@link BiomeResolver}.
 * <ul>
 *     <li>{@link #obfuscateSeed}: {@code Hashing.sha256().hashLong(seed).asLong()}: Guava feeds the 8 bytes of the
 *     long little-endian into SHA-256 and returns the first 8 digest bytes read little-endian (verified against the
 *     real class: the oracle prints the obfuscated seed);</li>
 *     <li>{@link #getBiome}: the 8 corner cells around {@code ((x - 2) >> 2, (y - 2) >> 2, (z - 2) >> 2)} are
 *     scored by {@code getFiddledDistance} (LCG {@code LinearCongruentialGenerator.next(a, b) = a * (a *
 *     6364136223846793005 + 1442695040888963407) + b}, fiddle {@code (floorMod(r >> 24, 1024) / 1024.0 - 0.5) * 0.9});
 *     the first strictly smaller distance wins.</li>
 * </ul>
 */
public final class BiomeManager {

    private final BiomeResolver noiseBiomeSource;
    private final long biomeZoomSeed;

    public BiomeManager(BiomeResolver noiseBiomeSource, long biomeZoomSeed) {
        this.noiseBiomeSource = noiseBiomeSource;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public static long obfuscateSeed(long seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array());
            return ByteBuffer.wrap(digest, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public long biomeZoomSeed() {
        return biomeZoomSeed;
    }

    public BiomeManager withDifferentSource(BiomeResolver biomeSource) {
        return new BiomeManager(biomeSource, biomeZoomSeed);
    }

    public String getBiome(int x, int y, int z) {
        int absX = x - 2;
        int absY = y - 2;
        int absZ = z - 2;
        int parentX = absX >> 2;
        int parentY = absY >> 2;
        int parentZ = absZ >> 2;
        double fractX = (double) (absX & 3) / 4.0;
        double fractY = (double) (absY & 3) / 4.0;
        double fractZ = (double) (absZ & 3) / 4.0;
        int minI = 0;
        double minFiddledDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; ++i) {
            boolean xEven = (i & 4) == 0;
            boolean yEven = (i & 2) == 0;
            boolean zEven = (i & 1) == 0;
            int cornerX = xEven ? parentX : parentX + 1;
            int cornerY = yEven ? parentY : parentY + 1;
            int cornerZ = zEven ? parentZ : parentZ + 1;
            double distanceX = xEven ? fractX : fractX - 1.0;
            double distanceY = yEven ? fractY : fractY - 1.0;
            double distanceZ = zEven ? fractZ : fractZ - 1.0;
            double next = getFiddledDistance(biomeZoomSeed, cornerX, cornerY, cornerZ, distanceX, distanceY, distanceZ);
            if (minFiddledDistance > next) {
                minI = i;
                minFiddledDistance = next;
            }
        }
        int biomeX = (minI & 4) == 0 ? parentX : parentX + 1;
        int biomeY = (minI & 2) == 0 ? parentY : parentY + 1;
        int biomeZ = (minI & 1) == 0 ? parentZ : parentZ + 1;
        return noiseBiomeSource.getNoiseBiome(biomeX, biomeY, biomeZ);
    }

    /** {@code getNoiseBiomeAtPosition(BlockPos)}. */
    public String getNoiseBiomeAtPosition(int x, int y, int z) {
        return noiseBiomeSource.getNoiseBiome(x >> 2, y >> 2, z >> 2);
    }

    public String getNoiseBiomeAtQuart(int quartX, int quartY, int quartZ) {
        return noiseBiomeSource.getNoiseBiome(quartX, quartY, quartZ);
    }

    private static long lcgNext(long rval, long c) {
        rval *= rval * 6364136223846793005L + 1442695040888963407L;
        rval += c;
        return rval;
    }

    private static double getFiddledDistance(long seed, int xRandom, int yRandom, int zRandom, double distanceX,
                                             double distanceY, double distanceZ) {
        long rval = lcgNext(seed, xRandom);
        rval = lcgNext(rval, yRandom);
        rval = lcgNext(rval, zRandom);
        rval = lcgNext(rval, xRandom);
        rval = lcgNext(rval, yRandom);
        rval = lcgNext(rval, zRandom);
        double fiddleX = getFiddle(rval);
        rval = lcgNext(rval, seed);
        double fiddleY = getFiddle(rval);
        rval = lcgNext(rval, seed);
        double fiddleZ = getFiddle(rval);
        return square(distanceZ + fiddleZ) + square(distanceY + fiddleY) + square(distanceX + fiddleX);
    }

    private static double square(double v) {
        return v * v;
    }

    private static double getFiddle(long rval) {
        double uniform = (double) Math.floorMod(rval >> 24, 1024) / 1024.0;
        return (uniform - 0.5) * 0.9;
    }
}
