package com.example.worldgen.feature.types;

import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.FeaturePlacer;
import com.example.worldgen.feature.PlacedFeature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.provider.WeightedList;
import com.example.worldgen.random.RandomSource;

import java.util.List;

/**
 * Ports of the 26.3 composition features (each {@code place} transcribed from its class's bytecode). A nested
 * {@code PlacedFeature.place(level, generator, random, pos)} is {@code new FeaturePlacer(level, generator).place(this,
 * random, pos)} (no biome check).
 */
public final class CompositeFeatures {

    private CompositeFeatures() {
    }

    /** {@code WeightedPlacedFeature(feature, chance)}. */
    public record WeightedPlacedFeature(PlacedFeature feature, float chance) {
    }

    /**
     * {@code RandomSelectorFeature(features, defaultFeature)}: for each entry in order, {@code random.nextFloat() < chance}
     * places that entry and returns its result; none: the default feature's result.
     */
    public record RandomSelector(List<WeightedPlacedFeature> features, PlacedFeature defaultFeature) implements Feature {
        @Override
        public String type() {
            return "minecraft:random_selector";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            for (WeightedPlacedFeature f : features) {
                if (random.nextFloat() < f.chance()) return f.feature().place(level, generator, random, origin);
            }
            return defaultFeature.place(level, generator, random, origin);
        }
    }

    /** {@code SimpleRandomSelectorFeature(features)}: {@code features.get(random.nextInt(features.size()))} is placed. */
    public record SimpleRandomSelector(List<PlacedFeature> features) implements Feature {
        @Override
        public String type() {
            return "minecraft:simple_random_selector";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            int index = random.nextInt(features.size());
            return features.get(index).place(level, generator, random, origin);
        }
    }

    /**
     * {@code WeightedRandomSelectorFeature(features)}: {@code features.getRandom(random)} (empty for a zero total weight,
     * else one {@code nextInt(totalWeight)}) mapped to its placement result, else false.
     */
    public record WeightedRandomSelector(WeightedList<PlacedFeature> features) implements Feature {
        @Override
        public String type() {
            return "minecraft:weighted_random_selector";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            if (features.isEmpty()) return false;
            return features.getRandomOrThrow(random).place(level, generator, random, origin);
        }
    }

    /** {@code RandomBooleanSelectorFeature(featureTrue, featureFalse)}: {@code random.nextBoolean()} picks one. */
    public record RandomBooleanSelector(PlacedFeature featureTrue, PlacedFeature featureFalse) implements Feature {
        @Override
        public String type() {
            return "minecraft:random_boolean_selector";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            boolean b = random.nextBoolean();
            return (b ? featureTrue : featureFalse).place(level, generator, random, origin);
        }
    }

    /**
     * {@code SequenceFeature(features)}: one {@code new FeaturePlacer(level, generator)}; each feature in order is placed
     * with {@code placer.place(feature, random, origin)}; the first false result returns false; all true: true.
     */
    public record Sequence(List<PlacedFeature> features) implements Feature {
        @Override
        public String type() {
            return "minecraft:sequence";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            FeaturePlacer placer = new FeaturePlacer(level, generator);
            for (PlacedFeature f : features) {
                if (!placer.place(f, random, origin)) return false;
            }
            return true;
        }
    }

    /**
     * {@code OverlayFeature(features)}: one {@code new FeaturePlacer(level, generator)}; every feature in order is placed
     * with {@code placer.place(feature, random, origin)}; the results are ORed.
     */
    public record Overlay(List<PlacedFeature> features) implements Feature {
        @Override
        public String type() {
            return "minecraft:overlay";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            FeaturePlacer placer = new FeaturePlacer(level, generator);
            boolean placed = false;
            for (PlacedFeature f : features) {
                placed |= placer.place(f, random, origin);
            }
            return placed;
        }
    }
}
