package com.example.worldgen.carver;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.Mth;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.carver.WorldCarver} (26.3, an interface; the carver records
 * hold their own configuration). Registered types ({@code WorldCarverTypes.bootstrap}): {@code cave} ->
 * {@link CaveWorldCarver}, {@code canyon} -> {@link CanyonWorldCarver}. {@link #carveEllipsoid} and
 * {@link #canReach} are transcribed from the bytecode, comparison directions included.
 */
public interface WorldCarver {

    /** {@code WorldCarver$CarveSkipChecker}: {@code shouldSkip(relX, relY, relZ, y)}. */
    @FunctionalInterface
    interface CarveSkipChecker {
        boolean shouldSkip(double relX, double relY, double relZ, int y);
    }

    /** Carves into {@code output} for the target chunk {@code chunkPos}, from the start chunk {@code sourcePos}. */
    boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos, ChunkPos sourcePos, CarverOutput output);

    /** {@code random.nextFloat() <= probability} for both registered types. */
    boolean isStartChunk(RandomSource random);

    /** {@code getRange()}: 4 (chunks). */
    default int getRange() {
        return 4;
    }

    /**
     * {@code WorldCarver.carveEllipsoid}: skips ellipsoids whose centre is more than {@code 16 + 2 * hRadius} from the
     * chunk middle on x or z; carves local columns whose normalised xz distance is {@code < 1}, y from
     * {@code min(floor(y + vRadius) + 1, output.maxY())} down to (exclusive) {@code max(floor(y - vRadius) - 1, output.minY())}.
     */
    static void carveEllipsoid(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius,
                               double verticalRadius, CarverOutput output, CarveSkipChecker skipChecker) {
        double midX = chunkPos.getMiddleBlockX();
        double midZ = chunkPos.getMiddleBlockZ();
        double maxDistance = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - midX) > maxDistance || Math.abs(z - midZ) > maxDistance) {
            return;
        }
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        int minX = Math.max(Mth.floor(x - horizontalRadius) - minBlockX - 1, 0);
        int maxX = Math.min(Mth.floor(x + horizontalRadius) - minBlockX, 15);
        int minY = Math.max(Mth.floor(y - verticalRadius) - 1, output.minY());
        int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, output.maxY());
        int minZ = Math.max(Mth.floor(z - horizontalRadius) - minBlockZ - 1, 0);
        int maxZ = Math.min(Mth.floor(z + horizontalRadius) - minBlockZ, 15);
        for (int localX = minX; localX <= maxX; localX++) {
            int blockX = chunkPos.getBlockX(localX);
            double relX = ((double) blockX + 0.5 - x) / horizontalRadius;
            for (int localZ = minZ; localZ <= maxZ; localZ++) {
                int blockZ = chunkPos.getBlockZ(localZ);
                double relZ = ((double) blockZ + 0.5 - z) / horizontalRadius;
                if (relX * relX + relZ * relZ >= 1.0) {
                    continue;
                }
                for (int blockY = maxY; blockY > minY; blockY--) {
                    double relY = ((double) blockY - 0.5 - y) / verticalRadius;
                    if (!skipChecker.shouldSkip(relX, relY, relZ, blockY)) {
                        output.carve(localX, blockY, localZ);
                    }
                }
            }
        }
    }

    /**
     * {@code WorldCarver.canReach}: {@code dx^2 + dz^2 - (maxStep - step)^2 <= (thickness + 2 + 16)^2} measured from
     * the chunk middle.
     */
    static boolean canReach(ChunkPos chunkPos, double x, double z, int currentStep, int totalSteps, float thickness) {
        double midX = chunkPos.getMiddleBlockX();
        double midZ = chunkPos.getMiddleBlockZ();
        double dx = x - midX;
        double dz = z - midZ;
        double remaining = (double) (totalSteps - currentStep);
        double reach = (double) (thickness + 2.0F + 16.0F);
        return dx * dx + dz * dz - remaining * remaining <= reach * reach;
    }
}
