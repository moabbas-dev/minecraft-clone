package com.example.worldgen.feature;

import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.random.RandomSource;

/**
 * The explicit marker for a feature whose type is registered in vanilla's {@code FeatureTypes} (26.3) but not ported yet.
 * It keeps the type id and the raw JSON body. {@link BiomeDecoration} skips a top-level placed feature whose feature is
 * unported, without running its placement modifiers; {@link #place} itself refuses to run.
 *
 * <p>Why skipping is safe for the other features' random streams: {@code ChunkGenerator.applyBiomeDecoration} calls
 * {@code random.setFeatureSeed(decorationSeed, index, step)} (= {@code setSeed(decorationSeed + index + 10000 * step)})
 * immediately before every {@code placeWithBiomeCheck} (bytecode), so no placed feature consumes randomness left over by
 * another one. What skipping does change is the world the later features see (the blocks the skipped feature would have
 * written). {@link FeatureLoader#PORTED_TYPES} lists the ported types; every other type of the registry decodes to this class.
 */
public record UnportedFeature(String type, Object json) implements Feature {

    @Override
    public boolean place(GenRegion level, RandomSource random, Pos origin) {
        throw new UnsupportedOperationException("feature type " + type + " is not ported");
    }

    @Override
    public boolean isPorted() {
        return false;
    }
}
