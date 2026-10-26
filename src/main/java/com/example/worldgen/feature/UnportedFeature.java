package com.example.worldgen.feature;

import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.random.RandomSource;

/**
 * The explicit marker for a feature whose type is registered in vanilla's {@code FeatureTypes} (26.3) but not ported yet.
 * It keeps the type id and the raw JSON body.
 *
 * <p>Top level: {@link BiomeDecoration} skips a top-level placed feature whose feature is unported, without running its
 * placement modifiers. Why skipping is safe for the other features' random streams: {@code ChunkGenerator.applyBiomeDecoration}
 * calls {@code random.setFeatureSeed(decorationSeed, index, step)} (= {@code setSeed(decorationSeed + index + 10000 * step)})
 * immediately before every {@code placeWithBiomeCheck} (bytecode), so no placed feature consumes randomness left over by
 * another one. What skipping does change is the world the later features see.
 *
 * <p>Nested: a ported composition feature (a selector, {@code sequence}, {@code overlay}) can reach an unported feature, and
 * a few ported paths need engine state that is not modelled ({@link Reached}). {@link #place} throws {@link Reached}; the
 * top-level {@link FeaturePlacer} catches it, writes nothing more for that top-level placed feature (its remaining placement
 * modifiers still run, so its random draws stay the same as an oracle that does the same) and the decoration continues with
 * the next feature. Every write made before the point is exactly vanilla's, since vanilla runs the same code up to there.
 * {@link FeatureLoader#PORTED_TYPES} lists the ported types; every other type of the registry decodes to this class.
 */
public record UnportedFeature(String type, Object json) implements Feature {

    /** Thrown where a ported feature reaches code or engine state that is not ported (the reason says what). */
    public static final class Reached extends RuntimeException {
        public Reached(String reason) {
            super(reason, null, false, false);
        }
    }

    @Override
    public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
        throw new Reached("feature type " + type + " is not ported");
    }

    @Override
    public boolean isPorted() {
        return false;
    }
}
