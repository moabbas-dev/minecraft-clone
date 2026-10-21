package com.example.worldgen.carver;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.RandomSupport;
import com.example.worldgen.random.WorldgenRandom;

import java.util.List;
import java.util.function.Function;

/**
 * The mask-building half of {@code NoiseBasedChunkGenerator.generateCarvers} (26.3, 448 bytes): for
 * {@code dx} in -8..8 (outer) and {@code dz} in -8..8 (inner), source chunk {@code (x + dx, z + dz)}, for each
 * carver {@code i} of the source chunk's biome carver list:
 * {@code random.setLargeFeatureSeed(seed + i, source.x, source.z)}; if {@code isStartChunk(random)} then
 * {@code carve(context, random, chunkPos, source, mask)}. The mask range is
 * {@code [minGenY + 1, minGenY + genDepth - 1 - (upgrading ? 0 : 7)]}.
 * <p>
 * The random is a {@link WorldgenRandom} over a {@link LegacyRandomSource} seeded with
 * {@link RandomSupport#generateUniqueSeed()}; that seed never reaches the output because every use is preceded by
 * {@code setLargeFeatureSeed}, which resets the LCG state. The tunnels' own randoms are
 * {@code RandomSource.createThreadLocalInstance(random.nextLong())} and therefore deterministic too.
 * <p>
 * The carver list per source chunk is vanilla's {@code BiomeGenerationSettings.getCarvers()} of the biome at quart
 * {@code (QuartPos.fromBlock(minBlockX), 0, QuartPos.fromBlock(minBlockZ))} from the uncached resolver; the caller
 * supplies it as {@code carversForSourceChunk}. All 56 overworld biomes of the 26.3 data carry
 * {@code [cave, cave_extra_underground, canyon]}. Applying the mask to blocks ({@code applyCarvingMask}) is a later
 * stage.
 */
public final class CarverStep {

    private CarverStep() {
    }

    /** Vanilla mask range for a chunk that is not being upgraded. */
    public static CarvingMask newMask(WorldGenerationContext context, boolean upgrading) {
        int topMargin = upgrading ? 0 : 7;
        int maxY = context.getMinGenY() + context.getGenDepth() - 1 - topMargin;
        return new CarvingMask(context.getMinGenY() + 1, maxY);
    }

    /** Builds the carving mask of {@code chunkPos} (non-upgrading chunk). */
    public static CarvingMask computeMask(long seed, ChunkPos chunkPos, WorldGenerationContext context,
                                          Function<ChunkPos, List<WorldCarver>> carversForSourceChunk) {
        CarvingMask mask = newMask(context, false);
        carve(seed, chunkPos, context, carversForSourceChunk, mask);
        return mask;
    }

    /** Same, with one carver list for every source chunk. */
    public static CarvingMask computeMask(long seed, ChunkPos chunkPos, WorldGenerationContext context,
                                          List<WorldCarver> carvers) {
        return computeMask(seed, chunkPos, context, pos -> carvers);
    }

    public static void carve(long seed, ChunkPos chunkPos, WorldGenerationContext context,
                             Function<ChunkPos, List<WorldCarver>> carversForSourceChunk, CarvingMask mask) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int range = 8;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkPos source = new ChunkPos(chunkPos.x() + dx, chunkPos.z() + dz);
                int index = 0;
                for (WorldCarver carver : carversForSourceChunk.apply(source)) {
                    random.setLargeFeatureSeed(seed + (long) index, source.x(), source.z());
                    if (carver.isStartChunk(random)) {
                        carver.carve(context, random, chunkPos, source, mask);
                    }
                    index++;
                }
            }
        }
    }
}
