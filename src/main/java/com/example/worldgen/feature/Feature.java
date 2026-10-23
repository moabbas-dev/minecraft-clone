package com.example.worldgen.feature;

import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.Feature} (26.3): a decoded feature body (the flat
 * {@code worldgen/feature/*.json} object, there is no {@code ConfiguredFeature} wrapper in 26.3) that places itself at a
 * position. The {@code ChunkGenerator} parameter of vanilla's {@code place} is not used by any ported feature and is left out.
 */
public interface Feature {

    /** The {@code FeatureTypes} registry id ({@code minecraft:ore}, ...). */
    String type();

    boolean place(GenRegion level, RandomSource random, Pos origin);

    /** Whether the feature body is ported ({@link UnportedFeature} is not). */
    default boolean isPorted() {
        return true;
    }

    /** {@code Feature.markAboveForPostProcessing}: up to two non-air blocks above {@code pos} are marked, stopping at air. */
    static void markAboveForPostProcessing(GenRegion level, Pos pos) {
        Pos p = pos;
        for (int i = 0; i < 2; ++i) {
            p = p.above();
            StateRef state = level.getBlockState(p);
            if (com.example.worldgen.terrain.BlockProperties.isAir(state)) return;
            level.markPosForPostProcessing(p.x(), p.y(), p.z());
        }
    }
}
